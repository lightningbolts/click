package compose.project.click.click.data.chat // pragma: allowlist secret

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import compose.project.click.click.data.storage.androidStorageContextOrThrow // pragma: allowlist secret
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

private const val DB_DIR = "local_store"
private const val DB_VERSION = 1

/** One SQLite file per user in `noBackupFilesDir` (never in cloud backups), FTS4 for search. */
private class UserStoreHelper(
    context: Context,
    file: File,
) : SQLiteOpenHelper(context, file.absolutePath, null, DB_VERSION) {
    var hasFts = false
        private set

    override fun onConfigure(db: SQLiteDatabase) {
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS messages (
                id TEXT PRIMARY KEY,
                chat_id TEXT NOT NULL,
                thread_key TEXT NOT NULL,
                sender_id TEXT NOT NULL,
                text TEXT NOT NULL,
                created_ms INTEGER NOT NULL,
                message_type TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS messages_by_chat_time ON messages (chat_id, created_ms)")
        runCatching { db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS messages_fts USING fts4(id, text)") }
    }

    override fun onOpen(db: SQLiteDatabase) {
        hasFts =
            db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='messages_fts'", null).use { it.moveToFirst() }
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int,
    ) = Unit
}

actual object LocalMessageStore {
    private val mutex = Mutex()
    private var openUser: String? = null
    private var helper: UserStoreHelper? = null

    private fun directory(context: Context): File = File(context.noBackupFilesDir, DB_DIR)

    private fun fileFor(
        context: Context,
        userId: String,
    ): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(userId.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(directory(context), "${digest.take(32)}.db")
    }

    /** Opens (or switches to) [userId]'s database; must hold [mutex]. */
    private fun dbFor(userId: String): Pair<SQLiteDatabase, Boolean>? {
        if (userId.isBlank()) return null
        val context = runCatching { androidStorageContextOrThrow() }.getOrNull() ?: return null
        if (openUser != userId || helper == null) {
            helper?.close()
            directory(context).mkdirs()
            helper = UserStoreHelper(context, fileFor(context, userId))
            openUser = userId
        }
        val h = helper ?: return null
        val db = runCatching { h.writableDatabase }.getOrNull() ?: return null
        return db to h.hasFts
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { mutex.withLock { block() } }

    actual suspend fun upsert(
        userId: String,
        messages: List<StoredChatMessage>,
    ) {
        if (messages.isEmpty()) return
        io {
            val (db, fts) = dbFor(userId) ?: return@io
            db.beginTransaction()
            try {
                messages.forEach { m ->
                    val values =
                        ContentValues().apply {
                            put("id", m.id)
                            put("chat_id", m.chatId)
                            put("thread_key", m.threadKey)
                            put("sender_id", m.senderId)
                            put("text", m.text)
                            put("created_ms", m.createdMs)
                            put("message_type", m.messageType)
                        }
                    db.insertWithOnConflict("messages", null, values, SQLiteDatabase.CONFLICT_REPLACE)
                    if (fts) {
                        db.delete("messages_fts", "id = ?", arrayOf(m.id))
                        db.insert(
                            "messages_fts",
                            null,
                            ContentValues().apply {
                                put("id", m.id)
                                put("text", m.text)
                            },
                        )
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    actual suspend fun remove(
        userId: String,
        messageIds: Collection<String>,
    ) {
        if (messageIds.isEmpty()) return
        io {
            val (db, fts) = dbFor(userId) ?: return@io
            messageIds.forEach { id ->
                db.delete("messages", "id = ?", arrayOf(id))
                if (fts) db.delete("messages_fts", "id = ?", arrayOf(id))
            }
        }
    }

    actual suspend fun search(
        userId: String,
        query: String,
        limit: Int,
    ): List<StoredChatMessage> =
        io {
            val (db, fts) = dbFor(userId) ?: return@io emptyList()
            val match = ftsQuery(query)
            val cols = "m.id, m.chat_id, m.thread_key, m.sender_id, m.text, m.created_ms, m.message_type"
            val cursor =
                if (fts && match != null) {
                    db.rawQuery(
                        "SELECT $cols FROM messages_fts f JOIN messages m ON m.id = f.id WHERE f.text MATCH ? " +
                            "ORDER BY m.created_ms DESC LIMIT ?",
                        arrayOf(match, limit.toString()),
                    )
                } else {
                    val like = "%" + query.trim().replace("%", "").replace("_", "") + "%"
                    db.rawQuery(
                        "SELECT $cols FROM messages m WHERE m.text LIKE ? ORDER BY m.created_ms DESC LIMIT ?",
                        arrayOf(like, limit.toString()),
                    )
                }
            cursor.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        add(
                            StoredChatMessage(
                                c.getString(0),
                                c.getString(1),
                                c.getString(2),
                                c.getString(3),
                                c.getString(4),
                                c.getLong(5),
                                c.getString(6),
                            ),
                        )
                    }
                }
            }
        }

    actual suspend fun latest(
        userId: String,
        chatId: String,
        limit: Int,
    ): List<StoredChatMessage> =
        io {
            val (db, _) = dbFor(userId) ?: return@io emptyList()
            db
                .rawQuery(
                    "SELECT id, chat_id, thread_key, sender_id, text, created_ms, message_type FROM messages " +
                        "WHERE chat_id = ? ORDER BY created_ms DESC LIMIT ?",
                    arrayOf(chatId, limit.toString()),
                ).use { c ->
                    buildList {
                        while (c.moveToNext()) {
                            add(
                                StoredChatMessage(
                                    c.getString(0),
                                    c.getString(1),
                                    c.getString(2),
                                    c.getString(3),
                                    c.getString(4),
                                    c.getLong(5),
                                    c.getString(6),
                                ),
                            )
                        }
                    }.reversed()
                }
        }

    actual suspend fun wipeAll() {
        io {
            helper?.close()
            helper = null
            openUser = null
            val context = runCatching { androidStorageContextOrThrow() }.getOrNull() ?: return@io
            directory(context).deleteRecursively()
        }
    }
}
