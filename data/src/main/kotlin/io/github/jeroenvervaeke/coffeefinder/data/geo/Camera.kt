package io.github.jeroenvervaeke.coffeefinder.data.geo

import io.github.jeroenvervaeke.coffeefinder.data.model.Coordinates
import io.github.jeroenvervaeke.coffeefinder.data.model.Ireland
import io.github.jeroenvervaeke.coffeefinder.data.model.Metres
import io.github.jeroenvervaeke.coffeefinder.data.model.Viewport
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/**
 * Where the map is looking: a centre, and how many degrees of latitude fit top to bottom.
 *
 * Latitude rather than longitude because a degree of latitude is the same distance everywhere,
 * while a degree of longitude narrows towards the poles. Deriving the [Viewport] from the canvas
 * shape rather than fitting a fixed box into it is what keeps Ireland the right shape on a phone
 * and on a tablet, and it means the polygon sent to `$geoWithin` is exactly what is on screen:
 * no letterboxed margin full of places that were queried and not drawn.
 */
data class Camera(val centre: Coordinates, val latitudeSpan: Double) {
    init {
        require(latitudeSpan in MINIMUM_SPAN..MAXIMUM_SPAN) {
            "a camera showing $latitudeSpan degrees of latitude is outside $MINIMUM_SPAN..$MAXIMUM_SPAN"
        }
    }

    /**
     * What is on screen, for a canvas [aspectRatio] wide units per tall unit.
     *
     * Longitude is divided by the cosine of the centre's latitude, which is how much narrower a
     * degree of longitude is there: without it Ireland is drawn 40% too wide.
     */
    fun viewport(aspectRatio: Double): Viewport {
        require(aspectRatio > 0) { "a canvas cannot be $aspectRatio wide per unit tall" }
        val halfHeight = latitudeSpan / 2
        val halfWidth = halfHeight * aspectRatio / cos(Math.toRadians(centre.latitude))
        return Viewport(
            west = clampLongitude(centre.longitude - halfWidth),
            south = clampLatitude(centre.latitude - halfHeight),
            east = clampLongitude(centre.longitude + halfWidth),
            north = clampLatitude(centre.latitude + halfHeight),
        )
    }

    /** Zoomed in by [factor], greater than one to see less of the world, and clamped either way. */
    fun zoomedBy(factor: Double): Camera {
        require(factor > 0 && factor.isFinite()) { "a zoom factor of $factor is not a scale" }
        return copy(latitudeSpan = (latitudeSpan / factor).coerceIn(MINIMUM_SPAN, MAXIMUM_SPAN))
    }

    /**
     * Moved by a fraction of what is on screen: `panned(0.5, 0.0)` scrolls half a screen east.
     *
     * The vertical fraction is of [latitudeSpan] and the horizontal one of the longitude span at
     * this latitude, so a drag of the same number of pixels moves the map the same distance in
     * both directions.
     */
    fun panned(eastFraction: Double, northFraction: Double, aspectRatio: Double): Camera {
        val moved = viewport(aspectRatio)
        return copy(
            centre = Coordinates(
                longitude = clampLongitude(centre.longitude + moved.widthDegrees * eastFraction),
                latitude = clampLatitude(centre.latitude + moved.heightDegrees * northFraction),
            ),
        )
    }

    companion object {
        /**
         * The camera that holds all of [viewport] on a canvas of this shape.
         *
         * A tall island on a tall screen still does not fit sideways: Ireland is 5.5 degrees of
         * longitude across, which at this latitude is less ground than 4.2 degrees of latitude,
         * but on a portrait phone the box that is 4.2 degrees tall is only 2.3 wide. So the span
         * is whichever of the two dimensions needs the most room, and the other gets margin.
         */
        fun covering(viewport: Viewport, aspectRatio: Double): Camera {
            require(aspectRatio > 0) { "a canvas cannot be $aspectRatio wide per unit tall" }
            val centre = viewport.centre
            val widthAsLatitude = viewport.widthDegrees * cos(Math.toRadians(centre.latitude))
            val span = max(viewport.heightDegrees, widthAsLatitude / aspectRatio) * MARGIN
            return Camera(centre, span.coerceIn(MINIMUM_SPAN, MAXIMUM_SPAN))
        }

        /**
         * A camera centred on [centre] with a circle of [radius] framed inside it.
         *
         * What the console screen opens on, because its headline is a count inside a radius and a
         * ring drawn two pixels across says nothing. [margin] is how many radii fit top to bottom:
         * three leaves the ring filling the middle third of a portrait screen with the ground it
         * sits in around it.
         *
         * Latitude only, since that is what a camera spans; a degree of it is the same distance
         * everywhere, which is the whole reason the camera is expressed in it.
         */
        fun around(centre: Coordinates, radius: Metres, margin: Double = RADII_ON_SCREEN): Camera {
            require(margin > 0) { "a margin of $margin radii frames nothing" }
            val span = radius.value * margin / METRES_PER_DEGREE_LATITUDE
            return Camera(centre, span.coerceIn(MINIMUM_SPAN, MAXIMUM_SPAN))
        }

        /**
         * The whole island, framed for a portrait phone, so the first thing drawn is every
         * document in the database. Reframed as soon as the canvas reports its real shape.
         */
        val IRELAND = covering(Ireland.EXTENT, PORTRAIT_ASPECT_RATIO)

        /** A portrait phone, which is what to assume until a canvas has been measured. */
        const val PORTRAIT_ASPECT_RATIO = 0.55

        /** Enough slack that the coast is inside the screen rather than flush against it. */
        private const val MARGIN = 1.06

        /** About 400 m top to bottom: a street, and the closest a phone screen usefully gets. */
        const val MINIMUM_SPAN = 0.0035

        /** Wider than the seed's extent, so zooming out stops at "the island, with room around it". */
        const val MAXIMUM_SPAN = 12.0

        /** How many radii fit top to bottom in [around]: the ring, and ground around it. */
        private const val RADII_ON_SCREEN = 3.0

        /**
         * One degree of latitude, in metres.
         *
         * The WGS84 mean, which is what [around] needs: it is sizing a screen, not measuring a
         * distance the engine already measures on its own sphere.
         */
        private const val METRES_PER_DEGREE_LATITUDE = 110_574.0
    }
}

/**
 * Kept far enough from the poles that the cosine correction stays a number worth dividing by, and
 * far enough inside the world that a viewport built around it is still a valid rectangle.
 */
private fun clampLatitude(latitude: Double) = min(max(latitude, -MAXIMUM_LATITUDE), MAXIMUM_LATITUDE)

/**
 * Longitude is clamped rather than wrapped. Wrapping would put the antimeridian inside a viewport,
 * and a polygon that crosses it is one MongoDB reads as going the long way round the planet.
 */
private fun clampLongitude(longitude: Double) =
    min(max(longitude, -MAXIMUM_LONGITUDE), MAXIMUM_LONGITUDE)

private const val MAXIMUM_LATITUDE = 85.0

private const val MAXIMUM_LONGITUDE = 179.0

/**
 * Whether two cameras show the same map closely enough that re-querying would be wasted.
 *
 * Used as `distinctUntilChanged`'s test, which is contractually an equivalence relation, so
 * everything here is symmetric in the two cameras: the tolerance is scaled by the larger span,
 * not by the receiver's, and the longitude tolerance is converted at the latitude between them.
 * Scaling by the receiver alone made `a.showsSameAs(b)` and `b.showsSameAs(a)` disagree whenever
 * the spans differed, which is every pinch.
 */
internal fun Camera.showsSameAs(other: Camera): Boolean {
    val tolerance = max(latitudeSpan, other.latitudeSpan) * SETTLED_FRACTION
    // A degree of longitude covers cos(latitude) of what a degree of latitude does, so the same
    // distance on screen is that many more degrees of it.
    val betweenLatitudes = (centre.latitude + other.centre.latitude) / 2
    val longitudeTolerance = tolerance / cos(Math.toRadians(clampLatitude(betweenLatitudes)))

    return abs(latitudeSpan - other.latitudeSpan) < tolerance &&
        abs(centre.latitude - other.centre.latitude) < tolerance &&
        abs(centre.longitude - other.centre.longitude) < longitudeTolerance
}

private const val SETTLED_FRACTION = 0.001
