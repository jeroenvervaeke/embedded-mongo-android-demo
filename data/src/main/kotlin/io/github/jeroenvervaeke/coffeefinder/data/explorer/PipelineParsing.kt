package io.github.jeroenvervaeke.coffeefinder.data.explorer

import org.bson.Document
import org.bson.json.JsonParseException

/** Text that was meant to be a pipeline and is not, with the reason a person can act on. */
class PipelineFormatException(message: String) : IllegalArgumentException(message)

/**
 * The stages [text] describes, ready to hand to the engine.
 *
 * The explorer screen lets a person type a pipeline, so this is the boundary where typed text
 * becomes documents: everything past it is a `List<Document>` the engine can run, and everything
 * that is not gets turned around here with a message rather than deeper with a cast.
 *
 * Both shapes are accepted, because both are what people paste: the array a pipeline is, and a
 * single stage on its own.
 *
 * The BSON library's own parser does the reading, so `$` keys, extended JSON and the relaxed
 * numbers `mongosh` prints all behave the way they do in a shell rather than the way a
 * hand-rolled reader would guess.
 */
fun parsePipeline(text: String): List<Document> {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) throw PipelineFormatException("the pipeline is empty")

    val stages = when (trimmed.first()) {
        '[' -> readStages(trimmed)
        '{' -> listOf(readStage(trimmed))
        else -> throw PipelineFormatException(
            "a pipeline is an array of stages, so it starts with `[`, not " +
                "`${trimmed.first()}`",
        )
    }
    if (stages.isEmpty()) throw PipelineFormatException("a pipeline with no stages returns nothing")
    return stages
}

/**
 * The array's elements, read by parsing it as the value of a field.
 *
 * `Document.parse` reads an object, and a pipeline is an array; wrapping it is what lets the same
 * parser read both, rather than a second reader with its own idea of what a number is.
 */
private fun readStages(array: String): List<Document> {
    val wrapped = parse("{ \"$FIELD\": $array }")
    val elements = wrapped[FIELD] as? List<*>
        ?: throw PipelineFormatException("the pipeline is not an array of stages")
    return elements.mapIndexed { index, element ->
        element as? Document
            ?: throw PipelineFormatException("stage ${index + 1} is not an object: $element")
    }
}

private fun readStage(stage: String): Document = parse(stage)

private fun parse(json: String): Document = try {
    Document.parse(json)
} catch (invalid: JsonParseException) {
    // The parser's own message names the offset it stopped at, which is the useful half of it.
    throw PipelineFormatException(invalid.message ?: "the pipeline is not valid JSON")
} catch (invalid: org.bson.BsonInvalidOperationException) {
    throw PipelineFormatException(invalid.message ?: "the pipeline is not valid JSON")
}

private const val FIELD = "stages"
