package feature.shop.client.domain

fun interface CreateTeam {
    suspend operator fun invoke(name: String)
}

fun interface UpdateTeam {
    suspend operator fun invoke(id: String, name: String)
}

fun interface DeleteTeam {
    suspend operator fun invoke(id: String)
}
