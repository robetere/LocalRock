package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.vacuum.camera.CameraMotionController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

/** One fresh touch gesture owns one drive operation. No restart after neutral or failure. */
@Composable
internal fun CameraPilotJoystick(motion: CameraMotionController, enabled: Boolean, modifier: Modifier) {
    var knob by remember { mutableStateOf(Offset.Zero) }
    Canvas(modifier.semantics {contentDescription="Hold and drag joystick to drive; release to stop"}
        .clip(CircleShape).background(Color(0xff171c20).copy(alpha=.78f))
        .pointerInput(motion,enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed=false)
                down.consume()
                val center=Offset(size.width/2f,size.height/2f)
                val radius=minOf(size.width,size.height)*.36f
                var attempted=false
                var running=false
                fun update(position: Offset) {
                    val raw=(position-center)/radius.coerceAtLeast(1f)
                    val len=sqrt(raw.x*raw.x+raw.y*raw.y)
                    val unit=if(len>1f) raw/len else raw
                    knob=unit
                    if(len<.16f) {
                        if(running) {motion.release();running=false}
                    } else if(!attempted || running) {
                        val throttle=if(abs(unit.y)<.14f) 0.0 else -unit.y.toDouble()
                        val steering=if(abs(unit.x)<.14f) 0.0 else -unit.x.toDouble()
                        if(throttle!=0.0 || steering!=0.0) {
                            running=motion.pilotInput(throttle,steering,start=!attempted)
                            attempted=true
                        } else if(running) {
                            motion.release()
                            running=false
                        }
                    }
                }
                try {
                    update(down.position)
                    while(true) {
                        val event=awaitPointerEvent()
                        val change=event.changes.firstOrNull {it.id==down.id} ?: break
                        if(!change.pressed || event.changes.count {it.pressed}>1) break
                        if(change.isConsumed) break
                        update(change.position)
                        change.consume()
                    }
                } finally { motion.release(); knob=Offset.Zero }
            }
        }) {
        val r=size.minDimension/2f
        val tint=if(enabled) Color.White else Color.Gray
        drawCircle(tint.copy(alpha=.45f),r*.92f,style=Stroke(1.5.dp.toPx()))
        drawCircle(tint.copy(alpha=.25f),r*.70f,style=Stroke(1.dp.toPx()))
        val c=center
        for (v in listOf(Offset(0f,-1f),Offset(0f,1f),Offset(-1f,0f),Offset(1f,0f))) {
            drawLine(tint,c+v*(r*.78f),c+v*(r*.88f),strokeWidth=2.dp.toPx())
        }
        drawCircle(Color(0xff64747d),r*.31f,c+knob*(r*.70f))
        drawCircle(tint.copy(alpha=.8f),r*.31f,c+knob*(r*.70f),style=Stroke(1.dp.toPx()))
    }
}

@Composable
internal fun CameraDrivePad(motion: CameraMotionController, enabled: Boolean, modifier: Modifier, includeDockStop: Boolean = true) {
    val scope=rememberCoroutineScope()
    Box(modifier.clip(CircleShape).background(Color(0xff242424).copy(alpha=.55f))) {
        val keys=listOf(
            Triple(CameraMotionController.Direction.FORWARD,Alignment.TopCenter,Icons.Default.KeyboardArrowUp),
            Triple(CameraMotionController.Direction.BACKWARD,Alignment.BottomCenter,Icons.Default.KeyboardArrowDown),
            Triple(CameraMotionController.Direction.LEFT,Alignment.CenterStart,Icons.Default.RotateLeft),
            Triple(CameraMotionController.Direction.RIGHT,Alignment.CenterEnd,Icons.Default.RotateRight),
        )
        keys.forEach { (direction,align,icon) ->
            val label=when(direction){
                CameraMotionController.Direction.FORWARD -> "Drive forward"
                CameraMotionController.Direction.BACKWARD -> "Drive backward"
                CameraMotionController.Direction.LEFT -> "Turn left"
                CameraMotionController.Direction.RIGHT -> "Turn right"
            }
            Box(Modifier.align(align).fillMaxSize(.32f)
                .semantics { contentDescription=label; onClick {
                    if(enabled) scope.launch { motion.hold(direction);delay(250);motion.release() }
                    enabled
                } }
                .pointerInput(enabled,direction) {
                    if(enabled) detectTapGestures(onPress={
                        motion.hold(direction)
                        try { tryAwaitRelease() } finally { motion.release() }
                    })
                },contentAlignment=Alignment.Center) {
                Icon(icon,label,Modifier.size(34.dp),tint=if(enabled) Color.White else Color.Gray)
            }
        }
        FilledTonalIconButton(onClick={motion.stop(includeDock=includeDockStop)},modifier=Modifier.align(Alignment.Center).fillMaxSize(.27f)) {
            Icon(Icons.Default.Stop,"Stop movement")
        }
    }
}

