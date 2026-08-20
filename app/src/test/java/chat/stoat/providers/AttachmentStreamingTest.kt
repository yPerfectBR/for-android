package chat.stoat.providers

import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.OutputStream

class AttachmentStreamingTest {
    private class TrackingOutputStream : OutputStream() {
        private val delegate = ByteArrayOutputStream()

        var writes = 0
            private set

        var maxWriteSize = 0
            private set

        var flushed = false
            private set

        override fun write(value: Int) {
            delegate.write(value)
            writes += 1
            maxWriteSize = maxOf(maxWriteSize, 1)
        }

        override fun write(
            buffer: ByteArray,
            offset: Int,
            length: Int
        ) {
            delegate.write(buffer, offset, length)
            writes += 1
            maxWriteSize = maxOf(maxWriteSize, length)
        }

        override fun flush() {
            flushed = true
            delegate.flush()
        }

        fun bytes(): ByteArray =
            delegate.toByteArray()
    }

    @Test
    fun largeAttachmentIsCopiedInBoundedChunks() = runBlocking {
        val payload = ByteArray(5 * 1024 * 1024 + 123) {
            (it % 251).toByte()
        }

        val output = TrackingOutputStream()

        copyAttachmentChannel(
            ByteReadChannel(payload),
            output
        )

        assertArrayEquals(payload, output.bytes())
        assertTrue(output.writes > 1)
        assertTrue(output.maxWriteSize <= 64 * 1024)
        assertTrue(output.flushed)
    }

    @Test
    fun customBufferSizeIsRespected() = runBlocking {
        val payload = ByteArray(200_000) {
            (it % 127).toByte()
        }

        val output = TrackingOutputStream()

        copyAttachmentChannel(
            ByteReadChannel(payload),
            output,
            bufferSize = 4096
        )

        assertArrayEquals(payload, output.bytes())
        assertTrue(output.writes > 1)
        assertTrue(output.maxWriteSize <= 4096)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidBufferSize() = runBlocking {
        copyAttachmentChannel(
            ByteReadChannel(byteArrayOf(1, 2, 3)),
            TrackingOutputStream(),
            bufferSize = 0
        )
    }
}
