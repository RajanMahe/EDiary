package com.aburv.kurippidu.data.security

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

/**
 * The database passphrase: 32 random bytes made once on this phone.
 *
 * It is stored encrypted with an AES key that lives in the Android Keystore
 * (hardware-backed where available) and can't be read out of the device.
 * Android's cloud backup excludes it (see backup rules), because the
 * Keystore key can't move to another phone.
 */
object DatabaseKey {

    const val PREFS_FILE = "kurippidu_db_key"
    private const val PREF_PASSPHRASE = "passphrase"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "kurippidu_db_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    /** True if a passphrase was already made (an encrypted database may exist). */
    fun exists(context: Context): Boolean =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE).contains(PREF_PASSPHRASE)

    /**
     * Returns the passphrase as text (64 hex characters), creating it on first use.
     * Text form is used so SQL `ATTACH ... KEY` and the Room factory derive the same key.
     */
    @Synchronized
    fun getOrCreate(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        prefs.getString(PREF_PASSPHRASE, null)?.let { stored ->
            return decrypt(stored)
        }

        val random = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val passphrase = random.joinToString("") { "%02x".format(it) }
        // commit(): the key must be on disk before any database is encrypted with it
        check(prefs.edit().putString(PREF_PASSPHRASE, encrypt(passphrase)).commit()) {
            "Could not save database key"
        }
        return passphrase
    }

    private fun keystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, keystoreKey()) }
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return b64(cipher.iv) + ":" + b64(encrypted)
    }

    private fun decrypt(stored: String): String {
        val (iv, data) = stored.split(":").map { Base64.decode(it, Base64.NO_WRAP) }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(128, iv))
        }
        return String(cipher.doFinal(data), Charsets.UTF_8)
    }

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
}
