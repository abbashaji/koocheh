package org.koocheh.storage

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.koocheh.crypto.ContextSecretSource
import org.koocheh.model.ContextId
import java.io.File
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.concurrent.CopyOnWriteArrayList
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/*
 * No cloud backup. The app shell must ship backup rules that exclude everything, e.g.
 *
 *   res/xml/backup_rules.xml (android:fullBackupContent):
 *     <full-backup-content>
 *       <exclude domain="root" /> <exclude domain="file" /> <exclude domain="database" />
 *       <exclude domain="sharedpref" /> <exclude domain="external" />
 *     </full-backup-content>
 *   res/xml/data_extraction_rules.xml (Android 12+):
 *     <data-extraction-rules>
 *       <cloud-backup><exclude domain="root" /><exclude domain="file" /><exclude domain="database" />
 *         <exclude domain="sharedpref" /></cloud-backup>
 *       <device-transfer><exclude domain="root" /><exclude domain="file" /><exclude domain="database" />
 *         <exclude domain="sharedpref" /></device-transfer>
 *     </data-extraction-rules>
 *   and in the manifest: android:allowBackup="false".
 * All files here live in app-private storage (noBackupFilesDir and the database dir); nothing is
 * written to external storage.
 */

enum class UnlockResult { REAL, DECOY, WIPED, WRONG }

/** Wraps the master key with a hardware-backed (where available) key that cannot leave the device. */
interface MasterKeyWrapper {
    fun wrap(plain: ByteArray): ByteArray
    fun unwrap(wrapped: ByteArray): ByteArray
    fun destroy()
}

/** Persists the opaque vault blob. */
interface VaultStorage {
    fun read(): ByteArray?
    fun write(bytes: ByteArray)
    fun destroy()
}

/** [UNVERIFIED] Android Keystore behaviour (key generation, GCM IV handling, deleteEntry) was not run on a device. */
class KeystoreMasterKeyWrapper(private val alias: String = "koocheh.master.v1") : MasterKeyWrapper {
    private fun store(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun key(): SecretKey {
        (store().getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    override fun wrap(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        return c.iv + c.doFinal(plain)
    }

    override fun unwrap(wrapped: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, wrapped.copyOfRange(0, 12)))
        return c.doFinal(wrapped, 12, wrapped.size - 12)
    }

    override fun destroy() {
        val s = store()
        if (s.containsAlias(alias)) s.deleteEntry(alias)
    }
}

/** Vault blob in a file in app-private, backup-excluded storage (pass Context.noBackupFilesDir). */
class FileVaultStorage(private val file: File) : VaultStorage {
    override fun read(): ByteArray? = if (file.exists()) file.readBytes() else null

    override fun write(bytes: ByteArray) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(file)) {
            file.writeBytes(bytes)
            tmp.delete()
        }
    }

    override fun destroy() {
        if (file.exists()) {
            // Overwrite before delete. [UNVERIFIED] flash wear levelling may keep old blocks; the
            // wrapped keys are also useless once the Keystore entry is deleted.
            file.writeBytes(VaultCrypto.random(file.length().toInt().coerceAtLeast(1)))
            file.delete()
        }
        File(file.parentFile, file.name + ".tmp").delete()
    }
}

/**
 * Vault layout: three fixed slots (real, decoy, wipe); unused slots are random bytes, and all used
 * slots have the same length, so the blob does not reveal which PINs are enrolled. Each slot is
 * salt || AES-GCM(KDF(pin, salt), tag || keystoreWrapped(masterKey)). Unlock tries every slot.
 * The decoy slot holds a different master key, so the decoy namespace shares nothing with the real one.
 * Re-enrolling the decoy PIN gives a new decoy master; old decoy data becomes unreadable.
 * Never logs PINs, keys or tags.
 */
class KeyVault(
    private val kdf: PinKdf,
    private val wrapper: MasterKeyWrapper,
    private val storage: VaultStorage,
    private val dataDestroyer: () -> Unit = {},
) : ContextSecretSource {
    private val lock = Any()
    private var master: ByteArray? = null
    private var ns: String? = null
    private val lockListeners = CopyOnWriteArrayList<() -> Unit>()

    /** "real" or "decoy" while unlocked; throws while locked. */
    val namespace: String get() = synchronized(lock) { ns ?: error("vault locked") }

    fun isInitialized(): Boolean = storage.read() != null

    fun addLockListener(listener: () -> Unit) {
        lockListeners.add(listener)
    }

    /** First run: creates the master key and the real-PIN slot. */
    fun initialize(pin: CharArray) = synchronized(lock) {
        check(storage.read() == null) { "already initialized" }
        val m = VaultCrypto.random(32)
        val w = wrapper.wrap(m)
        VaultCrypto.wipe(m)
        val slotSize = kdf.saltBytes + 12 + 16 + 1 + w.size
        val slots = MutableList(SLOTS) { VaultCrypto.random(slotSize) }
        slots[REAL_AT] = makeSlot(pin, REAL, w)
        storage.write(serialize(slots))
    }

    fun unlock(pin: CharArray): UnlockResult = synchronized(lock) {
        val raw = storage.read() ?: return UnlockResult.WRONG
        val opened = tryOpen(pin, parse(raw)) ?: return UnlockResult.WRONG
        when (opened.tag) {
            REAL, DECOY -> try {
                lockInternal()
                master = wrapper.unwrap(opened.payload)
                ns = if (opened.tag == REAL) "real" else "decoy"
                if (opened.tag == REAL) UnlockResult.REAL else UnlockResult.DECOY
            } catch (e: GeneralSecurityException) {
                UnlockResult.WRONG
            }
            WIPE -> {
                destroyAll()
                UnlockResult.WIPED
            }
            else -> UnlockResult.WRONG
        }
    }

    /** Zeroes in-memory keys and tells listeners (open databases) to close. */
    fun lock() = synchronized(lock) { lockInternal() }

    fun enrollDecoyPin(pin: CharArray) = enroll(pin, DECOY_AT, DECOY) { wrapper.wrap(VaultCrypto.random(32)) }

    fun enrollWipePin(pin: CharArray) = enroll(pin, WIPE_AT, WIPE) { VaultCrypto.random(it) }

    /** Irrecoverable: overwrites the vault blob, deletes the Keystore entry and the database files. */
    fun destroyAll() = synchronized(lock) {
        lockInternal()
        runCatching { storage.destroy() }
        runCatching { wrapper.destroy() }
        runCatching { dataDestroyer() }
        Unit
    }

    override fun secret(context: ContextId): ByteArray = secretForKey(context.storageKey())

    internal fun secretForKey(key: String): ByteArray = synchronized(lock) {
        val m = master ?: error("vault locked")
        VaultCrypto.hmac(m, "koocheh/ctx/".toByteArray() + key.toByteArray())
    }

    private fun lockInternal() {
        master?.let { VaultCrypto.wipe(it) }
        master = null
        ns = null
        lockListeners.forEach { runCatching { it() } }
    }

    private fun enroll(pin: CharArray, index: Int, tag: Byte, payload: (wrappedSize: Int) -> ByteArray) =
        synchronized(lock) {
            check(ns == "real") { "enrolling needs the real PIN unlocked" }
            val slots = parse(storage.read() ?: error("not initialized"))
            require(tryOpen(pin, slots) == null) { "PIN already in use" }
            val wrappedSize = slots[REAL_AT].size - kdf.saltBytes - 12 - 16 - 1
            slots[index] = makeSlot(pin, tag, payload(wrappedSize))
            storage.write(serialize(slots))
        }

    private fun makeSlot(pin: CharArray, tag: Byte, payload: ByteArray): ByteArray {
        val salt = VaultCrypto.random(kdf.saltBytes)
        val k = kdf.derive(pin, salt)
        try {
            return salt + VaultCrypto.seal(k, byteArrayOf(tag) + payload)
        } finally {
            VaultCrypto.wipe(k)
        }
    }

    private class Opened(val tag: Byte, val payload: ByteArray)

    private fun tryOpen(pin: CharArray, slots: List<ByteArray>): Opened? {
        var found: Opened? = null
        for (s in slots) {
            if (s.size <= kdf.saltBytes) continue
            val k = kdf.derive(pin, s.copyOfRange(0, kdf.saltBytes))
            val plain = VaultCrypto.open(k, s.copyOfRange(kdf.saltBytes, s.size))
            VaultCrypto.wipe(k)
            if (plain != null && plain.isNotEmpty() && found == null) {
                found = Opened(plain[0], plain.copyOfRange(1, plain.size))
            }
        }
        return found
    }

    private fun parse(raw: ByteArray): MutableList<ByteArray> {
        val bb = ByteBuffer.wrap(raw)
        return MutableList(SLOTS) { ByteArray(bb.int).also { a -> bb.get(a) } }
    }

    private fun serialize(slots: List<ByteArray>): ByteArray {
        val bb = ByteBuffer.allocate(slots.sumOf { 4 + it.size })
        slots.forEach { bb.putInt(it.size).put(it) }
        return bb.array()
    }

    private companion object {
        const val SLOTS = 3
        const val REAL_AT = 0
        const val DECOY_AT = 1
        const val WIPE_AT = 2
        const val REAL: Byte = 1
        const val DECOY: Byte = 2
        const val WIPE: Byte = 3
    }
}

/** Assumes org.koocheh.model.ContextId has a stable toString (enum or value type). */
internal fun ContextId.storageKey(): String = toString()
