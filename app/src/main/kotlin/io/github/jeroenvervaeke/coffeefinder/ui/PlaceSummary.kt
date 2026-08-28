package io.github.jeroenvervaeke.coffeefinder.ui

import io.github.jeroenvervaeke.coffeefinder.data.model.Place

/**
 * The second line of a result: the chain and the address when Overture gave them, and the
 * category when it gave neither.
 *
 * Overture leaves both optional and often supplies neither, so this is the one piece of the
 * screens with a decision in it — which is why it is a function rather than an expression buried
 * in a composable.
 */
fun Place.summary(): String =
    listOfNotNull(brand, address?.oneLine()?.ifEmpty { null })
        .joinToString(SEPARATOR)
        .ifEmpty { category.label }

private const val SEPARATOR = " · "
