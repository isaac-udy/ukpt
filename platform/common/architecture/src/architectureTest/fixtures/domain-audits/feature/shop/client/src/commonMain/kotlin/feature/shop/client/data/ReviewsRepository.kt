package feature.shop.client.data

import feature.shop.client.domain.FlowOfReviewRevision
import feature.shop.client.domain.GetOrphan
import feature.shop.client.domain.GetPromotions
import feature.shop.client.domain.GetReviewAuthor
import feature.shop.client.domain.GetReviewDecisions
import feature.shop.client.domain.GetReviewItems
import feature.shop.client.domain.GetReviewTitle
import feature.shop.client.domain.GetResolvedByInjectDelegate
import feature.shop.client.domain.GetResolvedFromKoin
import feature.shop.client.domain.GetResolvedInCompose
import kotlinx.coroutines.flow.flowOf

internal class ReviewsRepository {
    val getReviewTitle = GetReviewTitle { null }
    val getReviewItems = GetReviewItems { emptyList() }
    val getReviewDecisions = GetReviewDecisions { emptyList() }
    val flowOfReviewRevision = FlowOfReviewRevision { flowOf(0L) }
    val getReviewAuthor = GetReviewAuthor { "" }
    val getPromotions = GetPromotions { emptyList() }
    val getOrphan = GetOrphan { "" }
    val getResolvedFromKoin = GetResolvedFromKoin { "" }
    val getResolvedByInjectDelegate = GetResolvedByInjectDelegate { "" }
    val getResolvedInCompose = GetResolvedInCompose { "" }
}
