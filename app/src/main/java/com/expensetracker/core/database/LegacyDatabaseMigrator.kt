package com.expensetracker.core.database

import android.content.Context
import android.database.Cursor
import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.LinkedHashMap
import javax.inject.Inject
import javax.inject.Singleton
import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * One-time migration for existing dev/beta installs created before SQLCipher.
 *
 * A plaintext SQLite file starts with the 16-byte magic `SQLite format 3\0`,
 * while a SQLCipher-encrypted file does not. On startup this utility detects a
 * legacy plaintext `expense_tracker.db`, backs it up, reads every table into
 * memory, deletes the plaintext files, and re-imports the rows into the newly
 * encrypted database (triggered from Room's `onOpen` callback). Fresh installs
 * never touch this path.
 */
@Singleton
class LegacyDatabaseMigrator @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private var pending: LegacyData? = null
    private var importDone = false

    /** Detects and stages a legacy DB. Returns true when [importPending] has rows. */
    fun runIfNeeded(): Boolean {
        if (importDone) return false
        val dbFile = context.getDatabasePath(DB_NAME)
        if (!dbFile.exists() || !isPlaintextSqlite(dbFile)) return false

        return try {
            val data = readLegacy(dbFile)
            backUpLegacy(dbFile)
            // Remove plaintext db + journal/wal files so the encrypted DB is recreated.
            for (suffix in listOf("", "-wal", "-shm")) {
                File(dbFile.path + suffix).delete()
            }
            pending = data
            true
        } catch (e: Exception) {
            Log.e(TAG, "Legacy DB migration failed; encrypted DB will start empty", e)
            false
        }
    }

    /** Called from Room's `onOpen`; imports staged rows once, then clears state. */
    fun importPending(db: SupportSQLiteDatabase) {
        val data = pending ?: return
        pending = null
        importDone = true
        try {
            db.beginTransaction()
            try {
                for (table in TABLES) {
                    val rows = when (table) {
                        "categories" -> data.categories
                        "monitored_packages" -> data.monitoredPackages
                        "transactions" -> data.transactions
                        "clarification_history" -> data.clarificationHistory
                        "template_cache" -> data.templateCache
                        else -> throw IllegalStateException("Unexpected table $table")
                    }
                    insertRows(db, table, rows)
                }
                Log.i(TAG, "Legacy data re-imported into encrypted DB")
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Legacy re-import failed", e)
        }
    }

    private fun insertRows(db: SupportSQLiteDatabase, table: String, rows: List<Row>) {
        if (rows.isEmpty()) return
        // Rebuild column list from the first row so idempotent INSERT OR REPLACE
        // preserves original ids (Room's AUTOINCREMENT sequence re-derives from max id).
        val columns = rows.first().values.keys.joinToString(", ") { "`$it`" }
        val placeholders = rows.first().values.keys.joinToString(", ") { "?" }
        for (row in rows) {
            db.execSQL(
                "INSERT OR REPLACE INTO `$table` ($columns) VALUES ($placeholders)",
                row.values.values.toTypedArray(),
            )
        }
    }

    private fun readLegacy(dbFile: File): LegacyData {
        val coldb = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)
        return try {
            LegacyData(
                categories = queryAll(coldb, "categories"),
                monitoredPackages = queryAll(coldb, "monitored_packages"),
                transactions = queryAll(coldb, "transactions"),
                clarificationHistory = queryAll(coldb, "clarification_history"),
                templateCache = queryAll(coldb, "template_cache"),
            )
        } finally {
            coldb.close()
        }
    }

    private fun queryAll(db: SQLiteDatabase, table: String): List<Row> {
        val rows = mutableListOf<Row>()
        db.rawQuery("SELECT * FROM `$table`", null).use { cursor ->
            if (cursor.moveToFirst()) {
                do {
                    rows += cursorToRow(cursor)
                } while (cursor.moveToNext())
            }
        }
        return rows
    }

    private fun backUpLegacy(dbFile: File) {
        File(dbFile.path + LEGACY_BACKUP_SUFFIX).let { backup ->
            backup.delete()
            dbFile.copyTo(backup)
            Log.i(TAG, "Legacy plaintext DB backed up to ${backup.name}")
        }
    }

    private fun isPlaintextSqlite(file: File): Boolean {
        val firstBytes = ByteArray(SQLITE_MAGIC.size)
        file.inputStream().use { input ->
            val read = input.read(firstBytes)
            if (read < SQLITE_MAGIC.size) return false
        }
        return firstBytes.contentEquals(SQLITE_MAGIC)
    }

    private fun cursorToRow(cursor: Cursor): Row {
        return Row(LinkedHashMap<String, Any>().apply {
            for (i in 0 until cursor.columnCount) {
                val column = cursor.getColumnName(i)
                if (!cursor.isNull(i)) {
                    put(column, typedValue(cursor, i))
                }
            }
        })
    }

    private fun typedValue(cursor: Cursor, index: Int): Any {
        return when (cursor.getType(index)) {
            Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
            Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
            Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(index)
            else -> cursor.getString(index)
        }
    }

    private class LegacyData(
        val categories: List<Row>,
        val monitoredPackages: List<Row>,
        val transactions: List<Row>,
        val clarificationHistory: List<Row>,
        val templateCache: List<Row>,
    )

    private class Row(val values: LinkedHashMap<String, Any>)

    companion object {
        private const val TAG = "LegacyDatabaseMigrator"
        private const val DB_NAME = "expense_tracker.db"
        private const val LEGACY_BACKUP_SUFFIX = ".legacy-backup"
        private val SQLITE_MAGIC = "SQLite format 3\u0000".toByteArray(Charsets.ISO_8859_1)
        private val TABLES = listOf(
            "categories",
            "monitored_packages",
            "transactions",
            "clarification_history",
            "template_cache",
        )
    }
}