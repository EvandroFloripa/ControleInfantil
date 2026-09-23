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

    private const val KEY_FAILURES = "pin_failures"
    private const val KEY_LOCKED_UNTIL = "pin_locked_until"
    const val MAX_ATTEMPTS = 5
    private const val BASE_LOCKOUT_MS = 30_000L
    private const val MAX_LOCKOUT_MS = 60 * 60_000L

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

    /**
     * Quanto falta, em ms, para aceitar o PIN de novo (0 = liberado).
     *
     * A contagem fica gravada, e não na tela: se ficasse na tela, bastaria fechá-la
     * e abrir outra (ou girar o aparelho) para ganhar tentativas novas.
     */
    fun lockoutRemainingMs(context: Context): Long {
        val p = prefs(context)
        val now = System.currentTimeMillis()
        val remaining = p.getLong(KEY_LOCKED_UNTIL, 0L) - now
        if (remaining > MAX_LOCKOUT_MS) {
            // O relógio voltou para trás depois da trava (ex.: estava adiantado e a
            // hora da rede corrigiu). Regrava o prazo; só limitar o valor devolvido
            // manteria a trava até a hora antiga, que pode estar dias à frente.
            p.edit().putLong(KEY_LOCKED_UNTIL, now + MAX_LOCKOUT_MS).apply()
            return MAX_LOCKOUT_MS
        }
        // Adiantar o relógio encurta a trava, mas no quiosque a criança não abre as
        // Configurações.
        return remaining.coerceAtLeast(0L)
    }

    /**
     * Registra um PIN errado. A cada [MAX_ATTEMPTS] erros seguidos, trava por um
     * tempo que dobra a cada rodada. Devolve a duração da trava (0 se não travou).
     */
    fun registerFailure(context: Context): Long {
        val p = prefs(context)
        val failures = p.getInt(KEY_FAILURES, 0) + 1
        val lockout = if (failures % MAX_ATTEMPTS == 0) {
            val round = failures / MAX_ATTEMPTS - 1
            (BASE_LOCKOUT_MS shl round.coerceAtMost(10)).coerceAtMost(MAX_LOCKOUT_MS)
        } else 0L
        p.edit()
            .putInt(KEY_FAILURES, failures)
            .apply { if (lockout > 0) putLong(KEY_LOCKED_UNTIL, System.currentTimeMillis() + lockout) }
            .apply()
        return lockout
    }

    fun clearFailures(context: Context) {
        prefs(context).edit().remove(KEY_FAILURES).remove(KEY_LOCKED_UNTIL).apply()
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
