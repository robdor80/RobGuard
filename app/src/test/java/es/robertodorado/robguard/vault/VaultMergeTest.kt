package es.robertodorado.robguard.vault

import org.junit.Assert.*
import org.junit.Test

class VaultMergeTest {
    private fun record(id: String, password: String = "p") =
        VaultEntry(id, id, "user", password, "", "", "", 1, 1)

    @Test fun independentOfflineEditsMerge() {
        val base = listOf(record("a"), record("b"))
        val local = listOf(record("a", "local"), record("b"))
        val remote = listOf(record("a"), record("b", "remote"))
        val result = VaultMerge.merge(base, local, remote)
        assertTrue(result.canPublish)
        assertEquals("local", result.merged.first { it.id == "a" }.password)
        assertEquals("remote", result.merged.first { it.id == "b" }.password)
    }

    @Test fun deletionWinsOverUnchangedRecord() {
        val base = listOf(record("a"))
        val result = VaultMerge.merge(base, emptyList(), base)
        assertTrue(result.canPublish)
        assertTrue(result.merged.isEmpty())
    }

    @Test fun deleteVersusEditMustNotSilentlyResurrect() {
        val base = listOf(record("a"))
        val result = VaultMerge.merge(base, emptyList(), listOf(record("a", "remote")))
        assertFalse(result.canPublish)
        assertEquals(setOf("a"), result.conflicts)
    }

    @Test fun divergentConcurrentEditMustNotChooseLastWriter() {
        val base = listOf(record("a"))
        val result = VaultMerge.merge(base, listOf(record("a", "local")),
            listOf(record("a", "remote")))
        assertFalse(result.canPublish)
    }

    @Test fun sameEditsDoNotConflict() {
        val base = listOf(record("a"))
        val both = listOf(record("a", "same"))
        assertEquals(both, VaultMerge.merge(base, both, both).merged)
    }

    @Test fun brandNewIndependentRecordsMergeAndDoubleCreateConflicts() {
        assertEquals(2, VaultMerge.merge(emptyList(), listOf(record("a")),
            listOf(record("b"))).merged.size)
        assertFalse(VaultMerge.merge(emptyList(), listOf(record("a", "x")),
            listOf(record("a", "y"))).canPublish)
    }
}
