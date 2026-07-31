package feature.ukpt.client.data

/**
 * Canary for the architecture scope's test-source exclusion.
 *
 * This class deliberately violates the governed rules for its name and package — a public,
 * non-`internal` `[Name]Repository` in `client.data` that provides no domain interfaces — so it
 * only stays green while `projectScope` excludes test source sets. If that exclusion regresses
 * (it once narrowed to a hard-coded list that missed `commonTest`), `verifyArchitecture` fails on
 * this file immediately instead of silently scanning every fake and fixture in the project.
 */
class ScopeExclusionCanaryRepository
