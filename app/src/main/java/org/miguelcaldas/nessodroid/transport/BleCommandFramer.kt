package org.miguelcaldas.nessodroid.transport

object BleCommandFramer {
    const val MAX_COMMAND_BYTES = 1232
    private const val MAX_ATTRIBUTE_BYTES = 512
    private const val MAX_CHUNK_BYTES = 506
    private val dataPrefix = "@data:".toByteArray(Charsets.UTF_8)

    fun frames(command: String): List<ByteArray> {
        val commandBytes = command.toByteArray(Charsets.UTF_8)
        require(commandBytes.isNotEmpty()) { "Command cannot be empty" }
        require(commandBytes.size <= MAX_COMMAND_BYTES) { "Command exceeds $MAX_COMMAND_BYTES UTF-8 bytes" }
        if (commandBytes.size <= MAX_ATTRIBUTE_BYTES) {
            return listOf(commandBytes)
        }

        val frames = mutableListOf("@begin:${commandBytes.size}".toByteArray(Charsets.UTF_8))
        commandBytes.asList().chunked(MAX_CHUNK_BYTES).forEach { chunk ->
            frames += dataPrefix + chunk.toByteArray()
        }
        frames += "@end".toByteArray(Charsets.UTF_8)
        return frames
    }
}