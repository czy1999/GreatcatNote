package com.greatcat.note.mobile

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal const val PERSONAL_REMOTE_URL = "https://github.com/czy1999/Greatcat.git"
internal const val PERSONAL_BRANCH = "master"
internal const val PERSONAL_USERNAME = "czy1999"

data class RepoSettings(
    val remoteUrl: String = PERSONAL_REMOTE_URL,
    val branch: String = PERSONAL_BRANCH,
    val username: String = PERSONAL_USERNAME,
)

class SecureSettings(context: Context) {
    private val preferences = context.getSharedPreferences("greatcat_mobile", Context.MODE_PRIVATE)

    fun load(): RepoSettings = RepoSettings()

    fun hasToken(): Boolean = preferences.contains(KEY_TOKEN)

    fun saveToken(newToken: String) {
        preferences.edit().putString(KEY_TOKEN, encrypt(newToken.trim())).apply()
    }

    fun token(): String = preferences.getString(KEY_TOKEN, null)?.let(::decrypt).orEmpty()

    fun clearToken() {
        preferences.edit().remove(KEY_TOKEN).apply()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey(), SecureRandom())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val pieces = value.split(':', limit = 2)
        require(pieces.size == 2) { "Invalid encrypted credential" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(128, Base64.decode(pieces[0], Base64.NO_WRAP)),
        )
        return String(cipher.doFinal(Base64.decode(pieces[1], Base64.NO_WRAP)), Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "greatcatnote.git-token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_TOKEN = "token"
    }
}
