package io.github.jeroenvervaeke.coffeefinder.data.query

import io.github.jeroenvervaeke.embeddedmongodb.MongoCollection
import io.github.jeroenvervaeke.embeddedmongodb.MongoDatabase

/** The one database this application has. */
const val COFFEE_DATABASE = "coffee"

const val PLACES_COLLECTION = "places"

/** Holds the one document recording that a seed finished. */
const val SEED_COLLECTION = "seed"

/**
 * The two collections, named here rather than at every call site.
 *
 * A `MongoCollection` is a name and a way to run commands, so making one costs nothing and these
 * are functions rather than something cached.
 */
fun MongoDatabase.places(): MongoCollection = collection(PLACES_COLLECTION)

fun MongoDatabase.seedMarkers(): MongoCollection = collection(SEED_COLLECTION)

/**
 * The field a geo query writes its distance into. `$geoNear` names it in `distanceField`, and the
 * text search computes the same field itself, so one parser reads both.
 */
const val DISTANCE_FIELD = "distance"

/**
 * How far MongoDB thinks it is to the other side of the planet.
 *
 * `$geoNear` measures GeoJSON distances on a sphere of this radius, so the text search's own
 * distance expression uses it too: two numbers labelled "metres" that came from different spheres
 * would sort against each other subtly wrongly.
 */
const val EARTH_RADIUS_METRES = 6378100.0
