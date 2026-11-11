package es.robertodorado.robguard.vault

/**
 * Fusión de tres vías. Un registro ausente equivale a una eliminación,
 * de modo que una eliminación nunca resucita por defecto.
 * Dos ediciones distintas del mismo registro son conflicto, NO last-write-wins.
 */
internal object VaultMerge {
    data class Result(val merged: List<VaultEntry>, val conflicts: Set<String>) {
        val canPublish: Boolean get() = conflicts.isEmpty()
    }

    /**
     * Resolución voluntaria y sin pérdida: conserva la versión remota
     * y añade la versión local con otro ID para revisión posterior.
     * El caso editar/borrar recupera la versión editada.
     */
    fun resolveKeepingCopies(
        baseline: List<VaultEntry>, local: List<VaultEntry>, remote: List<VaultEntry>,
        newId: () -> String = { java.util.UUID.randomUUID().toString() }
    ): List<VaultEntry> {
        val attempt = merge(baseline, local, remote)
        if (attempt.canPublish) return attempt.merged
        val resolved = attempt.merged.toMutableList()
        val localMap = local.associateBy { it.id }
        val remoteMap = remote.associateBy { it.id }
        val usedIds = (baseline + local + remote).map { it.id }.toMutableSet()
        for (id in attempt.conflicts.sorted()) {
            remoteMap[id]?.let { resolved += it }
            localMap[id]?.let { entry ->
                val cloneId = newId()
                require(cloneId.isNotBlank() && cloneId.length <= 80 &&
                    usedIds.add(cloneId)) { "ID de copia no válido" }
                val suffix = " (copia local)"
                resolved += entry.copy(
                    id = cloneId,
                    title = entry.title.take(200 - suffix.length) + suffix
                ).validate()
            }
        }
        require(resolved.map { it.id }.toSet().size == resolved.size)
        return resolved
    }

    fun merge(
        baseline: List<VaultEntry>,
        local: List<VaultEntry>,
        remote: List<VaultEntry>
    ): Result {
        fun asMap(items: List<VaultEntry>): Map<String, VaultEntry> {
            require(items.map { it.id }.toSet().size == items.size) {
                "IDs duplicados"
            }
            return items.associateBy { it.id }
        }
        val b = asMap(baseline)
        val l = asMap(local)
        val r = asMap(remote)
        val merged = mutableListOf<VaultEntry>()
        val conflicts = mutableSetOf<String>()
        for (id in (b.keys + l.keys + r.keys).sorted()) {
            val prior = b[id]
            val ours = l[id]
            val theirs = r[id]
            val selected = when {
                ours == theirs -> ours
                ours == prior -> theirs
                theirs == prior -> ours
                else -> {
                    conflicts += id
                    null
                }
            }
            if (id !in conflicts && selected != null) merged += selected
        }
        return Result(merged, conflicts)
    }
}
