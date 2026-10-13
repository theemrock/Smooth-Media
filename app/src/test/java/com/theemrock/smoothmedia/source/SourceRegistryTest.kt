package com.theemrock.smoothmedia.source

import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceEntity
import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceType
import com.theemrock.smoothmedia.catalog.database.entity.SourceAvailability
import com.theemrock.smoothmedia.catalog.database.entity.SourcePermissionState
import com.theemrock.smoothmedia.catalog.database.entity.SourceSelectionState
import com.theemrock.smoothmedia.catalog.repository.LibrarySourceRepository
import com.theemrock.smoothmedia.catalog.repository.SourceRegistrationWriteResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceRegistryTest {
    @Test
    fun firstAndRepeatedSelection_returnAddedThenAlreadyRegistered() {
        val repository = FakeRepository()
        val registry = SourceRegistry(repository, FakePermissionGateway())

        val added = registry.register(selection(), 1_000)
        val repeated = registry.register(selection(), 2_000)

        assertTrue(added is SourceRegistrationResult.Added)
        assertTrue(repeated is SourceRegistrationResult.AlreadyRegistered)
        assertEquals(1, repository.listAll().size)
        assertEquals((added as SourceRegistrationResult.Added).sourceId, (repeated as SourceRegistrationResult.AlreadyRegistered).sourceId)
        assertEquals(1_000, repository.listAll().single().createdAtEpochMs)
    }

    @Test
    fun removedTreeSelection_reactivatesSameSourceAndPreservesCreationTime() {
        val repository = FakeRepository()
        val registry = SourceRegistry(repository, FakePermissionGateway())
        val sourceId = (registry.register(selection(), 1_000) as SourceRegistrationResult.Added).sourceId
        repository.markRemoved(sourceId, 2_000)

        val result = registry.register(selection(), 3_000)

        assertEquals(SourceRegistrationResult.Reactivated(sourceId), result)
        assertEquals(SourceSelectionState.ACTIVE, repository.find(sourceId)!!.selectionState)
        assertEquals(1_000, repository.find(sourceId)!!.createdAtEpochMs)
    }

    @Test
    fun invalidIdentity_isRejectedBeforePermissionMutation() {
        val gateway = FakePermissionGateway()
        val repository = FakeRepository()

        val result = SourceRegistry(repository, gateway).register(selection(authority = ""), 1_000)

        assertEquals(SourceRegistrationResult.InvalidUri, result)
        assertEquals(0, gateway.takeCount)
        assertTrue(repository.listAll().isEmpty())
    }

    @Test
    fun deniedPermission_doesNotCreateSource() {
        val repository = FakeRepository()
        val gateway = FakePermissionGateway(takeResult = TreePermissionTakeResult.PermissionDenied)

        val result = SourceRegistry(repository, gateway).register(selection(), 1_000)

        assertEquals(SourceRegistrationResult.PermissionDenied, result)
        assertTrue(repository.listAll().isEmpty())
    }

    @Test
    fun providerUnavailable_registersSourceWithoutClaimingAvailability() {
        val repository = FakeRepository()
        val gateway = FakePermissionGateway(takeResult = TreePermissionTakeResult.Unavailable(1))

        val result = SourceRegistry(repository, gateway).register(selection(), 1_000)

        assertTrue(result is SourceRegistrationResult.Unavailable)
        val stored = repository.listAll().single()
        assertEquals(SourcePermissionState.GRANTED, stored.permissionState)
        assertEquals(SourceAvailability.UNAVAILABLE, stored.availability)
    }

    @Test
    fun permissionPersistedButRoomWriteFails_reportsPartialFailure() {
        val repository = FakeRepository(failRegistration = true)

        val result = SourceRegistry(repository, FakePermissionGateway()).register(selection(), 1_000)

        assertEquals(SourceRegistrationResult.PermissionPersistedButStorageFailed, result)
    }

    @Test
    fun refresh_distinguishesPermissionLossUnavailableAndRecovery() {
        val repository = FakeRepository()
        val gateway = FakePermissionGateway()
        val registry = SourceRegistry(repository, gateway)
        val sourceId = (registry.register(selection(), 1_000) as SourceRegistrationResult.Added).sourceId

        gateway.inspectResult = TreePermissionInspection.PermissionLost
        assertEquals(SourceRefreshResult.PermissionLost(sourceId), registry.refresh(sourceId, 2_000))
        assertEquals(SourceAvailability.PERMISSION_LOST, repository.find(sourceId)!!.availability)

        gateway.inspectResult = TreePermissionInspection.Unavailable(1)
        assertEquals(SourceRefreshResult.Unavailable(sourceId), registry.refresh(sourceId, 3_000))
        assertEquals(SourceAvailability.UNAVAILABLE, repository.find(sourceId)!!.availability)

        gateway.inspectResult = TreePermissionInspection.Available(1)
        assertEquals(SourceRefreshResult.Available(sourceId), registry.refresh(sourceId, 4_000))
        assertEquals(SourceAvailability.AVAILABLE, repository.find(sourceId)!!.availability)
        assertEquals(1, repository.listAll().size)
    }

    @Test
    fun remove_marksSourceRemovedThenReleasesPermission() {
        val repository = FakeRepository()
        val gateway = FakePermissionGateway()
        val registry = SourceRegistry(repository, gateway)
        val sourceId = (registry.register(selection(), 1_000) as SourceRegistrationResult.Added).sourceId

        val result = registry.remove(sourceId, 2_000)

        assertEquals(SourceRemovalResult.Removed(sourceId), result)
        assertEquals(SourceSelectionState.REMOVED, repository.find(sourceId)!!.selectionState)
        assertEquals(listOf(selection().rootUri), gateway.releasedUris)
    }

    @Test
    fun releaseFailure_reportsPartialRemovalAndKeepsRemovedState() {
        val repository = FakeRepository()
        val gateway = FakePermissionGateway(releaseResult = TreePermissionReleaseResult.Failed)
        val registry = SourceRegistry(repository, gateway)
        val sourceId = (registry.register(selection(), 1_000) as SourceRegistrationResult.Added).sourceId

        val result = registry.remove(sourceId, 2_000)

        assertEquals(SourceRemovalResult.RemovedWithPermissionReleaseFailure(sourceId), result)
        assertEquals(SourceSelectionState.REMOVED, repository.find(sourceId)!!.selectionState)
    }

    @Test
    fun removedSource_canRetryPermissionReleaseAndClearsCurrentAccessState() {
        val repository = FakeRepository()
        val gateway = FakePermissionGateway(releaseResult = TreePermissionReleaseResult.Failed)
        val registry = SourceRegistry(repository, gateway)
        val sourceId = (registry.register(selection(), 1_000) as SourceRegistrationResult.Added).sourceId

        assertEquals(
            SourceRemovalResult.RemovedWithPermissionReleaseFailure(sourceId),
            registry.remove(sourceId, 2_000),
        )
        gateway.releaseResult = TreePermissionReleaseResult.Released

        assertEquals(SourceRemovalResult.AlreadyRemoved(sourceId), registry.remove(sourceId, 3_000))
        assertEquals(2, gateway.releasedUris.size)
        assertEquals(0, repository.find(sourceId)!!.persistedPermissionFlags)
        assertEquals(SourcePermissionState.LOST, repository.find(sourceId)!!.permissionState)
        assertEquals(SourceAvailability.PERMISSION_LOST, repository.find(sourceId)!!.availability)
    }

    @Test
    fun gatewayException_isConvertedToControlledRemovalFailure() {
        val repository = FakeRepository()
        val gateway = FakePermissionGateway()
        val registry = SourceRegistry(repository, gateway)
        val sourceId = (registry.register(selection(), 1_000) as SourceRegistrationResult.Added).sourceId
        gateway.throwOnRelease = true

        assertEquals(
            SourceRemovalResult.RemovedWithPermissionReleaseFailure(sourceId),
            registry.remove(sourceId, 2_000),
        )
        assertEquals(SourceSelectionState.REMOVED, repository.find(sourceId)!!.selectionState)
    }

    @Test
    fun roomRemovalFailure_doesNotReleasePermission() {
        val repository = FakeRepository()
        val gateway = FakePermissionGateway()
        val registry = SourceRegistry(repository, gateway)
        val sourceId = (registry.register(selection(), 1_000) as SourceRegistrationResult.Added).sourceId
        repository.failRemoval = true

        val result = registry.remove(sourceId, 2_000)

        assertEquals(SourceRemovalResult.StorageFailure, result)
        assertTrue(gateway.releasedUris.isEmpty())
        assertEquals(SourceSelectionState.ACTIVE, repository.find(sourceId)!!.selectionState)
    }

    @Test
    fun unknownSource_operationsReturnNotFound() {
        val registry = SourceRegistry(FakeRepository(), FakePermissionGateway())

        assertEquals(SourceRefreshResult.NotFound, registry.refresh("missing", 1_000))
        assertEquals(SourceRemovalResult.NotFound, registry.remove("missing", 1_000))
    }

    private fun selection(authority: String = "provider") = SelectedTree(
        rootUri = "content://provider/tree/primary%3AMusic",
        authority = authority,
        treeDocumentId = "primary:Music",
        grantedFlags = 1,
    )
}

private class FakePermissionGateway(
    private val takeResult: TreePermissionTakeResult = TreePermissionTakeResult.Granted(1),
    var releaseResult: TreePermissionReleaseResult = TreePermissionReleaseResult.Released,
) : TreePermissionGateway {
    var takeCount = 0
    var inspectResult: TreePermissionInspection = TreePermissionInspection.Available(1)
    val releasedUris = mutableListOf<String>()
    var throwOnRelease = false

    override fun takeAndVerify(selection: SelectedTree): TreePermissionTakeResult {
        takeCount += 1
        return takeResult
    }

    override fun inspect(rootUri: String): TreePermissionInspection = inspectResult

    override fun release(rootUri: String, persistedPermissionFlags: Int): TreePermissionReleaseResult {
        if (throwOnRelease) error("platform failure")
        releasedUris += rootUri
        return releaseResult
    }
}

private class FakeRepository(
    private val failRegistration: Boolean = false,
) : LibrarySourceRepository {
    private val sources = linkedMapOf<String, LibrarySourceEntity>()
    var failRemoval = false

    override fun insert(source: LibrarySourceEntity) {
        sources[source.sourceId] = source
    }

    override fun find(sourceId: String): LibrarySourceEntity? = sources[sourceId]

    override fun findByRootUri(rootUri: String): LibrarySourceEntity? = sources.values.firstOrNull { it.rootUri == rootUri }

    override fun listAll(): List<LibrarySourceEntity> = sources.values.toList()

    override fun registerOrReactivate(source: LibrarySourceEntity): SourceRegistrationWriteResult {
        if (failRegistration) error("storage failure")
        val existing = sources[source.sourceId]
        if (existing == null) {
            sources[source.sourceId] = source
            return SourceRegistrationWriteResult.Added
        }
        val result = if (existing.selectionState == SourceSelectionState.REMOVED) {
            SourceRegistrationWriteResult.Reactivated
        } else {
            SourceRegistrationWriteResult.AlreadyActive
        }
        sources[source.sourceId] = source.copy(createdAtEpochMs = existing.createdAtEpochMs)
        return result
    }

    override fun updateAccessState(
        sourceId: String,
        persistedPermissionFlags: Int,
        permissionState: SourcePermissionState,
        availability: SourceAvailability,
        updatedAtEpochMs: Long,
    ): Boolean {
        val source = sources[sourceId] ?: return false
        sources[sourceId] = source.copy(
            persistedPermissionFlags = persistedPermissionFlags,
            permissionState = permissionState,
            availability = availability,
            updatedAtEpochMs = updatedAtEpochMs,
        )
        return true
    }

    override fun markRemoved(sourceId: String, updatedAtEpochMs: Long): Boolean {
        if (failRemoval) return false
        val source = sources[sourceId] ?: return false
        sources[sourceId] = source.copy(
            selectionState = SourceSelectionState.REMOVED,
            updatedAtEpochMs = updatedAtEpochMs,
        )
        return true
    }
}
