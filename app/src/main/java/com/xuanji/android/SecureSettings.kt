package com.xuanji.android

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.io.IOException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal fun <T> retryOnceAfterInvalidatedKey(
    operation: () -> T,
    resetInvalidatedKey: () -> Unit,
    isInvalidated: (Exception) -> Boolean,
): T = try {
    operation()
} catch (e: Exception) {
    if (!isInvalidated(e)) throw e
    resetInvalidatedKey()
    operation()
}

/** Stores the provider key in Android Keystore-backed AES-GCM ciphertext. */
internal object SecureSettings {
    private const val PREFS = "settings"
    private const val KEY_ALIAS = "xuanji.settings.key"
    private const val CIPHER = "AES/GCM/NoPadding"

    fun saveApiKey(context: Context, value: String, configure: SharedPreferences.Editor.() -> Unit = {}) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit().apply(configure).remove("apiKey")
        if (value.isBlank()) {
            if (!editor.remove("apiKeyCipher").remove("apiKeyIv").commit()) throw IOException("配置写入失败")
            return
        }
        val cipher = retryOnceAfterInvalidatedKey(
            operation = { Cipher.getInstance(CIPHER).apply { init(Cipher.ENCRYPT_MODE, key()) } },
            resetInvalidatedKey = {
                KeyStore.getInstance("AndroidKeyStore").apply {
                    load(null)
                    deleteEntry(KEY_ALIAS)
                }
            },
            isInvalidated = { it is KeyPermanentlyInvalidatedException },
        )
        val saved = editor
            .putString("apiKeyCipher", Base64.encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP))
            .putString("apiKeyIv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .commit()
        if (!saved) throw IOException("配置写入失败")
    }

    fun loadApiKey(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cipherText = prefs.getString("apiKeyCipher", null)
        val ivText = prefs.getString("apiKeyIv", null)
        if (!cipherText.isNullOrBlank() && !ivText.isNullOrBlank()) {
            runCatching {
                val cipher = Cipher.getInstance(CIPHER)
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    key(),
                    GCMParameterSpec(128, Base64.decode(ivText, Base64.NO_WRAP)),
                )
                return String(cipher.doFinal(Base64.decode(cipherText, Base64.NO_WRAP)), Charsets.UTF_8)
            }
        }
        val legacy = prefs.getString("apiKey", "").orEmpty()
        if (legacy.isNotBlank()) runCatching { saveApiKey(context, legacy) }
        return legacy
    }

    fun saveSearchKey(context: Context, engine: String, value: String) {
        val prefs = context.getSharedPreferences("search_secrets", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        if (value.isBlank()) {
            check(editor.remove("${engine}Cipher").remove("${engine}Iv").commit()) { "搜索密钥保存失败" }
            return
        }
        val cipher = Cipher.getInstance(CIPHER).apply { init(Cipher.ENCRYPT_MODE, key("xuanji.search.key")) }
        check(editor.putString("${engine}Cipher", Base64.encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP))
            .putString("${engine}Iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP)).commit()) { "搜索密钥保存失败" }
    }

    fun loadSearchKey(context: Context, engine: String): String {
        val prefs = context.getSharedPreferences("search_secrets", Context.MODE_PRIVATE)
        val encrypted = prefs.getString("${engine}Cipher", null) ?: return ""
        val iv = prefs.getString("${engine}Iv", null) ?: error("${engine} 搜索密钥不完整，请重新配置")
        return try {
            val cipher = Cipher.getInstance(CIPHER)
            cipher.init(Cipher.DECRYPT_MODE, key("xuanji.search.key"), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (e: Exception) { throw IllegalStateException("${engine} 搜索密钥无法解密，请重新配置", e) }
    }

    private fun key(alias: String = KEY_ALIAS): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }
}
