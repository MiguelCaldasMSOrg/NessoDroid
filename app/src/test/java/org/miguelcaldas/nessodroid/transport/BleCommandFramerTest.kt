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

    @Test
    fun maximumAttributeUsesOneFrame() {
        val command = "x".repeat(512)

        assertArrayEquals(command.toByteArray(), BleCommandFramer.frames(command).single())
    }

    @Test
    fun commandAboveAttributeLimitUsesChunkProtocol() {
        val frames = BleCommandFramer.frames("x".repeat(513))

        assertEquals("@begin:513", frames.first().toString(Charsets.UTF_8))
        assertEquals(listOf(10, 512, 13, 4), frames.map { it.size })
        assertEquals("@end", frames.last().toString(Charsets.UTF_8))
    }

    @Test
    fun chunkBoundaryPreservesMultibyteCharacters() {
        val bytes = "\u20ac".repeat(400).toByteArray(Charsets.UTF_8)
        val frames = BleCommandFramer.frames(bytes.toString(Charsets.UTF_8))

        assertTrue(frames.all { it.size <= 512 })
        val reconstructed = frames.drop(1).dropLast(1).flatMap { it.drop(6) }.toByteArray()
        assertArrayEquals(bytes, reconstructed)
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyCommandIsRejected() {
        BleCommandFramer.frames("")
    }

    @Test(expected = IllegalArgumentException::class)
    fun commandAboveByteLimitIsRejected() {
        BleCommandFramer.frames("x".repeat(BleCommandFramer.MAX_COMMAND_BYTES + 1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun multibyteCommandAboveByteLimitIsRejected() {
        BleCommandFramer.frames("\u00e9".repeat(BleCommandFramer.MAX_COMMAND_BYTES / 2 + 1))
    }

    @Test
    fun framesFitNegotiatedAttributeSize() {
        val command = "x".repeat(BleCommandFramer.MAX_COMMAND_BYTES)
        for (attributeSize in listOf(20, 97, 244, 512)) {
            val frames = BleCommandFramer.frames(command, attributeSize)

            assertTrue(frames.all { it.size <= attributeSize })
            val reconstructed = frames.drop(1).dropLast(1).flatMap { it.drop(6) }.toByteArray()
            assertArrayEquals(command.toByteArray(), reconstructed)
            assertEquals(1, BleCommandFramer.frames("x".repeat(attributeSize), attributeSize).size)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun attributeSizeBelowMinimumMtuIsRejected() {
        BleCommandFramer.frames("p", 19)
    }

    @Test(expected = IllegalArgumentException::class)
    fun attributeSizeAboveGattLimitIsRejected() {
        BleCommandFramer.frames("p", 513)
    }
}