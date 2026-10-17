package com.theemrock.smoothmedia.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import com.theemrock.smoothmedia.library.dashboardFolderCount

class DashboardLayoutTest {
    @Test
    fun dashboardCategoryStripUsesFiveRealLibraryCategories() {
        assertEquals(
            listOf("Şarkılar", "Albümler", "Sanatçılar", "Türler", "Klasörler"),
            dashboardCategoryLabels,
        )
        assertEquals(5, dashboardCategoryLabels.size)
    }

    @Test
    fun dashboardMenuKeepsApprovedOrderWithoutSourcesEntry() {
        assertEquals(
            listOf("Kütüphane", "Tüm şarkılar", "Albümler", "Sanatçılar", "Türler", "Klasörler", "Ayarlar"),
            dashboardMenuLabels,
        )
        assertEquals(false, dashboardMenuLabels.contains("Kaynaklar"))
    }

    @Test
    fun compactDashboardWidthsKeepCategoryCardsScrollableOnPhoneWidth() {
        assertEquals(96f, dashboardCategoryWidthDp(384f), 0.01f)
        assertEquals(86f, dashboardMediaCardWidthDp(384f), 0.01f)
    }

    @Test
    fun folderCountIsHiddenWhenCatalogHasNoFolders() {
        assertEquals(null, dashboardFolderCount(null))
        assertEquals(null, dashboardFolderCount(0))
        assertEquals(18, dashboardFolderCount(18))
    }
}
