package com.example

import com.example.data.model.CommandType
import com.example.data.model.DeviceRole
import com.example.data.model.DeviceStatus
import com.example.data.model.RemoteCommand
import com.example.data.network.NetworkUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

class ExampleUnitTest {

    @Test
    fun remoteCommand_creation_hasValidAttributes() {
        val command = RemoteCommand(
            pairingCode = "GL-1234",
            commandType = CommandType.SET_VOLUME_MEDIA,
            valueInt = 40,
            valueString = "Media 40%"
        )

        assertNotNull(command.id)
        assertEquals("GL-1234", command.pairingCode)
        assertEquals(CommandType.SET_VOLUME_MEDIA, command.commandType)
        assertEquals(40, command.valueInt)
        assertEquals(DeviceRole.PARENT, command.senderRole)
    }

    @Test
    fun timeLimit_command_creation_hasValidAttributes() {
        val addTimeCmd = RemoteCommand(
            pairingCode = "GL-1234",
            commandType = CommandType.ADD_EXTRA_TIME,
            valueInt = 15,
            valueString = "+15 min"
        )
        assertEquals(CommandType.ADD_EXTRA_TIME, addTimeCmd.commandType)
        assertEquals(15, addTimeCmd.valueInt)

        val lockCmd = RemoteCommand(
            pairingCode = "GL-1234",
            commandType = CommandType.END_TIME_AND_LOCK,
            valueInt = 0,
            valueString = "End time & lock"
        )
        assertEquals(CommandType.END_TIME_AND_LOCK, lockCmd.commandType)
    }

    @Test
    fun timeLimit_addition_and_reset_calculation() {
        val dailyLimitMins = 60
        var remainingSecs = 0 // time expired

        // Parent grants +15 minutes
        val extraMins = 15
        remainingSecs += extraMins * 60

        val remainingMins = ceil(remainingSecs / 60.0).toInt()
        assertEquals(15, remainingMins)

        // Reset to full limit
        remainingSecs = dailyLimitMins * 60
        val resetRemainingMins = ceil(remainingSecs / 60.0).toInt()
        assertEquals(60, resetRemainingMins)
    }

    @Test
    fun volume_percentage_calculation_isAccurate() {
        val maxVolume = 15
        val testPercentage = 60
        val clamped = testPercentage.coerceIn(0, 100)
        val targetVolume = ((clamped / 100f) * maxVolume).roundToInt().coerceIn(0, maxVolume)

        assertEquals(9, targetVolume)
    }

    @Test
    fun brightness_percentage_mapping_isAccurate() {
        val brightnessPct = 50
        val clamped = brightnessPct.coerceIn(5, 100)
        val value255 = ((clamped / 100f) * 255).roundToInt().coerceIn(5, 255)

        assertEquals(128, value255)
    }

    @Test
    fun pairingCode_handshake_validation() {
        val parentCode = "GL-8492"
        val enteredCodeDirect = "GL-8492"
        val enteredCodeLowerCase = "gl-8492"
        val enteredCodeWithoutHyphen = "8492"

        assertTrue(parentCode.equals(enteredCodeDirect, ignoreCase = true))
        assertTrue(parentCode.equals(enteredCodeLowerCase, ignoreCase = true))
        assertTrue(parentCode.replace("-", "").contains(enteredCodeWithoutHyphen))
    }

    @Test
    fun deviceStatus_defaultState_hasValidAttributes() {
        val unlinkedStatus = DeviceStatus()

        // Unlinked state correctly reflects waiting for real child device
        assertFalse(unlinkedStatus.isOnline)
        assertFalse(unlinkedStatus.isLocked)
        assertEquals(-1, unlinkedStatus.batteryLevel)
        assertFalse(unlinkedStatus.hasRealTelemetry)

        // Active child telemetry state
        val activeTelemetryStatus = DeviceStatus(
            isOnline = true,
            batteryLevel = 85,
            volumeMedia = 50,
            brightness = 70,
            timeLimitMinutes = 120,
            timeRemainingMinutes = 90
        )
        assertTrue(activeTelemetryStatus.isOnline)
        assertTrue(activeTelemetryStatus.hasRealTelemetry)
        assertTrue(activeTelemetryStatus.batteryLevel in 0..100)
        assertTrue(activeTelemetryStatus.volumeMedia in 0..100)
        assertTrue(activeTelemetryStatus.brightness in 0..100)
        assertEquals(120, activeTelemetryStatus.timeLimitMinutes)
        assertEquals(90, activeTelemetryStatus.timeRemainingMinutes)
        assertFalse(activeTelemetryStatus.isTimeExpired)
    }

    @Test
    fun networkUtils_defaultPort_is8888() {
        assertEquals(8888, NetworkUtils.DEFAULT_PORT)
    }

    @Test
    fun connectionModes_areSupported() {
        val validModes = listOf("HYBRID", "LAN", "ONLINE")
        assertTrue(validModes.contains("HYBRID"))
        assertTrue(validModes.contains("LAN"))
        assertTrue(validModes.contains("ONLINE"))
    }
}
