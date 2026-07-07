package architecture.utils

/**
 * Reactive stream wrappers a domain interface may return — a `FlowOf…` interface's primary
 * function returns a `Flow<T>` (or `StateFlow`/`SharedFlow`). These are allowed *base* types whose
 * type argument is still validated recursively, so `Flow<Session?>` passes because `Session?` is a
 * domain type, while `Flow<android.view.View>` still fails.
 *
 * Kept in its own set rather than folded into [collectionTypeNames]: a reactive stream is not a
 * collection, and other rules may reason about `collectionTypeNames`. Each entry is listed both by
 * its simple name and its fully-qualified name (mirroring `collectionTypeNames`) so it matches
 * whichever `guessFullyQualifiedName` resolves to.
 */
val reactiveWrapperTypeNames = setOf(
    "Flow", "kotlinx.coroutines.flow.Flow",
    "StateFlow", "kotlinx.coroutines.flow.StateFlow",
    "SharedFlow", "kotlinx.coroutines.flow.SharedFlow",
)
