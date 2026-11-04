package org.koocheh.storage

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.GeneralSecurityException
import java.security.MessageDigest

// [UNVERIFIED] These tests use fakes for libsodium and the Android Keystore; the real
// SodiumPinKdf, KeystoreMasterKeyWrapper and SQLCipher were not run on a device.
// The ContextSecretSource contract (secret(context): 32 bytes) is exercised via secretForKey.
class KeyVaultTest {
    private class FakeKdf : PinKdf {
        override val saltBytes = 16
        override fun derive(pin: CharArray, salt: ByteArray): ByteArray =
            MessageDigest.getInstance("SHA-256").digest(String(pin).toByteArray() + salt)
    }

    private class FakeWrapper : MasterKeyWrapper {
        private val key = ByteArray(32) { it.toByte() }
        var destroyed = false
        override fun wrap(plain: ByteArray) = VaultCrypto.seal(key, plain)
        override fun unwrap(wrapped: ByteArray): ByteArray {
            if (destroyed) throw GeneralSecurityException("key destroyed")
            return VaultCrypto.open(key, wrapped) ?: throw GeneralSecurityException("bad")
        }
        override fun destroy() { destroyed = true }
    }

    private class MemStorage : VaultStorage {
        var blob: ByteArray? = null
        override fun read() = blob
        override fun write(bytes: ByteArray) { blob = bytes }
        override fun destroy() { blob = null }
    }

    private val wrapper = FakeWrapper()
    private val storage = MemStorage()
    private var dataDestroyed = 0
    private val vault = KeyVault(FakeKdf(), wrapper, storage) { dataDestroyed++ }

    private fun pin(s: String) = s.toCharArray()

    private fun setup() {
        vault.initialize(pin("1111"))
        assertEquals(UnlockResult.REAL, vault.unlock(pin("1111")))
        vault.enrollDecoyPin(pin("2222"))
        vault.enrollWipePin(pin("3333"))
        vault.lock()
    }

    @Test fun realPinUnlocksReal() {
        setup()
        assertEquals(UnlockResult.REAL, vault.unlock(pin("1111")))
        assertEquals("real", vault.namespace)
        assertEquals(32, vault.secretForKey("work").size)
    }

    @Test fun contextsShareNothing() {
        setup()
        vault.unlock(pin("1111"))
        assertFalse(vault.secretForKey("work").contentEquals(vault.secretForKey("social")))
        assertArrayEquals(vault.secretForKey("work"), vault.secretForKey("work"))
    }

    @Test fun decoyPinOpensSeparateNamespace() {
        setup()
        vault.unlock(pin("1111"))
        val realSecret = vault.secretForKey("work")
        vault.lock()
        assertEquals(UnlockResult.DECOY, vault.unlock(pin("2222")))
        assertEquals("decoy", vault.namespace)
        assertNotEquals(realSecret.toList(), vault.secretForKey("work").toList())
        assertEquals(0, dataDestroyed)
    }

    @Test fun wipePinDestroysEverything() {
        setup()
        assertEquals(UnlockResult.WIPED, vault.unlock(pin("3333")))
        assertTrue(wrapper.destroyed)
        assertNull(storage.blob)
        assertEquals(1, dataDestroyed)
        assertEquals(UnlockResult.WRONG, vault.unlock(pin("1111")))
        try { vault.secretForKey("work"); fail() } catch (e: IllegalStateException) { }
    }

    @Test fun wrongPinIsWrongAndDestroysNothing() {
        setup()
        assertEquals(UnlockResult.WRONG, vault.unlock(pin("9999")))
        assertFalse(wrapper.destroyed)
        assertEquals(0, dataDestroyed)
    }

    @Test fun lockZeroesKeysAndNotifies() {
        setup()
        var closed = 0
        vault.addLockListener { closed++ }
        vault.unlock(pin("1111"))
        vault.lock()
        assertTrue(closed >= 1)
        try { vault.secretForKey("work"); fail() } catch (e: IllegalStateException) { }
    }

    @Test fun duplicatePinCannotBeEnrolled() {
        setup()
        vault.unlock(pin("1111"))
        try { vault.enrollWipePin(pin("2222")); fail() } catch (e: IllegalArgumentException) { }
    }

    @Test fun enrollingNeedsRealUnlock() {
        setup()
        vault.unlock(pin("2222"))
        try { vault.enrollWipePin(pin("4444")); fail() } catch (e: IllegalStateException) { }
    }
}
