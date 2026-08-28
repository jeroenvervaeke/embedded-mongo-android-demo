package io.github.jeroenvervaeke.coffeefinder.data.parse

/**
 * A reply that did not hold the place this application stored.
 *
 * Its own type rather than an `IllegalStateException`: it means the documents and the code that
 * reads them have drifted apart — a seed built to a different shape, or a pipeline projecting
 * away a field it still parses — which is a different problem from a query the engine refused.
 */
class PlaceFormatException(message: String) : Exception(message)
