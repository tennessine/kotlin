// !DIAGNOSTICS: -UNUSED_PARAMETER -UNUSED_VARIABLE
// FILE: api.kt

package api

@Experimental(ExperimentalLevel.WARNING, ExperimentalScope.BINARY)
@Target(AnnotationTarget.FILE)
annotation class ExperimentalAPI

// FILE: api2.kt

// Currently, meta-annotation on a file does _not_ make all declarations in that file experimental
@file:ExperimentalAPI
package api

fun notExperimental() {}

// FILE: usage-ok.kt

package ok

import api.*

fun use() {
    notExperimental()
}
