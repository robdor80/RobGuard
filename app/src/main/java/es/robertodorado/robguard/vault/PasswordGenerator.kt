package es.robertodorado.robguard.vault

import java.security.SecureRandom

internal data class PasswordOptions(
    val length: Int = 20,
    val lowercase: Boolean = true,
    val uppercase: Boolean = true,
    val digits: Boolean = true,
    val symbols: Boolean = true
)

internal object PasswordGenerator {
    private const val LOWER = "abcdefghjkmnpqrstuvwxyz"
    private const val UPPER = "ABCDEFGHJKMNPQRSTUVWXYZ"
    private const val DIGITS = "23456789"
    private const val SYMBOLS = "!@#%&*+-=?"
    private val random = SecureRandom()

    fun generate(options: PasswordOptions): String {
        require(options.length in 12..64) { "Longitud permitida: 12–64" }
        val groups = buildList {
            if (options.lowercase) add(LOWER)
            if (options.uppercase) add(UPPER)
            if (options.digits) add(DIGITS)
            if (options.symbols) add(SYMBOLS)
        }
        require(groups.isNotEmpty()) { "Selecciona al menos un tipo de carácter" }
        val characters = groups.joinToString("")
        val password = CharArray(options.length)
        groups.forEachIndexed { i, group ->
            password[i] = group[random.nextInt(group.length)]
        }
        for (i in groups.size until options.length) {
            password[i] = characters[random.nextInt(characters.length)]
        }
        for (i in password.lastIndex downTo 1) {
            val j = random.nextInt(i + 1)
            val old = password[i]
            password[i] = password[j]
            password[j] = old
        }
        return String(password).also { password.fill('\u0000') }
    }
}
