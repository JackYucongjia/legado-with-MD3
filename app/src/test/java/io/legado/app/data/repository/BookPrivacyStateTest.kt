package io.legado.app.data.repository

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookPrivacyStateTest {

    private val hiddenGroup = BookGroup(groupId = 32L, isPrivate = true)
    private val visibleBook = Book(
        bookUrl = "visible-url",
        name = "MS攻略",
        author = "公开作者",
        group = 0L,
    )
    private val hiddenBook = Book(
        bookUrl = "hidden-url",
        name = "秘密书",
        author = "隐藏作者",
        group = 32L,
    )

    @Test
    fun `privacy mode only exposes records linked to visible books`() {
        val state = BookPrivacyState.create(
            books = listOf(visibleBook, hiddenBook),
            groups = listOf(hiddenGroup),
            privacyModeEnabled = true,
        )

        assertTrue(state.isReadRecordVisible("visible-url", "旧标题"))
        assertTrue(state.isReadRecordVisible(null, "ＭＳ 攻略", "公开作者"))
        assertTrue(state.isReadRecordVisible("stale-url", "ＭＳ 攻略", "公开作者"))
        assertFalse(state.isReadRecordVisible("hidden-url", "任意标题"))
        assertFalse(state.isReadRecordVisible(null, "秘密书"))
        assertFalse(state.isReadRecordVisible("stale-url", "秘密书"))
        assertFalse(state.isReadRecordVisible(null, "无法关联的历史别名"))
    }

    @Test
    fun `author disambiguates visible and hidden books with the same title`() {
        val state = BookPrivacyState.create(
            books = listOf(
                visibleBook.copy(name = "同名书"),
                hiddenBook.copy(name = "同名书"),
            ),
            groups = listOf(hiddenGroup),
            privacyModeEnabled = true,
        )

        assertTrue(state.isReadRecordVisible("stale-url", "同名书", "公开作者"))
        assertFalse(state.isReadRecordVisible("stale-url", "同名书", "隐藏作者"))
        assertFalse(state.isReadRecordVisible("stale-url", "同名书"))
    }

    @Test
    fun `privacy mode off exposes all historical records`() {
        val state = BookPrivacyState.create(
            books = listOf(visibleBook, hiddenBook),
            groups = listOf(hiddenGroup),
            privacyModeEnabled = false,
        )

        assertTrue(state.isReadRecordVisible("hidden-url", "秘密书"))
        assertTrue(state.isReadRecordVisible(null, "无法关联的历史别名"))
    }
}
