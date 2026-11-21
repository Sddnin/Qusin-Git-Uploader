package com.example.core.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface TokenStore {
  fun saveToken(token: String)
  fun getToken(): String?
  fun hasToken(): Boolean
  fun clearToken()
}

class KeystoreTokenManager(context: Context) : TokenStore {
  private val prefs: SharedPreferences =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  @Synchronized
  override fun saveToken(token: String) {
    val sanitized = token.trim()
    if (sanitized.isEmpty()) {
      clearToken()
      return
    }
    val secretKey = getOrCreateSecretKey()
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, secretKey)
    val iv = cipher.iv
    val encrypted = cipher.doFinal(sanitized.toByteArray(StandardCharsets.UTF_8))

    prefs.edit()
      .putString(KEY_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
      .putString(KEY_CIPHER, Base64.encodeToString(encrypted, Base64.NO_WRAP))
      .apply()
  }

  @Synchronized
  override fun getToken(): String? {
    val ivStr = prefs.getString(KEY_IV, null) ?: return null
    val cipherStr = prefs.getString(KEY_CIPHER, null) ?: return null
    return try {
      val iv = Base64.decode(ivStr, Base64.NO_WRAP)
      val cipherBytes = Base64.decode(cipherStr, Base64.NO_WRAP)
      val secretKey = getOrCreateSecretKey()
      val cipher = Cipher.getInstance(TRANSFORMATION)
      cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
      val plainBytes = cipher.doFinal(cipherBytes)
      String(plainBytes, StandardCharsets.UTF_8)
    } catch (_: Exception) {
      null
    }
  }

  override fun hasToken(): Boolean = !getToken().isNullOrBlank()

  @Synchronized
  override fun clearToken() {
    prefs.edit().remove(KEY_IV).remove(KEY_CIPHER).apply()
  }

  private fun getOrCreateSecretKey(): SecretKey {
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
    if (existing != null) return existing.secretKey

    val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
    val spec = KeyGenParameterSpec.Builder(
      KEY_ALIAS,
      KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
    )
      .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
      .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
      .setKeySize(256)
      .setRandomizedEncryptionRequired(true)
      .build()
    keyGenerator.init(spec)
    return keyGenerator.generateKey()
  }

  companion object {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "qusin_github_pat_aes_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val PREFS_NAME = "qusin_secure_credentials"
    private const val KEY_IV = "pat_iv"
    private const val KEY_CIPHER = "pat_cipher"
  }
}
