package io.github.jeroenvervaeke.coffeefinder.data.explorer

import io.github.jeroenvervaeke.coffeefinder.data.model.Ireland
import io.github.jeroenvervaeke.coffeefinder.data.model.Metres
import io.github.jeroenvervaeke.coffeefinder.data.query.PlaceCriteria
import io.github.jeroenvervaeke.coffeefinder.data.query.nearestPipeline
import io.github.jeroenvervaeke.coffeefinder.data.query.searchPipeline
import io.github.jeroenvervaeke.coffeefinder.data.query.viewportPipeline
import org.bson.Document
import org.bson.json.JsonMode
import org.bson.json.JsonWriterSettings

/**
 * How a stage is printed for the editor.
 *
 * Above the presets that use it rather than at the foot of the file: these are top-level
 * properties, and one initialised after its reader is null when the reader runs.
 */
private val PRETTY: JsonWriterSettings = JsonWriterSettings.builder()
    .outputMode(JsonMode.RELAXED)
    .indent(true)
    .build()

/** A pipeline worth starting from, under the name the explorer lists it as. */
data class PipelinePreset(val name: String, val text: String)

/**
 * The pipelines the explorer opens with.
 *
 * The first three are built by the application's own query builders rather than written out
 * again here, so what a person starts editing is the pipeline the map and the list actually run
 * — down to the folded-in haversine constants. A preset that had been transcribed would be a
 * plausible copy, and drift the first time a builder changed.
 */
val PIPELINE_PRESETS: List<PipelinePreset> = listOf(
    PipelinePreset(
        name = "NEAREST",
        text = nearestPipeline(
            from = Ireland.DUBLIN,
            limit = 50,
            maxDistance = Metres(1_500.0),
        ).asPipelineText(),
    ),
    PipelinePreset(
        name = "VIEWPORT",
        text = viewportPipeline(Ireland.EXTENT, limit = 5_180).asPipelineText(),
    ),
    PipelinePreset(
        name = "TEXT + HAVERSINE",
        text = searchPipeline(
            text = "coffee",
            from = Ireland.DUBLIN,
            limit = 15,
        ).asPipelineText(),
    ),
    PipelinePreset(
        name = "BRANDED ONLY",
        text = nearestPipeline(
            from = Ireland.DUBLIN,
            limit = 20,
            maxDistance = Metres(5_000.0),
            criteria = PlaceCriteria(brandedOnly = true),
        ).asPipelineText(),
    ),
    PipelinePreset(
        name = "CATEGORY ROLLUP",
        text = listOf(
            Document(
                "\$group",
                Document("_id", "\$cat")
                    .append("n", Document("\$sum", 1))
                    .append("avgConfidence", Document("\$avg", "\$confidence")),
            ),
            Document("\$sort", Document("n", -1)),
        ).asPipelineText(),
    ),
    PipelinePreset(
        name = "BRANDS BY BRANCHES",
        text = listOf(
            Document("\$match", Document("brand", Document("\$exists", true))),
            Document("\$group", Document("_id", "\$brand").append("branches", Document("\$sum", 1))),
            Document("\$sort", Document("branches", -1)),
            Document("\$limit", 8),
        ).asPipelineText(),
    ),
)

/** Single stages to drop into whatever is being edited. */
val STAGE_SNIPPETS: List<PipelinePreset> = listOf(
    PipelinePreset("\$match", "{ \"\$match\": { \"cat\": \"cafe\" } }"),
    PipelinePreset("\$sort", "{ \"\$sort\": { \"confidence\": -1 } }"),
    PipelinePreset("\$limit", "{ \"\$limit\": 20 }"),
    PipelinePreset("\$project", "{ \"\$project\": { \"name\": 1, \"cat\": 1, \"confidence\": 1 } }"),
    PipelinePreset("\$count", "{ \"\$count\": \"places\" }"),
    PipelinePreset("\$group", "{ \"\$group\": { \"_id\": \"\$addr.locality\", \"n\": { \"\$sum\": 1 } } }"),
    PipelinePreset("\$sample", "{ \"\$sample\": { \"size\": 5 } }"),
)

/**
 * These stages as the array a person edits.
 *
 * Indented by the BSON library's own writer, so the text in the editor parses back into the same
 * documents it was printed from.
 */
fun List<Document>.asPipelineText(): String =
    joinToString(separator = ",\n", prefix = "[\n", postfix = "\n]") { stage ->
        stage.toJson(PRETTY).prependIndent("  ")
    }
