package io.github.jeroenvervaeke.coffeefinder.data.parse

import io.github.jeroenvervaeke.coffeefinder.data.model.Address
import io.github.jeroenvervaeke.coffeefinder.data.model.NearbyPlace
import io.github.jeroenvervaeke.coffeefinder.data.model.Place
import io.github.jeroenvervaeke.coffeefinder.data.query.DISTANCE_FIELD
import org.bson.Document

/**
 * A place written back out as the document it was read from.
 *
 * The console screen can show a result as the row a person reads or as the document the engine
 * stored, and this is the second of those. It is the exact inverse of [toPlace] — the fields, the
 * names and the shapes the seed writes — which is a claim a round-trip test holds this to rather
 * than one this comment makes.
 */
fun Place.asDocument(): Document = Document("_id", id.value)
    .append("name", name)
    .append("cat", category.stored)
    .also { document -> brand?.let { document.append("brand", it) } }
    .append("confidence", confidence.value)
    .also { document -> address?.let { document.append("addr", it.asDocument()) } }
    .append(
        "loc",
        Document("type", "Point")
            .append("coordinates", listOf(coordinates.longitude, coordinates.latitude)),
    )

/** The same, with the distance the query measured — which is a field the reply carried too. */
fun NearbyPlace.asDocument(): Document = place.asDocument().append(DISTANCE_FIELD, distance.value)

/** Only the fields Overture gave: an address of four nulls is not what was stored. */
private fun Address.asDocument(): Document = Document().also { document ->
    street?.let { document.append("street", it) }
    locality?.let { document.append("locality", it) }
    postcode?.let { document.append("postcode", it) }
    region?.let { document.append("region", it) }
}
