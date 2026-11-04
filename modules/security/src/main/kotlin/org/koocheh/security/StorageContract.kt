// Local copy of the C-006 contract (org.koocheh.storage). The cell says to code
// against local interfaces and test fakes; remove this file once the storage
// module is on the classpath, to avoid duplicate classes.
package org.koocheh.storage

enum class UnlockResult { REAL, DECOY, WIPED, WRONG }

interface KeyVault {
    fun unlock(pin: CharArray): UnlockResult
    fun lock()
    fun destroyAll()
}
