package io.legado.audiobookshelf.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AudiobookshelfApiTest {

    @Test
    fun normalizeBaseUrl_keepsRouterBasePathAndRemovesTrailingSlash() {
        assertEquals(
            "https://example.com/audiobookshelf",
            AudiobookshelfApi.normalizeBaseUrl(" https://example.com/audiobookshelf/ ")
        )
    }

    @Test
    fun normalizeBaseUrl_rejectsMissingScheme() {
        assertThrows(IllegalArgumentException::class.java) {
            AudiobookshelfApi.normalizeBaseUrl("example.com:2048")
        }
    }

    @Test
    fun normalizeBaseUrl_rejectsQueryParameters() {
        assertThrows(IllegalArgumentException::class.java) {
            AudiobookshelfApi.normalizeBaseUrl("https://example.com/?token=secret")
        }
    }
}
