package es.robertodorado.robguard.devices

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLifecycleTest {
    @Test fun onlyAuthorizedDeviceMayRequestVaultAccess() {
        DeviceStatus.entries.forEach { status ->
            assertEquals(
                status == DeviceStatus.AUTHORIZED,
                DeviceLifecycle.mayRequestVaultAccess(status)
            )
        }
    }

    @Test fun revocationCannotReturnToAuthorized() {
        listOf(
            DeviceStatus.REVOKE_PENDING,
            DeviceStatus.REVOKED,
            DeviceStatus.WIPE_CONFIRMED
        ).forEach { status ->
            assertFalse(DeviceLifecycle.acceptsVerifiedTransition(status, DeviceStatus.AUTHORIZED))
        }
    }

    @Test fun expectedTransitionsAreAccepted() {
        assertTrue(DeviceLifecycle.acceptsVerifiedTransition(
            DeviceStatus.PENDING, DeviceStatus.AUTHORIZED))
        assertTrue(DeviceLifecycle.acceptsVerifiedTransition(
            DeviceStatus.AUTHORIZED, DeviceStatus.REVOKE_PENDING))
        assertTrue(DeviceLifecycle.acceptsVerifiedTransition(
            DeviceStatus.REVOKE_PENDING, DeviceStatus.REVOKED))
        assertTrue(DeviceLifecycle.acceptsVerifiedTransition(
            DeviceStatus.REVOKED, DeviceStatus.WIPE_CONFIRMED))
    }

    @Test fun verifiedStatesCannotSkipRevocation() {
        assertFalse(DeviceLifecycle.acceptsVerifiedTransition(
            DeviceStatus.AUTHORIZED, DeviceStatus.WIPE_CONFIRMED))
        assertFalse(DeviceLifecycle.acceptsVerifiedTransition(
            DeviceStatus.AUTHORIZED, DeviceStatus.REVOKED))
        assertFalse(DeviceLifecycle.acceptsVerifiedTransition(
            DeviceStatus.PENDING, DeviceStatus.WIPE_CONFIRMED))
        DeviceStatus.entries.forEach {
            assertFalse(DeviceLifecycle.acceptsVerifiedTransition(it, it))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun shortDeviceIdsAreRejected() {
        DeviceRecord("1", "Móvil", DeviceStatus.AUTHORIZED, 1L, null)
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyAliasesAreRejected() {
        DeviceRecord("F54e28A916dA372b", "   ", DeviceStatus.PENDING, 1L, null)
    }

    @Test(expected = IllegalArgumentException::class)
    fun futureEnrollmentRelativeToLastSeenIsRejected() {
        DeviceRecord("F54e28A916dA372b", "Tablet", DeviceStatus.PENDING, 5L, 4L)
    }

    @Test fun aValidDeviceWithoutKnownLastCheckIsAccepted() {
        val record = DeviceRecord(
            "F54e28A916dA372b", "Mi Realme", DeviceStatus.PENDING, 1L, null
        )
        assertEquals(null, record.lastServerSeenAtMillis)
    }
}
