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
