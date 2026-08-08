package io.legado.app.data.audiobook

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AudiobookTokenStore(context: Context) {

    private val tokenDirectory = File(context.noBackupFilesDir, TOKEN_DIRECTORY)
    private val keyAlias = "${context.packageName}.audiobookshelf.refresh"

    fun saveRefreshToken(profileId: Long, refreshToken: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        }
        val encrypted = cipher.doFinal(refreshToken.toByteArray(Charsets.UTF_8))
        tokenDirectory.mkdirs()
        tokenFile(profileId).writeText(
            buildString {
                append(Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                append('\n')
                append(Base64.encodeToString(encrypted, Base64.NO_WRAP))
            },
            Charsets.UTF_8
        )
    }

    fun readRefreshToken(profileId: Long): String? {
        val file = tokenFile(profileId)
        if (!file.isFile) return null
        return runCatching {
            val lines = file.readLines(Charsets.UTF_8)
            require(lines.size == 2)
            val iv = Base64.decode(lines[0], Base64.NO_WRAP)
            val encrypted = Base64.decode(lines[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(
                    Cipher.DECRYPT_MODE,
                    getOrCreateSecretKey(),
                    GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
                )
            }
            cipher.doFinal(encrypted).toString(Charsets.UTF_8)
        }.getOrNull()
    }

    fun delete(profileId: Long) {
        tokenFile(profileId).delete()
    }

    private fun tokenFile(profileId: Long): File {
        return File(tokenDirectory, "$profileId.token")
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEY_STORE
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val TOKEN_DIRECTORY = "audiobookshelf"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
