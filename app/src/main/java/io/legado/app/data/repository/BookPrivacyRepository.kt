package io.legado.app.data.repository

import io.legado.app.constant.PreferKey
import io.legado.app.data.dao.BookDao
import io.legado.app.data.dao.BookGroupDao
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import java.text.Normalizer
import java.util.Locale

data class BookRecordKey(
    val name: String,
    val author: String,
)

data class BookPrivacyState(
    val privacyModeEnabled: Boolean,
    val privateGroupMask: Long,
    val visibleBookUrls: Set<String>,
    val visibleBookKeys: Set<BookRecordKey>,
    val visibleBookNames: Set<String>,
    val hiddenBookUrls: Set<String>,
    val hiddenBookKeys: Set<BookRecordKey>,
    val hiddenBookNames: Set<String>,
) {

    fun isReadRecordVisible(
        bookUrl: String?,
        bookName: String,
        bookAuthor: String = "",
    ): Boolean {
        if (!privacyModeEnabled) return true
        if (!bookUrl.isNullOrBlank()) {
            if (bookUrl in hiddenBookUrls) return false
            if (bookUrl in visibleBookUrls) return true
        }
        val normalizedName = normalizeBookName(bookName)
        val normalizedAuthor = normalizeBookName(bookAuthor)
        if (normalizedAuthor.isNotEmpty()) {
            val key = BookRecordKey(normalizedName, normalizedAuthor)
            if (key in hiddenBookKeys) return false
            if (key in visibleBookKeys) return true
        }
        return normalizedName in visibleBookNames && normalizedName !in hiddenBookNames
    }

    companion object {
        fun create(
            books: List<Book>,
            groups: List<BookGroup>,
            privacyModeEnabled: Boolean,
        ): BookPrivacyState {
            val privateGroupMask = groups.asSequence()
                .filter {
                    (it.groupId > 0L || it.groupId == Long.MIN_VALUE) && it.isPrivate
                }
                .fold(0L) { mask, group -> mask or group.groupId }
            val hiddenBooks = books.filter { (it.group and privateGroupMask) != 0L }
            val visibleBooks = books.filterNot { (it.group and privateGroupMask) != 0L }
            return BookPrivacyState(
                privacyModeEnabled = privacyModeEnabled,
                privateGroupMask = privateGroupMask,
                visibleBookUrls = visibleBooks.mapTo(hashSetOf()) { it.bookUrl },
                visibleBookKeys = visibleBooks.mapTo(hashSetOf()) {
                    BookRecordKey(normalizeBookName(it.name), normalizeBookName(it.author))
                },
                visibleBookNames = visibleBooks.mapTo(hashSetOf()) {
                    normalizeBookName(it.name)
                },
                hiddenBookUrls = hiddenBooks.mapTo(hashSetOf()) { it.bookUrl },
                hiddenBookKeys = hiddenBooks.mapTo(hashSetOf()) {
                    BookRecordKey(normalizeBookName(it.name), normalizeBookName(it.author))
                },
                hiddenBookNames = hiddenBooks.mapTo(hashSetOf()) {
                    normalizeBookName(it.name)
                },
            )
        }
    }
}

class BookPrivacyRepository(
    private val bookDao: BookDao,
    private val bookGroupDao: BookGroupDao,
    private val settingsRepository: SettingsRepository,
) {

    fun observe(): Flow<BookPrivacyState> = combine(
        bookDao.flowAll(),
        bookGroupDao.flowAll(),
        settingsRepository.getBoolean(PreferKey.bookshelfPrivacyMode, true),
        BookPrivacyState::create,
    ).distinctUntilChanged()
}

internal fun normalizeBookName(name: String): String =
    Normalizer.normalize(name, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
        .filterNot { it.isWhitespace() || it == '\u200B' || it == '\uFEFF' }
