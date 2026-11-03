package com.orangefox.tensor.manager.ui

import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CanvasHolder
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.lang.reflect.Method

/*
 * Единый общий RenderNode блюра на весь экран: RenderEffect применяется
 * ровно 1 раз за кадр внутри recordLayer, а оверлеи (шапка, нижний бар,
 * кнопка перехода) рисуют готовый размытый узел как маску со сдвигом.
 */

@Stable
class GraphicsLayerRecordingState(
    val graphicsLayer: androidx.compose.ui.graphics.layer.GraphicsLayer,
) {
    val sharedBlurNode: RenderNode? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) RenderNode("SharedFullScreenBlur") else null
    var lastAppliedBlurRadius: Float = -1f
}

@Composable
fun rememberGraphicsLayerRecordingState(): GraphicsLayerRecordingState {
    val graphicsLayer = rememberGraphicsLayer()
    return remember(graphicsLayer) {
        GraphicsLayerRecordingState(graphicsLayer = graphicsLayer)
    }
}

fun Modifier.recordLayer(
    state: GraphicsLayerRecordingState,
    backgroundColor: Color,
    blurRadius: Float = 40f,
    topEdgeFadeHeight: Dp = 28.dp,
    bottomEdgeFadeHeight: Dp = 20.dp,
): Modifier = composed {
    val canvasHolder = remember { CanvasHolder() }

    this.drawWithContent {
        val w = size.width.toInt()
        val h = size.height.toInt()
        if (w > 0 && h > 0) {
            val topFadePx = topEdgeFadeHeight.toPx().coerceAtMost(h * 0.25f)
            val bottomFadePx = bottomEdgeFadeHeight.toPx().coerceAtMost(h * 0.25f)

            try {
                // 1. Записываем дерево контента ровно ОДИН раз
                state.graphicsLayer.record(size = IntSize(w, h)) {
                    this@drawWithContent.drawContent()
                }

                // 2. Запекаем общий блюр-узел S из записанного слоя C
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurRadius > 0.5f) {
                    val blurNode = state.sharedBlurNode!!
                    blurNode.setPosition(0, 0, w, h)
                    if (kotlin.math.abs(state.lastAppliedBlurRadius - blurRadius) > 0.25f) {
                        blurNode.setRenderEffect(
                            RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP)
                        )
                        state.lastAppliedBlurRadius = blurRadius
                    }
                    val recCanvas = blurNode.beginRecording(w, h)
                    canvasHolder.drawInto(recCanvas) {
                        drawRect(color = backgroundColor)
                        val orig = drawContext.canvas
                        drawContext.canvas = this@drawInto
                        drawLayerCompat(state.graphicsLayer, parentLayer = null)
                        drawContext.canvas = orig

                        if (topFadePx > 0f) {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(backgroundColor, backgroundColor.copy(alpha = 0f)),
                                    startY = 0f,
                                    endY = topFadePx,
                                ),
                                topLeft = Offset.Zero,
                                size = Size(size.width, topFadePx),
                            )
                        }
                        if (bottomFadePx > 0f) {
                            val startY = size.height - bottomFadePx
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(backgroundColor.copy(alpha = 0f), backgroundColor),
                                    startY = startY,
                                    endY = size.height,
                                ),
                                topLeft = Offset(0f, startY),
                                size = Size(size.width, bottomFadePx),
                            )
                        }
                    }
                    blurNode.endRecording()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            // 3. Рисуем записанный слой C на экран без второго обхода дерева Compose
            drawLayer(state.graphicsLayer)
        } else {
            drawContent()
        }
    }
}

/**
 * Отрисовывает вырез (маску) из уже готового [state.sharedBlurNode] со сдвигом [-offset].
 * Не создаёт новых RenderNode и не дублирует RenderEffect!
 */
@RequiresApi(Build.VERSION_CODES.S)
private fun Modifier.renderLayerAt(
    state: GraphicsLayerRecordingState,
    offsetProvider: () -> Offset,
): Modifier = this.drawBehind {
    val blurNode = state.sharedBlurNode ?: return@drawBehind
    if (!blurNode.hasDisplayList()) return@drawBehind
    val offset = offsetProvider()

    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        val saveCount = native.save()
        native.translate(-offset.x, -offset.y)
        native.drawRenderNode(blurNode)
        native.restoreToCount(saveCount)
    }
}

private fun DrawScope.drawLayerCompat(
    graphicsLayer: androidx.compose.ui.graphics.layer.GraphicsLayer,
    parentLayer: androidx.compose.ui.graphics.layer.GraphicsLayer?,
) {
    drawIntoCanvas { canvas ->
        invokeGraphicsLayerDrawReflectively(
            sourceLayer = graphicsLayer,
            destinationCanvas = canvas,
            parentLayer = parentLayer,
        )
    }
}

@Volatile
private var cachedGraphicsLayerDrawMethod: Method? = null

private fun invokeGraphicsLayerDrawReflectively(
    sourceLayer: androidx.compose.ui.graphics.layer.GraphicsLayer,
    destinationCanvas: androidx.compose.ui.graphics.Canvas,
    parentLayer: androidx.compose.ui.graphics.layer.GraphicsLayer?,
) {
    val method = cachedGraphicsLayerDrawMethod ?: resolveGraphicsLayerDrawMethod(sourceLayer)
    if (method != null) {
        try {
            method.invoke(sourceLayer, destinationCanvas, parentLayer)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
}

private fun resolveGraphicsLayerDrawMethod(
    sourceLayer: androidx.compose.ui.graphics.layer.GraphicsLayer,
): Method? {
    cachedGraphicsLayerDrawMethod?.let { return it }

    return try {
        val method = sourceLayer::class.java.declaredMethods.firstOrNull { candidate ->
            val params = candidate.parameterTypes
            params.size == 2 &&
                params[0].name.startsWith("androidx.compose.ui.graphics") &&
                params[1].name.startsWith("androidx.compose.ui.graphics.layer")
        }
        method?.isAccessible = true
        cachedGraphicsLayerDrawMethod = method
        method
    } catch (_: Throwable) {
        null
    }
}

/**
 * Равномерная матовая вуаль: сохраняет [minVeilAlpha] при высоком блюре для устранения пятнистости
 * и плавно доходит до 1.0f (непрозрачный фон) при снижении радиуса блюра к 0.
 */
private fun frostedVeilAlpha(blurRadius: Float, minVeilAlpha: Float): Float {
    val t = ((20f - blurRadius) / 20f).coerceIn(0f, 1f)
    return minVeilAlpha + (1f - minVeilAlpha) * t
}

private fun lowBlurTint(blurRadius: Float): Float =
    ((10f - blurRadius) / 10f).coerceIn(0f, 1f)

@Composable
fun TopBarBlurOverlay(
    modifier: Modifier = Modifier,
    topBarHeight: Dp,
    blurState: GraphicsLayerRecordingState,
    blurRadius: Float,
) {
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainer
    val veilAlpha = frostedVeilAlpha(blurRadius = blurRadius, minVeilAlpha = 0.58f)
    var region by remember { mutableStateOf(Rect.Zero) }

    Box(
        modifier = modifier.height(topBarHeight)
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurRadius > 0.5f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(surfaceColor)
            )

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        clip = true
                    }
                    .onGloballyPositioned { coords ->
                        region = coords.boundsInRoot()
                    }
                    .renderLayerAt(
                        state = blurState,
                        offsetProvider = { Offset(region.left, region.top) },
                    )
            )

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(surfaceColor.copy(alpha = veilAlpha))
            )
        } else {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(surfaceColor)
            )
        }
    }
}

@Composable
fun BottomBlurBar(
    modifier: Modifier = Modifier,
    blurState: GraphicsLayerRecordingState,
    blurRadius: Float,
    shape: Shape = CircleShape,
    content: @Composable () -> Unit,
) {
    val bgColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val veilColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val veilAlpha = frostedVeilAlpha(blurRadius = blurRadius, minVeilAlpha = 0.62f)
    var region by remember { mutableStateOf(Rect.Zero) }

    Box(
        modifier = modifier
            .clip(shape)
            .border(width = 1.dp, color = borderColor, shape = shape)
            .animateContentSize(
                animationSpec = spring(
                    stiffness = Spring.StiffnessHigh,
                    dampingRatio = Spring.DampingRatioNoBouncy,
                )
            ),
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurRadius > 0.5f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(bgColor)
            )

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        clip = true
                    }
                    .onPlaced { coords ->
                        region = coords.boundsInRoot()
                    }
                    .onGloballyPositioned { coords ->
                        region = coords.boundsInRoot()
                    }
                    .renderLayerAt(
                        state = blurState,
                        offsetProvider = { Offset(region.left, region.top) },
                    )
            )

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(veilColor.copy(alpha = veilAlpha))
            )
        } else {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(bgColor)
            )
        }

        content()
    }
}
