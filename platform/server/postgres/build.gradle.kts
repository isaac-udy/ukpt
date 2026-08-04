plugins {
    id("ukpt.jvm-base")
    // Registers `exportPostgresSchema` (the committed schema.sql snapshot) and
    // `generatePostgresTables` (the Exposed Table/Row sources), both hooked into compileKotlin.
    // Resolves version-free from the root buildscript classpath (see the root build file).
    alias(libs.plugins.udytilsPostgres)
}

kotlin {
    compilerOptions {
        // The generated Exposed tables type UUID id columns as kotlin.uuid.Uuid (Exposed 1.x's
        // native uuid() type), which is still experimental under this Kotlin toolchain.
        freeCompilerArgs.add("-opt-in=kotlin.uuid.ExperimentalUuidApi")
    }
}

// The Flyway migrations in src/main/resources/db/migration are the schema's only source of
// truth. The plugin applies them to a throwaway embedded Postgres, snapshots the result to
// schema.sql, and generates one Exposed Table/Row file per table under build/generated/.
configure<dev.isaacudy.udytils.postgres.gradle.PostgresCodegenExtension> {
    // Every feature's storage code imports its tables from this one shared package
    // (`ServerData.tableAccessOwnedByStorage` is what confines those imports to `server.data`).
    outputPackage.set("platform.server.postgres.tables")
    // The runtime artifact is re-exported as `api` below, so the plugin needn't also add it
    // to `implementation`.
    runtimeDependency.set(false)
}

dependencies {
    // Re-exported so a feature's `server.data` gets the column types, the migrator, the
    // notification bus and Exposed itself from this module (docs/serverdata.md).
    api(libs.udytils.postgres.core)
    // TransactionRunner is bound in `postgresPlatformDependencies`, beside the bindings
    // `postgresDependencies(config)` provides.
    api(libs.udytils.postgres.koin)

    // An embedded Postgres is the only place TransactionRunner's joining semantics are
    // observable; dev/test scope only, never on a production runtime classpath.
    testImplementation(libs.udytils.postgres.embedded)
    testImplementation(libs.kotlinx.coroutinesCore)
    testImplementation(libs.kotlin.testJunit)
    testRuntimeOnly(libs.logback)
}
