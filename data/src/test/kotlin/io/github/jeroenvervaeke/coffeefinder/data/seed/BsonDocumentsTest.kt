package io.github.jeroenvervaeke.coffeefinder.data.seed

import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.bson.Document
import org.bson.codecs.DocumentCodec
import org.bson.codecs.EncoderContext
import org.bson.BsonBinaryWriter
import org.bson.io.BasicOutputBuffer

class BsonDocumentsTest {
    @Test
    fun `every document in the stream is read, in order`() {
        val written = listOf(Document("n", 1), Document("n", 2), Document("n", 3))

        assertEquals(written, bsonDocuments(stream(written)).toList())
    }

    @Test
    fun `an empty stream holds no documents rather than failing`() {
        assertEquals(emptyList(), bsonDocuments(ByteArrayInputStream(ByteArray(0))).toList())
    }

    @Test
    fun `documents are read lazily, so the whole seed is never in memory`() {
        val read = bsonDocuments(stream(List(100) { Document("n", it) })).take(2).toList()

        assertEquals(listOf(Document("n", 0), Document("n", 1)), read)
    }

    @Test
    fun `a stream that gives up one byte at a time is still read whole`() {
        // What a short read looks like. `read` is allowed to return fewer bytes than asked for,
        // and on an inflating stream it routinely does, so the reader has to loop rather than
        // treat one call as one document.
        val written = listOf(Document("n", 1), Document("n", 2))
        val bytes = encoded(written)
        val trickle = object : InputStream() {
            private var at = 0

            override fun read(): Int = if (at < bytes.size) bytes[at++].toInt() and 0xFF else -1

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (at >= bytes.size) return -1
                b[off] = bytes[at++]
                return 1
            }
        }

        assertEquals(written, bsonDocuments(trickle).toList())
    }

    @Test
    fun `a stream that ends inside a document is reported rather than truncated silently`() {
        val whole = encoded(listOf(Document("n", 1), Document("n", 2)))
        val cut = whole.copyOf(whole.size - 3)

        assertFailsWith<EOFException> { bsonDocuments(ByteArrayInputStream(cut)).toList() }
    }

    @Test
    fun `a stream that ends inside a length prefix is reported rather than read as padding`() {
        val whole = encoded(listOf(Document("n", 1)))
        val cut = whole + byteArrayOf(1, 0)

        assertFailsWith<EOFException> { bsonDocuments(ByteArrayInputStream(cut)).toList() }
    }

    @Test
    fun `a length no document could have is refused rather than allocated`() {
        val bogus = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(1 shl 30).array()

        assertFailsWith<EOFException> { bsonDocuments(ByteArrayInputStream(bogus)).toList() }
    }

    @Test
    fun `a length below an empty document is refused`() {
        val bogus = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(2).array()

        assertFailsWith<EOFException> { bsonDocuments(ByteArrayInputStream(bogus)).toList() }
    }
}

internal fun stream(documents: List<Document>) = ByteArrayInputStream(encoded(documents))

/** The concatenated BSON the seed ships as: no header, no count, no separators. */
internal fun encoded(documents: List<Document>): ByteArray {
    val codec = DocumentCodec()
    val context = EncoderContext.builder().build()
    return documents.fold(ByteArray(0)) { bytes, document ->
        BasicOutputBuffer().use { buffer ->
            BsonBinaryWriter(buffer).use { writer -> codec.encode(writer, document, context) }
            bytes + buffer.toByteArray()
        }
    }
}
