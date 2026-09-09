package feature.shop.client.domain

fun interface SubmitReview {
    suspend operator fun invoke(id: String)
}

internal class SubmitReviewImpl(
    private val getReviewTitle: GetReviewTitle,
    private val getReviewItems: GetReviewItems,
    private val getReviewDecisions: GetReviewDecisions,
    private val flowOfReviewRevision: FlowOfReviewRevision,
    private val getReviewAuthor: GetReviewAuthor,
    private val getPromotions: GetPromotions,
) : SubmitReview {
    override suspend fun invoke(id: String) = Unit
}

fun interface ShowPromotions {
    suspend operator fun invoke(): List<Promotion>
}

internal class ShowPromotionsImpl(
    private val getPromotions: GetPromotions,
    private val getReviewAuthor: GetReviewAuthor,
) : ShowPromotions {
    override suspend fun invoke(): List<Promotion> = getPromotions()
}

fun interface RequireReviewer {
    suspend operator fun invoke(id: String)
}

internal class RequireReviewerImpl(
    private val getReviewAuthor: GetReviewAuthor,
) : RequireReviewer {
    override suspend fun invoke(id: String) = Unit
}

fun interface AdministerTeams {
    suspend operator fun invoke()
}

internal class AdministerTeamsImpl(
    private val createTeam: CreateTeam,
    private val updateTeam: UpdateTeam,
    private val deleteTeam: DeleteTeam,
) : AdministerTeams {
    override suspend fun invoke() = Unit
}
