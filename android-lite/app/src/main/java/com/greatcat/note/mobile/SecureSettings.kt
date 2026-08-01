package com.greatcat.note.mobile

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

data class RepoSettings(
    val remoteUrl: String = "",
    val branch: String = "main",
    val username: String = "",
)

class SecureSettings(context: Context) {
    private val preferences = context.getSharedPreferences("greatcat_mobile", Context.MODE_PRIVATE)

    fun load(): RepoSettings = RepoSettings(
        remoteUrl = preferences.getString(KEY_REMOTE, "").orEmpty(),
        branch = preferences.getString(KEY_BRANCH, "main").orEmpty().ifBlank { "main" },
        username = preferences.getString(KEY_USERNAME, "").orEmpty(),
    )

    fun hasToken(): Boolean = preferences.contains(KEY_TOKEN)

    fun save(settings: RepoSettings, newToken: String) {
        preferences.edit()
            .putString(KEY_REMOTE, settings.remoteUrl.trim())
            .putString(KEY_BRANCH, settings.branch.trim())
            .putString(KEY_USERNAME, settings.username.trim())
            .apply()
        if (newToken.isNotBlank()) {
            preferences.edit().putString(KEY_TOKEN, encrypt(newToken.trim())).apply()
        }
    }

    fun token(): String = preferences.getString(KEY_TOKEN, null)?.let(::decrypt).orEmpty()

    fun clearToken() {
        preferences.edit().remove(KEY_TOKEN).apply()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
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
            IvParameterSpec(Base64.decode(pieces[0], Base64.NO_WRAP)),
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
        const val KEY_REMOTE = "remote_url"
        const val KEY_BRANCH = "branch"
        const val KEY_USERNAME = "username"
        const val KEY_TOKEN = "token"
    }
}
