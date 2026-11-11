package es.robertodorado.robguard.devices

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DeviceChallengeTranscriptTest {
    private val deviceId = "installation_AAAA1111"
    private val challenge = ByteArray(32) { it.toByte() }

    @Test fun sameInputProducesIdenticalTranscript() {
        assertArrayEquals(
            DeviceChallengeTranscript.make(deviceId, challenge),
            DeviceChallengeTranscript.make(deviceId, challenge)
        )
    }

    @Test fun otherDeviceHasDistinctTranscript() {
        assertFalse(DeviceChallengeTranscript.make(deviceId, challenge).contentEquals(
            DeviceChallengeTranscript.make("installation_BBBB2222", challenge)
        ))
    }

    @Test fun otherNonceHasDistinctTranscript() {
        assertFalse(DeviceChallengeTranscript.make(deviceId, challenge).contentEquals(
            DeviceChallengeTranscript.make(deviceId, ByteArray(32) { 8 })
        ))
    }

    @Test(expected = IllegalArgumentException::class)
    fun shortChallengeRejected() {
        DeviceChallengeTranscript.make(deviceId, byteArrayOf(1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun overlongChallengeRejected() {
        DeviceChallengeTranscript.make(deviceId, ByteArray(513))
    }

    @Test(expected = IllegalArgumentException::class)
    fun malformedIdRejected() {
        DeviceChallengeTranscript.make("../another", challenge)
    }
}
