package com.theemrock.smoothmedia.source

import android.content.Context
import com.theemrock.smoothmedia.catalog.database.CatalogDatabase
import com.theemrock.smoothmedia.catalog.database.CatalogDatabaseFactory
import com.theemrock.smoothmedia.catalog.repository.RoomLibrarySourceRepository
import java.io.Closeable

class SourceRegistryRuntime private constructor(
    val database: CatalogDatabase,
    val repository: RoomLibrarySourceRepository,
    val registry: SourceRegistry,
) : Closeable {
    override fun close() {
        database.close()
    }

    companion object {
        fun create(context: Context): SourceRegistryRuntime {
            val database = CatalogDatabaseFactory.create(context)
            val repository = RoomLibrarySourceRepository(database)
            val registry = SourceRegistry(
                repository = repository,
                permissionGateway = AndroidTreePermissionGateway(context.contentResolver),
            )
            return SourceRegistryRuntime(database, repository, registry)
        }
    }
}

object SourceRegistryFactory {
    fun create(context: Context): SourceRegistryRuntime = SourceRegistryRuntime.create(context.applicationContext)
}
