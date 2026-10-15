package com.isaacudy.ukpt

import feature.ukpt.ukptClientDependencies
import org.koin.core.module.Module

/**
 * The Compose HTML client's dependency graph. It lists the same feature modules as
 * :app:client:common's `clientDependencies`, which this module can't depend on because it is
 * Compose UI.
 */
val clientDependencies: List<Module> = listOf(
    ukptClientDependencies,
)
