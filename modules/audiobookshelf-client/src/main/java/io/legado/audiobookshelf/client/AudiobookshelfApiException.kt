package io.legado.audiobookshelf.client

class AudiobookshelfApiException(
    val statusCode: Int,
    message: String
) : Exception(message)
