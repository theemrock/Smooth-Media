package com.theemrock.smoothmedia.artwork

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException

/** Shares only the exact in-flight key; completed failures are never retained. */
class ArtworkLoadCoordinator<K, V> {
    private val inFlight = ConcurrentHashMap<K, CompletableFuture<V?>>()

    fun load(key: K, loader: () -> V?): V? = load(key, loader, {})

    fun load(key: K, loader: () -> V?, onJoinedInFlight: () -> Unit = {}): V? {
        val created = CompletableFuture<V?>()
        val active = inFlight.putIfAbsent(key, created)
        if (active != null) {
            onJoinedInFlight()
            return await(active)
        }
        return try {
            loader().also(created::complete)
        } catch (error: Throwable) {
            created.completeExceptionally(error)
            throw error
        } finally {
            inFlight.remove(key, created)
        }
    }

    private fun await(future: CompletableFuture<V?>): V? = try {
        future.get()
    } catch (interrupted: InterruptedException) {
        Thread.currentThread().interrupt()
        throw interrupted
    } catch (failed: ExecutionException) {
        throw (failed.cause ?: failed)
    }
}
