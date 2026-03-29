package architecture.definitions

import kotlin.reflect.KClass

val KClass<*>.packageName: String
    get() {
        return java.packageName
    }
