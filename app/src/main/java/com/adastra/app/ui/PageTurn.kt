package com.adastra.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer

/**
 * The page on top, swinging around the spine (its left edge) like a notebook page.
 * [turned] runs from 0 (lying flat) to 1 (turned away, edge-on).
 */
fun Modifier.turningPage(turned: () -> Float): Modifier = graphicsLayer {
    transformOrigin = TransformOrigin(0f, 0.5f)
    rotationY = -90f * turned()
    cameraDistance = 24f * density
}

/** The page underneath, in shadow until the page above is turned away. [revealed] runs 0..1. */
fun Modifier.pageBeneath(revealed: () -> Float): Modifier = graphicsLayer {
    alpha = 0.35f + 0.65f * revealed()
}

/**
 * Shows [target] and turns a page whenever it changes: forward (to a later page) lifts the
 * current page away; backward lays the earlier page back down on top.
 */
@Composable
fun <T> PageTurner(target: T, order: (T) -> Int, content: @Composable (T) -> Unit) {
    var shown by remember { mutableStateOf(target) }
    var from by remember { mutableStateOf<T?>(null) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(target) {
        if (target != shown) {
            from = shown
            shown = target
            progress.snapTo(0f)
            progress.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
            from = null
        }
    }

    val old = from
    val forward = old != null && order(shown) > order(old)
    // Bottom layer first. Keys keep each page's state while it changes layers.
    val layers: List<T> = when {
        old == null || old == shown -> listOf(shown)
        forward -> listOf(shown, old)
        else -> listOf(old, shown)
    }
    val turned = { if (forward) progress.value else 1f - progress.value }

    Box(Modifier.fillMaxSize()) {
        layers.forEachIndexed { i, page ->
            key(page) {
                val role = when {
                    layers.size == 1 -> Modifier
                    i == 0 -> Modifier.pageBeneath { if (forward) progress.value else 1f - progress.value }
                    else -> Modifier.turningPage(turned)
                }
                Box(Modifier.fillMaxSize().then(role).background(Astra.Bg)) { content(page) }
            }
        }
    }
}
