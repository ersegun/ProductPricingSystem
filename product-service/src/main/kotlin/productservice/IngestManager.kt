package productservice

import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import shared.*
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

object IngestManager {
    private val statuses = mutableMapOf<String, Holder>()
    private val json = Json { ignoreUnknownKeys = true }

    fun start(id: String, req: IngestRequest, productStore: MutableMap<String, Product>, discountClient: DiscountClient) {
        val h = Holder(id)
        statuses[id] = h
        GlobalScope.launch(SupervisorJob() + Dispatchers.Default) {
            h.status = "running"; h.startedAt = now()
            try {
                val files = buildList {
                    when (req.mode ?: "all") {
                        "products" -> add("products.ndjson")
                        "discounts" -> add("discounts.ndjson")
                        else -> { add("products.ndjson"); add("discounts.ndjson") }
                    }
                }
                h.filesDiscovered = files.size
                files.forEach { file ->
                    h.filesProcessed++
                    if (file.startsWith("products")) ingestProducts(file, h, req, productStore)
                    else ingestDiscounts(file, h, req, discountClient)
                    if (req.failFast == true && h.errors.isNotEmpty()) return@launch
                }
                h.status = "completed"
            } catch (t: Throwable) {
                h.errors.add(IngestError("unknown", 0, t.message ?: "$t")); h.status = "failed"
            } finally { h.updatedAt = now() }
        }
    }

    fun status(id: String): IngestStatus? = statuses[id]?.toDto()

    private suspend fun ingestProducts(file: String, h: Holder, req: IngestRequest, productStore: MutableMap<String, Product>) {
        val chunk = req.chunkSize ?: 100
        val retries = req.retries ?: 2
        val path = Path.of("data", file)
        if (!Files.exists(path)) return
        val lines = Files.readAllLines(path)
        lines.chunked(chunk).forEach { block ->
            coroutineScope {
                block.mapIndexed { idx, line ->
                    async {
                        retry(retries) {
                            try {
                                val p = json.decodeFromString<Product>(line)
                                Country.from(p.country) // validate
                                if (req.dryRun != true) { productStore[p.id] = p; h.prod.ingested++ }
                            } catch (e: Exception) {
                                h.errors.add(IngestError(file, h.prod.parsed + idx + 1L, e.message ?: "parse error"))
                                h.prod.failed++; throw e
                            } finally { h.prod.parsed++ }
                        }
                    }
                }.awaitAll()
            }
        }
    }

    private suspend fun ingestDiscounts(file: String, h: Holder, req: IngestRequest, discountClient: DiscountClient) {
        val chunk = req.chunkSize ?: 100
        val retries = req.retries ?: 2
        val path = Path.of("data", file)
        if (!Files.exists(path)) return
        val lines = Files.readAllLines(path)
        lines.chunked(chunk).forEach { block ->
            coroutineScope {
                block.mapIndexed { idx, line ->
                    async {
                        retry(retries) {
                            try {
                                val d = json.decodeFromString<Discount>(line)
                                require(d.percent > 0.0 && d.percent < 100.0)
                                if (req.dryRun != true) {
                                    val resp = discountClient.applyDiscount(
                                        DiscountApplyRequest(d.productId, d.discountId, d.percent)
                                    )
                                    if (!resp.applied && resp.alreadyApplied) h.disc.deduplicated++
                                    else h.disc.ingested++
                                }
                            } catch (e: Exception) {
                                h.errors.add(IngestError(file, h.disc.parsed + idx + 1L, e.message ?: "parse error"))
                                h.disc.failed++; throw e
                            } finally { h.disc.parsed++ }
                        }
                    }
                }.awaitAll()
            }
        }
    }

    private suspend fun retry(times: Int, block: suspend () -> Unit) {
        var attempt = 0
        while (true) {
            try { block(); return } catch (e: Exception) {
                attempt++
                if (attempt > times) throw e
                delay(100L * attempt)
            }
        }
    }

    private fun now() = Instant.now().toString()

    private class Holder(val id: String) {
        var status = "pending"
        var filesDiscovered = 0
        var filesProcessed = 0
        var startedAt = now()
        var updatedAt = now()

        val prod = Counts()
        val disc = CountsWithDedup()
        val errors = mutableListOf<IngestError>()

        fun toDto() = IngestStatus(
            ingestionId = id,
            status = status,
            filesDiscovered = filesDiscovered,
            filesProcessed = filesProcessed,
            products = IngestCounts(prod.parsed, prod.ingested, prod.failed),
            discounts = IngestCounts(disc.parsed, disc.ingested, disc.failed, disc.deduplicated),
            errorsSample = errors.toList(),
            startedAt = startedAt,
            updatedAt = updatedAt
        )

        open class Counts {
            var parsed = 0L
            var ingested = 0L
            var failed = 0L
        }
        class CountsWithDedup : Counts() {
            var deduplicated = 0L
        }
    }
}
