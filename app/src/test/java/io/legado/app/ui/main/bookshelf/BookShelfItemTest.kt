package io.legado.app.ui.main.bookshelf

import org.junit.Assert.assertTrue
import org.junit.Test

class BookShelfItemTest {

    @Test
    fun `marks book in highest-bit private group as hidden`() {
        val book = BookShelfItem(
            bookUrl = "book-url",
            name = "book",
            author = "author",
            origin = "origin",
            originName = "origin-name",
            coverUrl = null,
            customCoverUrl = null,
            durChapterTitle = null,
            durChapterTime = 0L,
            durChapterPos = 0,
            latestChapterTitle = null,
            latestChapterTime = 0L,
            lastCheckCount = 0,
            totalChapterNum = 0,
            durChapterIndex = 0,
            type = 0,
            group = Long.MIN_VALUE,
            order = 0,
        )

        assertTrue(book.toUiItem(privateGroupMask = Long.MIN_VALUE).isHidden)
    }
}
