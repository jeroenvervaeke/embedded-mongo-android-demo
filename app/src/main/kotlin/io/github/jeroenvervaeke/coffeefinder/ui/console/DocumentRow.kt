package io.github.jeroenvervaeke.coffeefinder.ui.console

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.jeroenvervaeke.coffeefinder.data.model.NearbyPlace
import io.github.jeroenvervaeke.coffeefinder.data.parse.asDocument
import io.github.jeroenvervaeke.coffeefinder.ui.pretty
import io.github.jeroenvervaeke.coffeefinder.ui.theme.Console
import io.github.jeroenvervaeke.coffeefinder.ui.theme.MONO_CODE

/**
 * The same result as the document it is: `_id`, `cat`, the GeoJSON point, and the distance the
 * pipeline measured.
 *
 * Written back out by [asDocument], which a round-trip test holds to the shape the seed stored —
 * so this is the document, not an illustration of one.
 */
@Composable
fun DocumentRow(
    nearby: NearbyPlace,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val source = remember(nearby) { highlightJson(nearby.asDocument().pretty()) }

    Text(
        text = source,
        style = MONO_CODE,
        color = Console.Mint,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(SHAPE)
            .background(if (selected) Console.Spring.copy(alpha = 0.06f) else Console.Ink.copy(alpha = 0.5f))
            .border(1.dp, if (selected) Console.Spring.copy(alpha = 0.4f) else Console.Line, SHAPE)
            .clickable(onClick = onSelect)
            // A document is nested deeply enough that wrapping it would cost more than scrolling.
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 11.dp, vertical = 9.dp),
    )
}

private val SHAPE = RoundedCornerShape(11.dp)
