package io.github.jeroenvervaeke.coffeefinder.data.seed

import java.io.EOFException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.bson.BsonBinaryReader
import org.bson.Document
import org.bson.codecs.DecoderContext
import org.bson.codecs.DocumentCodec

/**
 * Reads a stream of concatenated BSON documents — the shape `mongodump` writes and the shape the
 * seed ships in — one document at a time.
 *
 * There is no container around them, no count and no index: a BSON document begins with its own
 * length, so the stream is walked by reading four bytes and then that many. Lazily, because the
 * point of streaming the seed is that all 5,180 documents are never in memory at once.
 *
 * The sequence can be iterated once and only while [input] is open.
 */
fun bsonDocuments(input: InputStream): Sequence<Document> {
    val buffered = input.buffered()
    return generateSequence { buffered.nextDocument() }
}

/** Returns the next document, or `null` at a clean end of stream. */
private fun InputStream.nextDocument(): Document? {
    val document = ByteArray(Int.SIZE_BYTES)
    // Nothing left is the end of the stream; one to three bytes left is a truncated file, and
    // reading them as a length would produce a plausible-looking number out of padding.
    when (fill(document, 0, document.size)) {
        0 -> return null
        document.size -> Unit
        else -> throw EOFException("the seed ends inside a document's length prefix")
    }

    val length = ByteBuffer.wrap(document).order(ByteOrder.LITTLE_ENDIAN).int
    if (length !in MINIMUM_DOCUMENT_BYTES..MAXIMUM_DOCUMENT_BYTES) {
        throw EOFException("a BSON document declaring $length bytes is not one this seed can hold")
    }

    val whole = document.copyOf(length)
    val body = length - document.size
    if (fill(whole, document.size, body) != body) {
        throw EOFException("the seed ends inside a document that declared $length bytes")
    }
    return decode(whole)
}

/**
 * Reads until [length] bytes have arrived or the stream ends, returning how many arrived.
 *
 * Hand-rolled rather than `InputStream.readNBytes`, which looks like the obvious call and is
 * **API 33**: this application's floor is 28, so on every device between Android 9 and 12L that
 * method is a `NoSuchMethodError` at the first document of the seed. Nothing catches it here —
 * this module is plain Kotlin, so lint never checks it for platform APIs, and its tests run on a
 * JVM where the method exists. `read` has been there since API 1.
 *
 * A single `read` is allowed to return fewer bytes than asked for, which is why this loops: on a
 * `GZIPInputStream` a short read is normal rather than exceptional.
 */
private fun InputStream.fill(target: ByteArray, offset: Int, length: Int): Int {
    var filled = 0
    while (filled < length) {
        val read = read(target, offset + filled, length - filled)
        if (read < 0) break
        filled += read
    }
    return filled
}

private fun decode(bytes: ByteArray): Document =
    BsonBinaryReader(ByteBuffer.wrap(bytes)).use { reader -> CODEC.decode(reader, CONTEXT) }

/** An empty document: its own four length bytes and the terminator. */
private const val MINIMUM_DOCUMENT_BYTES = 5

/** MongoDB's own document limit. A length beyond it means a corrupt stream, not a large place. */
private const val MAXIMUM_DOCUMENT_BYTES = 16 * 1024 * 1024

private val CODEC = DocumentCodec()
private val CONTEXT = DecoderContext.builder().build()
