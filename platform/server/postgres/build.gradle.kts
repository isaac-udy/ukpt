plugins {
    id("ukpt.jvm-base")
    // Resolves version-free from the root buildscript classpath (see the root build file).
    alias(libs.plugins.udytilsPostgres)
}

kotlin {
    compilerOptions {
        // The generated Exposed tables type UUID id columns as kotlin.uuid.Uuid.
        freeCompilerArgs.add("-opt-in=kotlin.uuid.ExperimentalUuidApi")
    }
}

// The Flyway migrations in src/main/resources/db/migration are the schema's only source of truth;
// the plugin derives schema.sql and the generated Exposed tables from them.
configure<dev.isaacudy.udytils.postgres.gradle.PostgresCodegenExtension> {
    // Every feature's storage code imports its tables from this one shared package
    // (`ServerData.tableAccessOwnedByStorage` confines those imports to `server.data`).
    outputPackage.set("platform.server.postgres.tables")
    // Re-exported as `api` below, so the plugin needn't add it to `implementation` too.
    runtimeDependency.set(false)
}

dependencies {
    // Re-exported so a feature's `server.data` gets the column types, the migrator, the
    // notification bus and Exposed itself from this module (docs/serverdata.md).
    api(libs.udytils.postgres.core)
    api(libs.udytils.postgres.koin)

    // An embedded Postgres is the only place TransactionRunner's joining semantics are observable.
    testImplementation(libs.udytils.postgres.embedded)
    testImplementation(libs.kotlinx.coroutinesCore)
    testImplementation(libs.kotlin.testJunit)
    testRuntimeOnly(libs.logback)
}
