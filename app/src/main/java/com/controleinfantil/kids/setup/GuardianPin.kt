package com.controleinfantil.kids.setup

import android.content.Context
import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PIN que protege a área do responsável.
 *
 * Sem ele, a criança abriria a configuração com um toque longo e poderia liberar o
 * navegador, as Configurações ou a Play Store na tela de escolha de apps — o que
 * derruba todo o controle.
 *
 * Guardamos apenas o hash com sal (PBKDF2), nunca o PIN. Como um PIN é curto, o
 * custo alto de derivação é o que torna a tentativa e erro lenta.
 */
object GuardianPin {

    private const val PREFS = "guardian"
    private const val KEY_HASH = "pin_hash"
    private const val KEY_SALT = "pin_salt"

    private const val ITERATIONS = 50_000
    private const val KEY_LENGTH_BITS = 256
    const val MIN_LENGTH = 4

    fun isSet(context: Context): Boolean =
        prefs(context).contains(KEY_HASH)

    fun set(context: Context, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs(context).edit()
            .putString(KEY_SALT, encode(salt))
            .putString(KEY_HASH, encode(derive(pin, salt)))
            .apply()
    }

    fun verify(context: Context, pin: String): Boolean {
        val p = prefs(context)
        val salt = p.getString(KEY_SALT, null)?.let(::decode) ?: return false
        val expected = p.getString(KEY_HASH, null)?.let(::decode) ?: return false
        return constantTimeEquals(derive(pin, salt), expected)
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS))
            .encoded

    /** Comparação sem atalho, para não vazar o acerto parcial pelo tempo. */
    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }

    private fun encode(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode(value: String): ByteArray =
        Base64.decode(value, Base64.NO_WRAP)

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
