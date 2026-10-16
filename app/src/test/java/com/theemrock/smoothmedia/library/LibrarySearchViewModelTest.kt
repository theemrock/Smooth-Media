package com.theemrock.smoothmedia.library

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySearchViewModelTest {
    @Test
    fun blankQueryClearsResultsWithoutRepositoryCall() {
        val calls = AtomicInteger()
        val viewModel = LibrarySearchViewModel(
            repository = object : LibrarySearchRepository {
                override fun search(query: String, limit: Int, offset: Int): LibrarySearchPage {
                    calls.incrementAndGet()
                    return LibrarySearchPage()
                }
            },
            scheduler = Executors.newSingleThreadScheduledExecutor(),
        )

        viewModel.onQueryChanged(" ")
        assertEquals("", viewModel.state.value.results.songs.joinToString())
        assertEquals(0, calls.get())
        viewModel.close()
    }

    @Test
    fun obsoleteDebouncedQueryIsCancelled() {
        val queries = mutableListOf<String>()
        val viewModel = LibrarySearchViewModel(
            repository = object : LibrarySearchRepository {
                override fun search(query: String, limit: Int, offset: Int): LibrarySearchPage {
                    queries += query
                    return LibrarySearchPage()
                }
            },
            scheduler = Executors.newSingleThreadScheduledExecutor(),
        )

        viewModel.onQueryChanged("old")
        viewModel.onQueryChanged("new")
        Thread.sleep(400)

        assertTrue(queries.contains("new"))
        assertTrue(!queries.contains("old"))
        viewModel.close()
    }
}
