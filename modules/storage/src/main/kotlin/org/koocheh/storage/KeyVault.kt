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
