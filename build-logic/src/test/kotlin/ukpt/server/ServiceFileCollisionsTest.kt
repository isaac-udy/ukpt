package ukpt.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServiceFileCollisionsTest {

    private val flywayPlugin = "META-INF/services/org.flywaydb.core.extensibility.Plugin"
    private val exposedDialect = "META-INF/services/org.jetbrains.exposed.Dialect"

    @Test
    fun reportsNoCollisionWhenEveryServicePathHasOneDeclarer() {
        val verdict = ServiceFileCollisions.analyse(
            listOf(
                ServiceFileDeclaration("flyway-core-12.9.0.jar", setOf(flywayPlugin)),
                ServiceFileDeclaration("exposed-jdbc-1.0.0.jar", setOf(exposedDialect)),
            ),
            moduleResourcePaths = emptySet(),
        )

        assertEquals(emptyList(), verdict.collisions)
        assertEquals(emptyList(), verdict.unhandled)
    }

    @Test
    fun failsWhenTwoJarsDeclareTheSamePath() {
        val verdict = ServiceFileCollisions.analyse(
            listOf(
                ServiceFileDeclaration("flyway-database-postgresql-12.9.0.jar", setOf(flywayPlugin)),
                ServiceFileDeclaration("flyway-core-12.9.0.jar", setOf(flywayPlugin, exposedDialect)),
                ServiceFileDeclaration("postgresql-42.7.4.jar", setOf(exposedDialect)),
            ),
            moduleResourcePaths = emptySet(),
        )

        assertEquals(
            listOf(
                ServiceFileCollision(
                    servicePath = flywayPlugin,
                    origins = listOf("flyway-core-12.9.0.jar", "flyway-database-postgresql-12.9.0.jar"),
                    overriddenByModuleResource = false,
                ),
                ServiceFileCollision(
                    servicePath = exposedDialect,
                    origins = listOf("flyway-core-12.9.0.jar", "postgresql-42.7.4.jar"),
                    overriddenByModuleResource = false,
                ),
            ),
            verdict.collisions,
        )
        assertEquals(2, verdict.unhandled.size)
        assertEquals(emptyList(), verdict.handled)
    }

    @Test
    fun treatsAModuleResourceAtTheSamePathAsCoveringTheCollision() {
        val verdict = ServiceFileCollisions.analyse(
            listOf(
                ServiceFileDeclaration("flyway-core-12.9.0.jar", setOf(flywayPlugin)),
                ServiceFileDeclaration("flyway-database-postgresql-12.9.0.jar", setOf(flywayPlugin)),
            ),
            moduleResourcePaths = setOf(flywayPlugin),
        )

        assertEquals(1, verdict.collisions.size)
        assertTrue(verdict.collisions.single().overriddenByModuleResource)
        assertEquals(emptyList(), verdict.unhandled)
        assertEquals(1, verdict.handled.size)
    }

    @Test
    fun coversOnlyTheOverriddenPathWhenSeveralCollide() {
        val verdict = ServiceFileCollisions.analyse(
            listOf(
                ServiceFileDeclaration("flyway-core-12.9.0.jar", setOf(flywayPlugin, exposedDialect)),
                ServiceFileDeclaration("flyway-database-postgresql-12.9.0.jar", setOf(flywayPlugin)),
                ServiceFileDeclaration("postgresql-42.7.4.jar", setOf(exposedDialect)),
            ),
            moduleResourcePaths = setOf(flywayPlugin),
        )

        assertEquals(listOf(exposedDialect), verdict.unhandled.map(ServiceFileCollision::servicePath))
        assertEquals(listOf(flywayPlugin), verdict.handled.map(ServiceFileCollision::servicePath))
    }

    @Test
    fun namesTheCollidingPathAndItsDeclarersInTheFailureMessage() {
        val verdict = ServiceFileCollisions.analyse(
            listOf(
                ServiceFileDeclaration("flyway-core-12.9.0.jar", setOf(flywayPlugin)),
                ServiceFileDeclaration("flyway-database-postgresql-12.9.0.jar", setOf(flywayPlugin)),
            ),
            moduleResourcePaths = emptySet(),
        )

        val message = verdict.failureMessage("app/server/src/main/resources")

        assertTrue(flywayPlugin in message)
        assertTrue("flyway-core-12.9.0.jar" in message)
        assertTrue("flyway-database-postgresql-12.9.0.jar" in message)
        assertTrue("app/server/src/main/resources" in message)
    }

    @Test
    fun rendersBothHandledAndUnhandledCollisions() {
        val verdict = ServiceFileCollisions.analyse(
            listOf(
                ServiceFileDeclaration("flyway-core-12.9.0.jar", setOf(flywayPlugin, exposedDialect)),
                ServiceFileDeclaration("flyway-database-postgresql-12.9.0.jar", setOf(flywayPlugin)),
                ServiceFileDeclaration("postgresql-42.7.4.jar", setOf(exposedDialect)),
            ),
            moduleResourcePaths = setOf(flywayPlugin),
        )

        val report = verdict.render("app/server/src/main/resources")

        assertTrue("UNHANDLED $exposedDialect" in report)
        assertTrue("covered   $flywayPlugin" in report)
    }
}
