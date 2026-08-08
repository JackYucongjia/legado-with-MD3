package io.legado.app.domain.gateway

import io.legado.app.domain.model.AudiobookHomeSnapshot
import io.legado.app.domain.model.AudiobookServerProfile

interface AudiobookGateway {

    suspend fun getSavedProfile(): AudiobookServerProfile?

    suspend fun connect(
        existingProfileId: Long?,
        name: String,
        baseUrl: String,
        username: String,
        password: String
    ): AudiobookHomeSnapshot

    suspend fun restore(profileId: Long): AudiobookHomeSnapshot

    suspend fun refresh(profileId: Long): AudiobookHomeSnapshot

    suspend fun forget(profileId: Long)
}

class AudiobookAuthenticationRequiredException(
    message: String = "登录已失效，请重新输入密码"
) : Exception(message)
