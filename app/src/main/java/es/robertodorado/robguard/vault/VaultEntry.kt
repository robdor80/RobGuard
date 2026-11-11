package es.robertodorado.robguard.vault

/** Modelo desvinculado de UI/Firebase. El registro completo se cifra en disco. */
internal data class VaultEntry(
    val id: String,
    val title: String,
    val username: String,
    val password: String,
    val website: String,
    val category: String,
    val notes: String,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun validate(): VaultEntry = apply {
        require(id.isNotBlank() && id.length <= 80) { "ID no válido" }
        require(title.isNotBlank() && title.length <= 200) { "Indica un título (máx. 200)" }
        require(username.length <= 500) { "Usuario demasiado largo" }
        require(password.length <= 4096) { "Contraseña demasiado larga" }
        require(website.length <= 1024) { "Dirección web demasiado larga" }
        require(category.length <= 100) { "Categoría demasiado larga" }
        require(notes.length <= 10000) { "Notas demasiado largas" }
        require(createdAt > 0 && updatedAt >= createdAt) { "Fechas no válidas" }
    }
}
