package com.greatcat.note.mobile

import android.content.Context

internal const val PERSONAL_REMOTE_URL = "https://github.com/czy1999/Greatcat.git"
internal const val PERSONAL_BRANCH = "master"
internal const val PERSONAL_USERNAME = "czy1999"

data class RepoSettings(
    val remoteUrl: String = PERSONAL_REMOTE_URL,
    val branch: String = PERSONAL_BRANCH,
    val username: String = PERSONAL_USERNAME,
)

class SecureSettings(context: Context) {
    private val preferences = context.getSharedPreferences("greatcat_mobile_v2", Context.MODE_PRIVATE)

    fun load(): RepoSettings = RepoSettings()

    fun hasToken(): Boolean = preferences.contains(KEY_TOKEN)

    fun saveToken(newToken: String) {
        preferences.edit().putString(KEY_TOKEN, newToken.trim()).apply()
    }

    fun token(): String = preferences.getString(KEY_TOKEN, "").orEmpty()

    fun clearToken() {
        preferences.edit().remove(KEY_TOKEN).apply()
    }

    private companion object {
        const val KEY_TOKEN = "token"
    }
}
