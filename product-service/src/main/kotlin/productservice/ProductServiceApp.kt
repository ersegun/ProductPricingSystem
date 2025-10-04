@file:JvmName("ProductServiceApp")
package productservice

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import shared.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

private val logger = LoggerFactory.getLogger("ProductService")

fun main() {
    logger.info("Starting Product Service on port 8080")
    embeddedServer(Netty, port = 8080) {
        module()
    }.start(wait = true)
}

fun Application.module() {
    install(CallLogging) {
        level = Level.INFO
        format { call ->
            val status = call.response.status()
            val method = call.request.httpMethod.value
            val path = call.request.path()
            val duration = call.processingTimeMillis()
            "$method $path - $status (${duration}ms)"
        }
    }

    install(ServerContentNegotiation) { json() }

    val TOKEN = System.getenv("API_TOKEN") ?: "secret-dev-token-please-change"
    logger.info("API authentication configured")

    install(Authentication) {
        bearer("auth") {
            authenticate { credential ->
                val isValid = credential.token == TOKEN
                if (!isValid) {
                    logger.warn("Authentication failed: invalid token")
                }
                if (isValid) UserIdPrincipal("client") else null
            }
        }
    }

    // In-memory product catalog
    val products = mutableMapOf<String, Product>()
    logger.info("In-memory product catalog initialized")

    // HTTP client for Discount Service
    val http = HttpClient(CIO) {
        install(ClientContentNegotiation) { json() }
        install(DefaultRequest) { header(HttpHeaders.Authorization, "Bearer $TOKEN") }
    }
    logger.info("HTTP client configured for Discount Service at http://localhost:8081")

    val discountClient = object : DiscountClient {
        override suspend fun applyDiscount(request: DiscountApplyRequest): DiscountApplyResponse {
            logger.debug("Calling Discount Service to apply discount: productId=${request.productId}, discountId=${request.discountId}")
            return try {
                val response = http.put("http://localhost:8081/discounts/apply") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }.body<DiscountApplyResponse>()
                logger.debug("Discount Service response: applied=${response.applied}, alreadyApplied=${response.alreadyApplied}")
                response
            } catch (e: Exception) {
                logger.error("Failed to apply discount via Discount Service: ${e.message}", e)
                throw e
            }
        }

        override suspend fun getDiscountsForProduct(productId: String): List<Discount> {
            logger.debug("Fetching discounts for productId=$productId from Discount Service")
            return try {
                val discounts = http.get("http://localhost:8081/discounts/$productId").body<List<Discount>>()
                logger.debug("Retrieved ${discounts.size} discount(s) for productId=$productId")
                discounts
            } catch (e: Exception) {
                logger.error("Failed to fetch discounts from Discount Service for productId=$productId: ${e.message}", e)
                throw e
            }
        }
    }

    routing {
        authenticate("auth") {

            // GET /products?country={country}
            get("/products") {
                val countryParam = call.request.queryParameters["country"]
                if (countryParam == null) {
                    logger.warn("GET /products called without country parameter")
                    return@get call.respond(HttpStatusCode.BadRequest, "country is required")
                }

                logger.info("Fetching products for country=$countryParam")

                val country = try {
                    Country.from(countryParam)
                } catch (e: Exception) {
                    logger.error("Invalid country code: $countryParam", e)
                    throw e
                }

                val filtered = products.values.filter { p: Product ->
                    p.country.equals(country.name, true)
                }
                logger.debug("Found ${filtered.size} product(s) for country=${country.name}")

                val result: List<ProductWithFinalPrice> = coroutineScope {
                    filtered.map { p ->
                        async {
                            val discounts: List<Discount> = discountClient.getDiscountsForProduct(p.id)
                            val taxed = p.basePrice * (1.0 + country.vatPercent())
                            val final = discounts.fold(taxed) { acc, d -> acc * (1 - d.percent / 100.0) }
                            ProductWithFinalPrice(
                                id = p.id,
                                name = p.name,
                                basePrice = p.basePrice,
                                country = p.country,
                                taxedPrice = "%.2f".format(final).toDouble()
                            )
                        }
                    }.awaitAll()
                }

                logger.info("Returning ${result.size} product(s) with final prices for country=${country.name}")
                call.respond<List<ProductWithFinalPrice>>(result)
            }

            // POST /products/{id}/discount
            post("/products/{id}/discount") {
                val id = call.parameters["id"]!!
                val req = call.receive<DiscountApplyRequest>()

                logger.info("Applying discount to product: productId=${req.productId}, discountId=${req.discountId}, percent=${req.percent}")

                if (id != req.productId) {
                    logger.warn("Path id ($id) does not match request productId (${req.productId})")
                    return@post call.respond(HttpStatusCode.BadRequest, "Path id must match productId")
                }

                val resp: DiscountApplyResponse = discountClient.applyDiscount(req)
                logger.info("Discount application result: applied=${resp.applied}, alreadyApplied=${resp.alreadyApplied}")
                call.respond(resp)
            }

            // POST /admin/ingest
            post("/admin/ingest") {
                val ingestReq = call.receive<IngestRequest>()
                val id = "ing-${System.currentTimeMillis()}"

                logger.info("Starting ingestion: id=$id, mode=${ingestReq.mode ?: "all"}, dryRun=${ingestReq.dryRun ?: false}, chunkSize=${ingestReq.chunkSize ?: 100}, retries=${ingestReq.retries ?: 2}, failFast=${ingestReq.failFast ?: false}")

                IngestManager.start(id, ingestReq, products, discountClient)
                call.respond(mapOf("ingestionId" to id, "status" to "started"))
            }

            // GET /admin/ingest/{id}/status
            get("/admin/ingest/{id}/status") {
                val id = call.parameters["id"]!!
                logger.debug("Checking ingestion status for id=$id")

                val status: IngestStatus? = IngestManager.status(id)
                if (status != null) {
                    logger.debug("Ingestion status: id=$id, status=${status.status}, filesProcessed=${status.filesProcessed}/${status.filesDiscovered}")
                    call.respond(status)
                } else {
                    logger.warn("Ingestion not found: id=$id")
                    call.respond(HttpStatusCode.NotFound)
                }
            }
        }
    }

    logger.info("Product Service routing configured and ready")
}