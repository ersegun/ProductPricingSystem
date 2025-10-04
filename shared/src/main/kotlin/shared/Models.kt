package shared

import kotlinx.serialization.Serializable

@Serializable
data class Product(
    val id: String,
    val name: String,
    val basePrice: Double,
    val country: String
)

@Serializable
data class Discount(
    val productId: String,
    val discountId: String,
    val percent: Double
)

enum class Country {
    Sweden, Germany, France;
    fun vatPercent(): Double = when (this) {
        Sweden -> 0.25; Germany -> 0.19; France -> 0.20
    }
    companion object {
        fun from(s: String) = entries.firstOrNull { it.name.equals(s, true) }
            ?: error("Unknown country: $s")
    }
}

@Serializable
data class DiscountApplyRequest(val productId: String, val discountId: String, val percent: Double)

@Serializable
data class DiscountApplyResponse(val applied: Boolean, val alreadyApplied: Boolean)

@Serializable
data class ProductWithFinalPrice(
    val id: String, val name: String, val basePrice: Double, val country: String, val taxedPrice: Double
)

@Serializable
data class IngestRequest(
    val workers: Int? = null,
    val chunkSize: Int? = null,
    val mode: String? = null,      // "products" | "discounts" | "all"
    val failFast: Boolean? = null,
    val retries: Int? = null,
    val dryRun: Boolean? = null
)

@Serializable
data class IngestStatus(
    val ingestionId: String,
    val status: String,  // running | completed | failed
    val filesDiscovered: Int,
    val filesProcessed: Int,
    val products: IngestCounts?,
    val discounts: IngestCounts?,
    val errorsSample: List<IngestError>,
    val startedAt: String,
    val updatedAt: String
)

@Serializable
data class IngestCounts(
    val parsed: Long,
    val ingested: Long,
    val failed: Long,
    val deduplicated: Long? = null
)

@Serializable
data class IngestError(val file: String, val line: Long, val reason: String)

interface DiscountClient {
    suspend fun applyDiscount(request: DiscountApplyRequest): DiscountApplyResponse
    suspend fun getDiscountsForProduct(productId: String): List<Discount>
}
