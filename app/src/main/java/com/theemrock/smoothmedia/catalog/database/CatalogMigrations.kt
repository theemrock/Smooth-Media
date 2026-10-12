package com.theemrock.smoothmedia.catalog.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object CatalogMigrations {
    val FROM_1_TO_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE library_sources " +
                    "ADD COLUMN selection_state TEXT NOT NULL DEFAULT 'ACTIVE'",
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(FROM_1_TO_2)
}
