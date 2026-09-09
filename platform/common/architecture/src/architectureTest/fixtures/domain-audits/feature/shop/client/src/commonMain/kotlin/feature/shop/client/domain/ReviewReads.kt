package feature.shop.client.domain

import kotlinx.coroutines.flow.Flow

data class Review(
    val id: String,
    val title: String,
)

data class Promotion(
    val code: String,
)

fun interface GetReviewTitle {
    suspend operator fun invoke(id: String): String?
}

fun interface GetReviewItems {
    suspend operator fun invoke(id: String): List<String>
}

fun interface GetReviewDecisions {
    suspend operator fun invoke(id: String): List<String>
}

fun interface FlowOfReviewRevision {
    operator fun invoke(id: String): Flow<Long>
}

fun interface GetReviewAuthor {
    suspend operator fun invoke(id: String): String
}

fun interface GetPromotions {
    suspend operator fun invoke(): List<Promotion>
}

fun interface GetOrphan {
    suspend operator fun invoke(): String
}
