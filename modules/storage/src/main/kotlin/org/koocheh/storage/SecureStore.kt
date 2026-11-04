package org.koocheh.storage

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import org.koocheh.model.ContextId
import org.koocheh.model.DisclosureTier
import java.security.MessageDigest

// [UNVERIFIED] Room + SQLCipher (SupportFactory, loadLibs, passphrase clearing) and Room kapt codegen
// were not run on a device or built here. Database files stay in app-private storage.
// Every table has a ctx column and every query filters by it; there is no cross-context query.

@Entity(tableName = "peer", primaryKeys = ["ctx", "peerKey"])
class PeerEntity(val ctx: String, val peerKey: String, val tier: String, val payload: ByteArray)

@Entity(tableName = "encounter", primaryKeys = ["ctx", "linkKey", "day"])
class EncounterEntity(val ctx: String, val linkKey: String, val day: Int, val count: Int)

@Entity(tableName = "message", indices = [Index("ctx", "peerKey")])
class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val ctx: String,
    val peerKey: String,
    val expiresAtMs: Long,
    val cipher: ByteArray,
)

@Dao
abstract class StoreDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertPeer(e: PeerEntity)

    @Query("SELECT * FROM peer WHERE ctx = :ctx AND peerKey = :peerKey")
    abstract suspend fun peer(ctx: String, peerKey: String): PeerEntity?

    @Query("DELETE FROM peer WHERE ctx = :ctx AND peerKey = :peerKey")
    abstract suspend fun deletePeerRow(ctx: String, peerKey: String)

    @Query("DELETE FROM message WHERE ctx = :ctx AND peerKey = :peerKey")
    abstract suspend fun deleteMessagesFor(ctx: String, peerKey: String)

    @Transaction
    open suspend fun deletePeerAll(ctx: String, peerKey: String) {
        deletePeerRow(ctx, peerKey)
        deleteMessagesFor(ctx, peerKey)
    }

    @Query("INSERT OR IGNORE INTO encounter (ctx, linkKey, day, count) VALUES (:ctx, :linkKey, :day, 0)")
    abstract suspend fun ensureEncounter(ctx: String, linkKey: String, day: Int)

    @Query("UPDATE encounter SET count = count + 1 WHERE ctx = :ctx AND linkKey = :linkKey AND day = :day")
    abstract suspend fun bumpEncounter(ctx: String, linkKey: String, day: Int)

    @Transaction
    open suspend fun increment(ctx: String, linkKey: String, day: Int) {
        ensureEncounter(ctx, linkKey, day)
        bumpEncounter(ctx, linkKey, day)
    }

    @Query("SELECT day FROM encounter WHERE ctx = :ctx AND linkKey = :linkKey AND day >= :sinceDay ORDER BY day")
    abstract suspend fun encounterDays(ctx: String, linkKey: String, sinceDay: Int): List<Int>

    @Insert
    abstract suspend fun insertMessage(e: MessageEntity)

    @Query("SELECT * FROM message WHERE ctx = :ctx AND peerKey = :peerKey ORDER BY id")
    abstract suspend fun messages(ctx: String, peerKey: String): List<MessageEntity>

    @Query("DELETE FROM encounter WHERE ctx = :ctx AND day < :cutoffDay")
    abstract suspend fun purgeEncounters(ctx: String, cutoffDay: Int)

    @Query("DELETE FROM message WHERE ctx = :ctx AND expiresAtMs <= :nowMs")
    abstract suspend fun purgeMessages(ctx: String, nowMs: Long)
}

@Database(
    entities = [PeerEntity::class, EncounterEntity::class, MessageEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class KoochehDb : RoomDatabase() {
    abstract fun dao(): StoreDao
}

internal class RoomContextStore(private val ctx: String, private val dao: StoreDao) : ContextStore {
    override suspend fun putPeerRecord(peerKey: String, tier: DisclosureTier, payload: ByteArray) =
        dao.upsertPeer(PeerEntity(ctx, peerKey, tier.name, payload))

    override suspend fun getPeerRecord(peerKey: String): PeerRecord? =
        dao.peer(ctx, peerKey)?.let { PeerRecord(it.peerKey, DisclosureTier.valueOf(it.tier), it.payload) }

    override suspend fun deletePeer(peerKey: String) = dao.deletePeerAll(ctx, peerKey)

    override suspend fun incrementEncounter(linkKey: ByteArray, day: Int) =
        dao.increment(ctx, hex(linkKey), day)

    override suspend fun encountersFor(linkKey: ByteArray, sinceDay: Int): List<Int> =
        dao.encounterDays(ctx, hex(linkKey), sinceDay)

    override suspend fun putMessage(peerKey: String, expiresAtMs: Long, cipher: ByteArray) =
        dao.insertMessage(MessageEntity(0, ctx, peerKey, expiresAtMs, cipher))

    override suspend fun messages(peerKey: String): List<StoredMessage> =
        dao.messages(ctx, peerKey).map { StoredMessage(it.peerKey, it.expiresAtMs, it.cipher) }

    override suspend fun purgeExpired(nowMs: Long) {
        dao.purgeEncounters(ctx, RetentionPolicy.encounterCutoffDay(nowMs))
        dao.purgeMessages(ctx, nowMs)
    }

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
}

/** One encrypted database per (namespace, context); the passphrase is that context's vault secret. */
class SecureStore(private val appContext: Context, private val vault: KeyVault) {
    private val open = HashMap<String, KoochehDb>()

    init {
        SQLiteDatabase.loadLibs(appContext)
        vault.addLockListener { closeAll() }
    }

    @Synchronized
    fun forContext(context: ContextId): ContextStore {
        val key = context.storageKey()
        val name = dbName(vault.namespace, key)
        val db = open.getOrPut(name) {
            Room.databaseBuilder(appContext, KoochehDb::class.java, name)
                .openHelperFactory(SupportFactory(vault.secret(context)))
                .build()
        }
        return RoomContextStore(key, db.dao())
    }

    @Synchronized
    fun closeAll() {
        open.values.forEach { runCatching { it.close() } }
        open.clear()
    }

    companion object {
        private const val PREFIX = "koocheh-"

        /** File names hide the namespace: a hash of namespace and context. */
        internal fun dbName(namespace: String, contextKey: String): String {
            val d = MessageDigest.getInstance("SHA-256").digest("$namespace/$contextKey".toByteArray())
            return PREFIX + d.take(8).joinToString("") { "%02x".format(it) } + ".db"
        }

        /** Pass as KeyVault's dataDestroyer. */
        fun deleteAllFiles(appContext: Context) {
            appContext.databaseList().filter { it.startsWith(PREFIX) }.forEach { appContext.deleteDatabase(it) }
        }
    }
}
