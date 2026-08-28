package io.github.jeroenvervaeke.coffeefinder.ui

import org.bson.Document
import org.bson.json.JsonMode
import org.bson.json.JsonWriterSettings

/**
 * The command, indented, exactly as it was sent.
 *
 * The BSON library's own writer rather than anything hand-rolled: what the pipeline screen is
 * claiming is that this is the document that crossed the bridge, and a pretty-printer that
 * paraphrased it would make that claim false.
 */
fun Document.pretty(): String = toJson(SETTINGS)

private val SETTINGS: JsonWriterSettings = JsonWriterSettings.builder()
    .outputMode(JsonMode.RELAXED)
    .indent(true)
    .build()
