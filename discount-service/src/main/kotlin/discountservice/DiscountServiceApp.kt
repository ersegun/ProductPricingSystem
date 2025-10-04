@file:JvmName("DiscountServiceApp")
package discountservice

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import shared.Discount
import shared.DiscountApplyRequest
import shared.DiscountApplyResponse
import java.util.concurrent.ConcurrentHashMap

private val logger = LoggerFactory.getLogger("DiscountService")

fun main() {
    logger.info("Starting Discount Service on port 8081")
    embeddedServer(Netty, port = 8081) {
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

    install(ContentNegotiation) { json() }

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

    // In-memory discounts
    val discounts = ConcurrentHashMap<String, MutableList<Discount>>() // key = productId
    logger.info("In-memory discount store initialized")

    routing {
        authenticate("auth") {

            // PUT /discounts/apply
            put("/discounts/apply") {
                val req = call.receive<DiscountApplyRequest>()
                logger.debug("Received apply discount request: productId=${req.productId}, discountId=${req.discountId}, percent=${req.percent}")

                val list = discounts.computeIfAbsent(req.productId) { mutableListOf<Discount>() }
                val already = list.any { it.discountId == req.discountId }

                val resp = if (already) {
                    logger.info("Discount already applied: productId=${req.productId}, discountId=${req.discountId}")
                    DiscountApplyResponse(applied = false, alreadyApplied = true)
                } else {
                    list.add(Discount(req.productId, req.discountId, req.percent))
                    logger.info("Discount applied successfully: productId=${req.productId}, discountId=${req.discountId}, percent=${req.percent}")
                    DiscountApplyResponse(applied = true, alreadyApplied = false)
                }

                call.respond(resp)
            }

            // GET /discounts/{productId}
            get("/discounts/{productId}") {
                val productId = call.parameters["productId"]!!
                logger.debug("Fetching discounts for productId=$productId")

                val result: List<Discount> = discounts[productId] ?: emptyList()

                logger.info("Retrieved ${result.size} discount(s) for productId=$productId")
                call.respond(result)
            }
        }
    }

    logger.info("Discount Service routing configured and ready")
}