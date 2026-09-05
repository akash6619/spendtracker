package com.spendtracker.app.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.spendtracker.core.model.SourceMessage
import com.spendtracker.core.importing.SourceFingerprinter
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

/**
 * Produces installation-local, non-reversible identities for source messages.
 *
 * It signs normalized sender, timestamp, and body data with an HMAC-SHA256 key
 * stored inside Android Keystore. Only the hexadecimal digest leaves this class;
 * deleting the key is part of SpendTracker's full local-data reset.
 */
class AndroidSourceFingerprinter : SourceFingerprinter {
    override fun fingerprint(message: SourceMessage): String {
        // NUL separators prevent adjacent fields from producing ambiguous input.
        val normalized = buildString {
            append(message.sender.trim().uppercase())
            append('\u0000')
            append(message.receivedAtEpochMillis)
            append('\u0000')
            append(message.body.trim())
        }
        return Mac.getInstance(ALGORITHM).run {
            init(getOrCreateKey())
            doFinal(normalized.toByteArray(StandardCharsets.UTF_8))
                .joinToString("") { byte -> "%02x".format(byte) }
        }
    }

    fun deleteKey() {
        KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(KEY_ALIAS)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        // The secret remains non-exportable inside Android Keystore.
        return KeyGenerator.getInstance(ALGORITHM, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN)
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "spendtracker_sms_fingerprint_v1"
        const val ALGORITHM = "HmacSHA256"
    }
}
