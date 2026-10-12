package com.theemrock.smoothmedia.catalog.database

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

object CatalogDatabaseCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        installInvariants(db)
    }

    override fun onOpen(db: SupportSQLiteDatabase) {
        installInvariants(db)
    }

    private fun installInvariants(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS track_locations_original_hash_immutable
            BEFORE UPDATE OF
                original_hash_algorithm_name,
                original_hash_algorithm_version,
                original_hash_digest
            ON track_locations
            WHEN OLD.original_hash_algorithm_name IS NOT NEW.original_hash_algorithm_name
              OR OLD.original_hash_algorithm_version IS NOT NEW.original_hash_algorithm_version
              OR OLD.original_hash_digest IS NOT NEW.original_hash_digest
            BEGIN
                SELECT RAISE(ABORT, 'original_file_hash is immutable');
            END
            """.trimIndent(),
        )
    }
}
