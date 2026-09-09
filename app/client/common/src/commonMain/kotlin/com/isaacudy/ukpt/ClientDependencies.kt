package com.isaacudy.ukpt

import feature.ukpt.ukptClientDependencies
import org.koin.core.module.Module

/**
 * The client's whole dependency graph. Each feature's `[name]ClientDependencies` is added here,
 * so `ClientDependenciesTest` verifies the same list `App` installs.
 */
val clientDependencies: List<Module> = listOf(
    ukptClientDependencies,
)
