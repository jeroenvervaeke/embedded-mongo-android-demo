package io.github.jeroenvervaeke.coffeefinder.ui.console

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.jeroenvervaeke.coffeefinder.ui.theme.Console
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * The sheet the map hands over to: the count sitting on the bottom edge, and the documents behind
 * it a drag away.
 *
 * One gesture, both directions. Dragging or tapping the count opens the list over the map;
 * dragging it back down returns to the map, with the count where it was. What is under the peek
 * — the search box, the view switch, the rows — is off the bottom of the screen while it is shut,
 * which is the point: a minimised map shows the map, the count, and nothing else.
 *
 * The grip and the peek are what drags, and they are on screen in both states: with the list up,
 * the count is still at the top of the sheet, so the way back down is where the way up was. The
 * list scrolls on its own under them.
 */
@Composable
fun ResultSheet(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    peek: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val containerHeight = with(density) { maxHeight.toPx() }
        val openTop = containerHeight * MAP_KEPT_VISIBLE
        var peekHeight by remember { mutableIntStateOf(0) }

        // Shut is "everything but the peek, below the screen"; open is the sheet's own top.
        val travel = (containerHeight - openTop - peekHeight).coerceAtLeast(0f)
        val offset = remember { Animatable(travel) }
        val scope = rememberCoroutineScope()
        val settle by rememberUpdatedState(onExpandedChange)

        // Follows the state it is given -- a tap on the map shuts the sheet from outside it --
        // and re-lands after a rotation, where the travel it was holding no longer means anything.
        LaunchedEffect(expanded, travel) {
            offset.animateTo(if (expanded) 0f else travel, SETTLING)
        }

        val drag = Modifier.draggable(
            orientation = Orientation.Vertical,
            state = rememberDraggableState { delta ->
                scope.launch { offset.snapTo((offset.value + delta).coerceIn(0f, travel)) }
            },
            onDragStopped = { velocity ->
                val target = settleTarget(offset.value, travel, velocity)
                // The state is told first so that the effect above does not animate back.
                settle(target == 0f)
                offset.animateTo(target, SETTLING)
            },
        )

        Column(
            Modifier
                .offset { IntOffset(0, (openTop + offset.value).roundToInt()) }
                .height(maxHeight - with(density) { openTop.toDp() })
                .fillMaxWidth()
                .clip(SHEET_SHAPE)
                .background(Brush.verticalGradient(listOf(Console.PanelRaised, Console.Ink)))
                .border(1.dp, Console.Edge, SHEET_SHAPE),
        ) {
            Column(
                Modifier
                    .onSizeChanged { peekHeight = it.height }
                    .then(drag)
                    .clickable { onExpandedChange(!expanded) }
                    .semantics { contentDescription = PEEK_DESCRIPTION },
            ) {
                Grip()
                peek()
            }
            content()
        }
    }
}

/** What a test and a screen reader call the part of the sheet that is always on screen. */
const val PEEK_DESCRIPTION = "Result summary, drag to open the list"

@Composable
private fun Grip() {
    Box(Modifier.fillMaxWidth().padding(top = 9.dp, bottom = 3.dp), Alignment.Center) {
        Box(Modifier.size(width = 38.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(Console.Edge))
    }
}

/** How much of the map stays uncovered when the list is all the way up. */
private const val MAP_KEPT_VISIBLE = 0.16f

private val SHEET_SHAPE = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)

/** Firm enough to feel like a mechanism, and without the overshoot a bouncier one would add. */
private val SETTLING = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 420f)
