package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.kodraliu.localrock.shared.vacuum.map.MapFormat
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap

/** Only actual floor cells are eligible, with the same Y inversion as the normal map. */
internal fun cameraTargetAt(map: ParsedMap, normalized: Offset): Pair<Int,Int>? {
    if(map.format!=MapFormat.LEGACY || !normalized.x.isFinite() || !normalized.y.isFinite()) return null
    if(normalized.x !in 0f..<1f || normalized.y !in 0f..<1f) return null
    val x=(normalized.x*map.width).toInt()
    val y=map.height-1-(normalized.y*map.height).toInt()
    val cell=map.grid.getOrNull(y*map.width+x)?.toInt()?.and(255) ?: return null
    val original = map.originalGrid?.getOrNull(y*map.width+x)?.toInt()?.and(255)
    val scannedRoom = if (original != null) original and 7 == 7 && original ushr 3 > 0 else cell in 1..126
    if (cell == 127 || (cell != 128 && !scannedRoom)) return null
    val point=map.cellToMm(Offset(normalized.x*map.width,normalized.y*map.height))
    if(point.first !in 0..51_200 || point.second !in 0..51_200) return null
    if(map.noGoZones.any { it.contains(point.first,point.second) }) return null
    return point
}

@Composable
internal fun CameraTargetMap(map: ParsedMap,floor:Int,target:Pair<Int,Int>?,enabled:Boolean,onTarget:(Pair<Int,Int>)->Unit,modifier:Modifier,gestures:Boolean=true) {
    val bitmap=rememberMapBitmap(map)
    val viewport=remember { LiveMapViewport() }
    val identity = map.viewportIdentity(floor)
    val bounds = remember(identity) { map.contentBoundsNorm() }
    LaunchedEffect(identity) { viewport.frameIfNeeded(identity) }
    val currentMap by rememberUpdatedState(map)
    val selected by rememberUpdatedState(onTarget)
    fun viewFor(size: Size): MapView {
        val fit = fitScale(size.width, size.height, bounds.width * bitmap.width,
            bounds.height * bitmap.height, 0f, 1f, false)
        return framedView(size.width, size.height,
            (bounds.left + bounds.right) / 2f * bitmap.width, (bounds.top + bounds.bottom) / 2f * bitmap.height,
            fit, viewport.zoom, viewport.pan, 0f, 1f)
    }
    val currentViewFor by rememberUpdatedState(::viewFor)
    val drawn = remember { DrawnView() }
    Canvas(modifier.clipToBounds()
        .pointerInput(identity,gestures) { if (gestures) detectTransformGestures { center, pan, zoom, _ ->
            val next = (viewport.zoom * zoom).coerceIn(0.4f, 8f)
            val view = drawn.forHitTest { currentViewFor(size.toSize()) }
            val fit = view.k / viewport.zoom
            val canvasCenter = Offset(size.width / 2f, size.height / 2f)
            val moved = (center - canvasCenter) -
                ((center - canvasCenter) - viewport.pan * fit) * (next / viewport.zoom) + pan
            viewport.pan = Offset(moved.x.coerceIn(-size.width.toFloat(), size.width.toFloat()),
                moved.y.coerceIn(-size.height.toFloat(), size.height.toFloat())) / fit
            viewport.zoom = next
        }}
        .pointerInput(identity, enabled) { if (enabled) detectTapGestures(
            onDoubleTap = { viewport.reset() },
            onTap = { p ->
                val view = drawn.forHitTest { currentViewFor(size.toSize()) }
                val local = view.toBitmap(p.x, p.y)
                cameraTargetAt(currentMap, Offset(local.x / bitmap.width, local.y / bitmap.height))?.let(selected)
            },
        ) }) {
        val view = viewFor(size)
        drawn.view = view
        val origin = view.toScreen(0f, 0f)
        withTransform({ translate(origin.x, origin.y); scale(view.k, view.k, pivot = Offset.Zero) }) {
            drawImage(bitmap, srcOffset = IntOffset.Zero, srcSize = IntSize(bitmap.width, bitmap.height),
                dstOffset = IntOffset.Zero, dstSize = IntSize(bitmap.width, bitmap.height), filterQuality = FilterQuality.None)
            target?.let {
                val p = map.mmToCanvas(it.first, it.second, bitmap.width.toFloat(), bitmap.height.toFloat())
                drawCircle(Color.White, 10.dp.toPx() / view.k, p, style = Stroke(3.dp.toPx() / view.k))
                drawCircle(Color(0xff3185ff), 5.dp.toPx() / view.k, p)
            }
        }
    }
}
