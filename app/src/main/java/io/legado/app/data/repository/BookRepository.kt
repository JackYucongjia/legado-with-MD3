package io.legado.app.data.repository

import io.legado.app.data.dao.BookChapterDao
import io.legado.app.data.dao.BookDao
import io.legado.app.data.dao.GroupBookCount
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.ui.main.bookshelf.BookShelfItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class BookRepository(
    private val bookDao: BookDao,
    private val bookChapterDao: BookChapterDao,
    private val bookPrivacyRepository: BookPrivacyRepository,
) {
    fun getAllBooks(): Flow<List<Book>> {
        return bookDao.flowAll()
    }

    fun getVisibleBooks(): Flow<List<Book>> {
        return combine(bookDao.flowAll(), bookPrivacyRepository.observe()) { books, privacyState ->
            books.filter {
                privacyState.isReadRecordVisible(it.bookUrl, it.name, it.author)
            }
        }
    }

    suspend fun getBookCoverByNameAndAuthor(bookName: String, bookAuthor: String): String? {
        return getVisibleBook(bookName, bookAuthor)?.getDisplayCover()
    }

    suspend fun getChapterTitle(bookName: String, bookAuthor: String, chapterIndex: Int): String? {
        val bookUrl = getVisibleBook(bookName, bookAuthor)?.bookUrl ?: return null
        return withContext(Dispatchers.IO) {
            bookChapterDao.getChapterTitleByUrlAndIndex(bookUrl, chapterIndex)
        }
    }

    suspend fun getVisibleBook(name: String, author: String): Book? {
        return withContext(Dispatchers.IO) {
            val privacyState = bookPrivacyRepository.observe().first()
            bookDao.getBooks(name, author).firstOrNull {
                privacyState.isReadRecordVisible(it.bookUrl, it.name, it.author)
            }
        }
    }

    suspend fun getBook(bookUrl: String): Book? {
        return withContext(Dispatchers.IO) {
            bookDao.getBook(bookUrl)
        }
    }

    suspend fun getBook(name: String, author: String): Book? {
        return withContext(Dispatchers.IO) {
            bookDao.getBook(name, author)
        }
    }

    fun flowBookShelfByGroup(groupId: Long): Flow<List<BookShelfItem>> {
        return bookDao.flowBookShelfByGroup(groupId)
    }

    fun flowBookShelfIncludingPrivate(): Flow<List<BookShelfItem>> {
        return bookDao.flowBookShelfIncludingPrivate().map { books ->
            books.filterNot { it.isNotShelf }
        }
    }

    fun flowSystemGroupCounts(): Flow<List<GroupBookCount>> {
        return bookDao.flowSystemGroupCounts()
    }

    fun flowAllBookShelfCount(): Flow<Int> {
        return bookDao.flowAllBookShelfCount()
    }

    fun flowUserGroupBookCount(groupId: Long): Flow<Int> {
        return bookDao.flowUserGroupBookCount(groupId)
    }

    fun flowGroupPreview(groupId: Long): Flow<List<BookShelfItem>> {
        return bookDao.flowGroupPreview(groupId)
    }

    suspend fun getChapterCount(bookUrl: String): Int {
        return withContext(Dispatchers.IO) {
            bookChapterDao.getChapterCount(bookUrl)
        }
    }

    suspend fun getVolumeCount(bookUrl: String): Int {
        return withContext(Dispatchers.IO) {
            bookChapterDao.getVolumeCount(bookUrl)
        }
    }

    suspend fun update(vararg book: Book) {
        withContext(Dispatchers.IO) {
            bookDao.update(*book)
        }
    }

    suspend fun getMinOrder(): Int {
        return withContext(Dispatchers.IO) {
            bookDao.minOrder
        }
    }

    suspend fun insert(book: Book) {
        withContext(Dispatchers.IO) {
            bookDao.insert(book)
        }
    }

    suspend fun insertChapters(vararg chapters: BookChapter) {
        withContext(Dispatchers.IO) {
            bookChapterDao.insert(*chapters)
        }
    }

    suspend fun getHasUpdateBooks(): List<Book> {
        return withContext(Dispatchers.IO) {
            bookDao.hasUpdateBooks
        }
    }

    suspend fun replace(oldBook: Book, newBook: Book) {
        withContext(Dispatchers.IO) {
            bookDao.replace(oldBook, newBook)
        }
    }

    suspend fun deleteChaptersByBook(bookUrl: String) {
        withContext(Dispatchers.IO) {
            bookChapterDao.delByBook(bookUrl)
        }
    }

}
