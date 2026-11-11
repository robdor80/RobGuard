package es.robertodorado.robguard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardSectionTest {
    @Test fun navigationHasThreeDistinctPages() {
        assertEquals(3, GuardSection.entries.size)
        assertEquals(3, GuardSection.entries.map { it.title }.toSet().size)
        assertTrue(GuardSection.entries.any { it == GuardSection.VAULT })
    }
}
