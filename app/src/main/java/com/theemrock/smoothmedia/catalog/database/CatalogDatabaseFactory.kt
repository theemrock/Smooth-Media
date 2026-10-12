package com.theemrock.smoothmedia.catalog.database

import android.content.Context
import androidx.room.Room

object CatalogDatabaseFactory {
    const val DATABASE_NAME = "smooth-media-catalog.db"

    fun create(context: Context): CatalogDatabase =
        Room.databaseBuilder(
            context.applicationContext,
            CatalogDatabase::class.java,
            DATABASE_NAME,
        )
            .addCallback(CatalogDatabaseCallback)
            .addMigrations(*CatalogMigrations.ALL)
            .build()
}
