package com.theemrock.smoothmedia.artwork

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkLoadCoordinatorTest {
    @Test
    fun concurrentSameKeyRunsOneLoad() {
        val coordinator = ArtworkLoadCoordinator<String, ByteArray>()
        val calls = AtomicInteger()
        val entered = CountDownLatch(1)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val joined = CountDownLatch(1)
        val release = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            fun load(): ByteArray? {
                ready.countDown()
                assertTrue(start.await(2, TimeUnit.SECONDS))
                return coordinator.load(
                    key = "asset-a|128",
                    loader = {
                        calls.incrementAndGet()
                        entered.countDown()
                        assertTrue(release.await(2, TimeUnit.SECONDS))
                        byteArrayOf(1)
                    },
                    onJoinedInFlight = joined::countDown,
                )
            }
            val first = pool.submit<ByteArray?> { load() }
            val second = pool.submit<ByteArray?> { load() }
            assertTrue(ready.await(2, TimeUnit.SECONDS))
            start.countDown()
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            assertTrue(joined.await(2, TimeUnit.SECONDS))
            release.countDown()

            assertEquals(listOf<Byte>(1), first.get(2, TimeUnit.SECONDS)?.toList())
            assertEquals(listOf<Byte>(1), second.get(2, TimeUnit.SECONDS)?.toList())
            assertEquals(1, calls.get())
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun differentSourcesAndTargetProfilesDoNotShare() {
        val coordinator = ArtworkLoadCoordinator<String, Int>()
        val calls = AtomicInteger()
        assertEquals(1, coordinator.load("asset-a|128") { calls.incrementAndGet() })
        assertEquals(2, coordinator.load("asset-b|128") { calls.incrementAndGet() })
        assertEquals(3, coordinator.load("asset-a|192") { calls.incrementAndGet() })
        assertEquals(3, calls.get())
    }

    @Test
    fun failedLoadIsRemovedAndCanBeRetried() {
        val coordinator = ArtworkLoadCoordinator<String, Int>()
        var calls = 0
        runCatching {
            coordinator.load("asset-a|128") {
                calls++
                error("temporary read failure")
            }
        }
        assertEquals(1, calls)
        assertEquals(7, coordinator.load("asset-a|128") { calls++; 7 })
        assertEquals(2, calls)
    }

    @Test
    fun waitingCallerCanBeInterruptedWithoutCachingCancellation() {
        val coordinator = ArtworkLoadCoordinator<String, Int>()
        val entered = CountDownLatch(1)
        val waitingStarted = CountDownLatch(1)
        val release = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            pool.submit<Int?> {
                coordinator.load("asset-a|128") {
                    entered.countDown()
                    release.await(2, TimeUnit.SECONDS)
                    9
                }
            }
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            val waiting = pool.submit<Int?> {
                waitingStarted.countDown()
                coordinator.load("asset-a|128") { 1 }
            }
            assertTrue(waitingStarted.await(2, TimeUnit.SECONDS))
            waiting.cancel(true)
            release.countDown()
            assertEquals(10, coordinator.load("asset-a|128") { 10 })
        } finally {
            release.countDown()
            pool.shutdownNow()
        }
    }
}
