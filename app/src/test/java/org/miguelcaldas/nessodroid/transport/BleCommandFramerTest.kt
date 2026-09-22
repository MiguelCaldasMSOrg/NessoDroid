package org.miguelcaldas.nessodroid.transport

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BleCommandFramerTest {
    @Test
    fun shortCommandUsesOneFrame() {
        val frames = BleCommandFramer.frames("p")

        assertEquals(1, frames.size)
        assertArrayEquals("p".toByteArray(), frames.single())
    }

    @Test
    fun longCommandUsesFirmwareChunkProtocol() {
        val command = "transfer " + "x".repeat(700)
        val bytes = command.toByteArray()
        val frames = BleCommandFramer.frames(command)

        assertEquals("@begin:${bytes.size}", frames.first().toString(Charsets.UTF_8))
        assertEquals("@end", frames.last().toString(Charsets.UTF_8))
        assertTrue(frames.all { it.size <= 512 })
        val reconstructed = frames.drop(1).dropLast(1).flatMap { it.drop(6) }.toByteArray()
        assertArrayEquals(bytes, reconstructed)
    }

    @Test
    fun commandLimitCountsUtf8Bytes() {
        val command = "é".repeat(BleCommandFramer.MAX_COMMAND_BYTES / 2)

        assertEquals(BleCommandFramer.MAX_COMMAND_BYTES, command.toByteArray().size)
        assertTrue(BleCommandFramer.frames(command).size > 1)
    }
}