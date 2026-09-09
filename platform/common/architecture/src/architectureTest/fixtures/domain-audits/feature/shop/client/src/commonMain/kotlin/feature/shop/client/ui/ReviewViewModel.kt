package feature.shop.client.ui

import feature.shop.client.domain.AdministerTeams
import feature.shop.client.domain.RequireReviewer
import feature.shop.client.domain.ShowPromotions
import feature.shop.client.domain.SubmitReview

class ReviewViewModel(
    private val submitReview: SubmitReview,
    private val showPromotions: ShowPromotions,
    private val requireReviewer: RequireReviewer,
    private val administerTeams: AdministerTeams,
)
