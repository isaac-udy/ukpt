package feature.shop.client.data

import feature.shop.client.domain.CreateTeam
import feature.shop.client.domain.DeleteTeam
import feature.shop.client.domain.UpdateTeam

internal class TeamsRepository {
    val createTeam = CreateTeam { }
    val updateTeam = UpdateTeam { _, _ -> }
    val deleteTeam = DeleteTeam { }
}
