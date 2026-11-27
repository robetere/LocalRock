package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun CameraControllerSettings(
    connectedPad: String?, layout: CameraPadLayout,
    onLayoutChange: (CameraPadLayout) -> Unit, ready: Boolean,
    onUseController: () -> Unit,
) {
    val input = CameraGamepadMonitor()
    val colors = MaterialTheme.colorScheme
    val name = input.deviceName ?: connectedPad
    val connected = name != null
    Surface(shape=RoundedCornerShape(16.dp), color=colors.surfaceVariant, modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), horizontalArrangement=Arrangement.spacedBy(12.dp),
            verticalAlignment=Alignment.CenterVertically) {
            Surface(shape=RoundedCornerShape(12.dp), color=colors.secondaryContainer) {
                Canvas(Modifier.padding(9.dp).size(34.dp)) {
                    withTransform({ scale(size.width/100f,size.height/70f,pivot=Offset.Zero) }) {
                        drawRoundRect(colors.onSecondaryContainer,Offset(12f,18f),Size(76f,40f),CornerRadius(19f))
                        drawCircle(colors.onSecondaryContainer,15f,Offset(23f,48f))
                        drawCircle(colors.onSecondaryContainer,15f,Offset(77f,48f))
                        drawLine(colors.secondaryContainer,Offset(25f,35f),Offset(43f,35f),4f)
                        drawLine(colors.secondaryContainer,Offset(34f,26f),Offset(34f,44f),4f)
                        drawCircle(colors.secondaryContainer,4f,Offset(69f,29f))
                        drawCircle(colors.secondaryContainer,4f,Offset(77f,37f))
                    }
                }
            }
            Column(Modifier.weight(1f)) {
                Text(name ?: "No controller detected", style=MaterialTheme.typography.titleSmall)
                Text(if(connected) "Connected • Input monitor" else "Pair a controller in Android Settings",
                    style=MaterialTheme.typography.bodySmall,
                    color=if(connected) colors.primary else colors.onSurfaceVariant)
            }
            if(connected) Icon(Icons.Default.CheckCircle,null,tint=colors.primary)
        }
    }
    Text("Driving layout",style=MaterialTheme.typography.titleSmall)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        FilterChip(selected=layout==CameraPadLayout.RACING,onClick={onLayoutChange(CameraPadLayout.RACING)},
            label={Text("Racing")},modifier=Modifier.weight(1f))
        FilterChip(selected=layout==CameraPadLayout.JOYSTICK,onClick={onLayoutChange(CameraPadLayout.JOYSTICK)},
            label={Text("Joystick")},modifier=Modifier.weight(1f))
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if(maxWidth>=520.dp) {
            Row(horizontalArrangement=Arrangement.spacedBy(16.dp),verticalAlignment=Alignment.Top) {
                Column(Modifier.weight(1.2f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    ControllerInputPanel(input,layout,connected)
                }
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    ControllerMappings(layout,input)
                }
            }
        } else {
            Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                ControllerInputPanel(input,layout,connected)
                ControllerMappings(layout,input)
            }
        }
    }
    Surface(color=colors.secondaryContainer,shape=RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Info,null,tint=colors.onSecondaryContainer)
            Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text("Safe input testing",style=MaterialTheme.typography.titleSmall,color=colors.onSecondaryContainer)
                Text("Controller input here never drives the robot. To drive, select Use controller, move and centre the controls, then press A. Release the controls to stop.",
                    style=MaterialTheme.typography.bodySmall,color=colors.onSecondaryContainer)
            }
        }
    }
    Button(onClick=onUseController,enabled=connectedPad!=null && ready,modifier=Modifier.fillMaxWidth()) {
        Text("Use controller")
    }
}

private val PadGreen=Color(0xff94dfb5)
private val PadAmber=Color(0xffedb46b)
private val PadRed=Color(0xfff08c91)
private val PadBlue=Color(0xff80b9ef)
private val PadYellow=Color(0xffe5c66c)

@Composable
private fun ControllerInputPanel(input:CameraPadMonitorSnapshot,layout:CameraPadLayout,connected:Boolean) {
    val active = input.pressed.isNotEmpty() || listOf(input.leftX,input.leftY,input.rightX,input.rightY,input.leftTrigger,input.rightTrigger).any {abs(it)>.02}
    Surface(color=Color(0xff181d20),shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("LIVE INPUT MONITOR",style=MaterialTheme.typography.labelSmall,color=Color(0xffb4c1bb),modifier=Modifier.weight(1f))
                Text(if(!input.available) "Unavailable" else if(!connected) "Offline" else if(active) "Input" else "Ready",
                    style=MaterialTheme.typography.labelSmall,color=if(active) PadGreen else Color(0xffb4c1bb))
            }
            ControllerDiagram(input,layout)
            Text(if(!input.available) "Input monitor could not attach to this window." else if(!connected)
                "Connect a game controller." else "Press buttons or move either stick to test inputs.",
                style=MaterialTheme.typography.bodySmall,color=Color(0xffb4c1bb))
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                InputMeter("LT",input.leftTrigger,PadAmber,Modifier.weight(1f))
                InputMeter("RT",input.rightTrigger,PadGreen,Modifier.weight(1f))
            }
            Text("Left stick  X ${percent(input.leftX)}   Y ${percent(input.leftY)}",
                style=MaterialTheme.typography.labelSmall,color=Color.White)
            Text("Right stick  X ${percent(input.rightX)}   Y ${percent(input.rightY)}",
                style=MaterialTheme.typography.labelSmall,color=Color.White)
            val effective=input.effective(layout)
            Text(when {
                effective==null -> "Effective drive: invalid input"
                effective.conflicting -> "Effective drive: conflicting triggers — stop"
                effective.neutral -> "Effective drive: neutral (deadzone applied)"
                else -> "Effective drive: ${percent(effective.throttle)} throttle / ${percent(effective.steering)} steer"
            },style=MaterialTheme.typography.labelSmall,color=if(effective?.conflicting==true) PadRed else PadGreen)
            Text("Read-only preview • No motion commands",style=MaterialTheme.typography.labelSmall,color=Color(0xffb4c1bb))
        }
    }
}

private fun percent(value:Double) = "${(value*100).roundToInt()}%"

@Composable
private fun InputMeter(label:String,value:Double,tint:Color,modifier:Modifier) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Row {
            Text(label,color=tint,style=MaterialTheme.typography.labelSmall)
            Spacer(Modifier.weight(1f))
            Text(percent(value),color=Color.White,style=MaterialTheme.typography.labelSmall)
        }
        Box(Modifier.fillMaxWidth().height(5.dp).background(Color(0xff414b50),RoundedCornerShape(3.dp))) {
            Box(Modifier.fillMaxWidth(value.toFloat().coerceIn(0f,1f)).fillMaxHeight().background(tint,RoundedCornerShape(3.dp)))
        }
    }
}

@Composable
private fun ControllerMappings(layout:CameraPadLayout,input:CameraPadMonitorSnapshot) {
    Text("Control mapping",style=MaterialTheme.typography.titleSmall)
    val p=input.pressed
    if(layout==CameraPadLayout.RACING) {
        ControllerMapping("RT","Accelerate",PadGreen,input.rightTrigger>.08 || CameraPadButton.RT in p)
        ControllerMapping("LT","Reverse",PadAmber,input.leftTrigger>.08 || CameraPadButton.LT in p)
    }
    ControllerMapping("LS",if(layout==CameraPadLayout.RACING) "Steer left / right" else "Forward, reverse and steer",PadGreen,
        abs(input.leftX)>.15 || (layout==CameraPadLayout.JOYSTICK && abs(input.leftY)>.15))
    ControllerMapping("A","Enable / disable driving",PadGreen,CameraPadButton.A in p)
    ControllerMapping("B","Stop and disarm",PadRed,CameraPadButton.B in p)
    Text("Stick deadzone 15% • Trigger deadzone 8%",style=MaterialTheme.typography.bodySmall)
}

@Composable
private fun ControllerMapping(key:String,action:String,tint:Color,active:Boolean) {
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Surface(shape=RoundedCornerShape(8.dp),color=if(active) tint else MaterialTheme.colorScheme.surfaceVariant) {
            Box(Modifier.width(38.dp).padding(vertical=6.dp),contentAlignment=Alignment.Center) {
                Text(key,color=if(active) Color(0xff17201b) else MaterialTheme.colorScheme.onSurfaceVariant,
                    style=MaterialTheme.typography.labelMedium)
            }
        }
        Text(action,style=MaterialTheme.typography.bodySmall,modifier=Modifier.weight(1f))
    }
}

/** Xbox-style diagram, drawn natively; all coordinates are a 340 × 212 logical viewport. */
@Composable
private fun ControllerDiagram(input:CameraPadMonitorSnapshot,layout:CameraPadLayout) {
    val measurer=rememberTextMeasurer()
    val p=input.pressed
    Canvas(Modifier.fillMaxWidth().aspectRatio(340f/212f).semantics {
        contentDescription="Controller diagram. Pressed buttons: ${p.joinToString().ifEmpty { "none" }}. Left stick X ${percent(input.leftX)}, Y ${percent(input.leftY)}."
    }) {
        withTransform({scale(size.width/340f,size.height/212f,pivot=Offset.Zero)}) {
            val shell=Color(0xff424d54)
            val dark=Color(0xff20282c)
            val edge=Color(0xff718088)
            fun label(s:String,x:Float,y:Float,color:Color=Color(0xffdce4e0),font:Float=11f) {
                val result=measurer.measure(s,style=TextStyle(color=color,fontSize=(font/density/fontScale).sp,fontWeight=FontWeight.Medium))
                drawText(result,topLeft=Offset(x-result.size.width/2f,y-result.size.height/2f))
            }
            fun rounded(x:Float,y:Float,w:Float,h:Float,color:Color,r:Float=6f) =
                drawRoundRect(color,Offset(x,y),Size(w,h),CornerRadius(r))
            fun shoulder(x:Float,trigger:Double,triggerButton:CameraPadButton,bumper:CameraPadButton,tint:Color,name:String) {
                rounded(x,18f,58f,23f,dark,8f)
                val level=if(triggerButton in p) 1f else trigger.toFloat()
                if(level>0) rounded(x+2,39f-19f*level,54f,19f*level,tint,4f)
                drawRoundRect(if(level>0) tint else edge,Offset(x,18f),Size(58f,23f),CornerRadius(8f),style=Stroke(1f))
                label(name,x+29f,29f,if(level>.5f) dark else Color.White)
                rounded(x,43f,58f,12f,if(bumper in p) PadGreen else edge,5f)
                label(if(bumper==CameraPadButton.LB) "LB" else "RB",x+29f,49f,dark,9f)
            }
            shoulder(72f,input.leftTrigger,CameraPadButton.LT,CameraPadButton.LB,PadAmber,"LT")
            shoulder(210f,input.rightTrigger,CameraPadButton.RT,CameraPadButton.RB,PadGreen,"RT")
            label(if(layout==CameraPadLayout.RACING) "Reverse" else "Trigger",101f,9f,PadAmber,10f)
            label(if(layout==CameraPadLayout.RACING) "Accelerate" else "Trigger",239f,9f,PadGreen,10f)
            val body=Path().apply {
                moveTo(98f,54f); quadraticTo(74f,54f,62f,81f)
                lineTo(38f,151f); quadraticTo(26f,187f,47f,198f)
                quadraticTo(67f,209f,91f,175f); lineTo(115f,146f)
                quadraticTo(123f,140f,137f,140f); lineTo(203f,140f)
                quadraticTo(217f,140f,225f,146f); lineTo(249f,175f)
                quadraticTo(273f,209f,293f,198f); quadraticTo(314f,187f,302f,151f)
                lineTo(278f,81f); quadraticTo(266f,54f,242f,54f)
                quadraticTo(226f,54f,210f,62f); lineTo(130f,62f)
                quadraticTo(114f,54f,98f,54f); close()
            }
            drawPath(body,shell); drawPath(body,edge,style=Stroke(1.6f))
            fun stick(center:Offset,x:Double,y:Double,button:CameraPadButton) {
                val moving=abs(x)>.15 || abs(y)>.15 || button in p
                drawCircle(dark,24f,center)
                drawCircle(if(moving) PadGreen else edge,24f,center,style=Stroke(1.4f))
                drawCircle(edge,24f*.15f,center,style=Stroke(.8f))
                val pos=center+Offset((x*11).toFloat(),(y*11).toFloat())
                drawLine(if(moving) PadGreen else edge,center,pos,2f)
                drawCircle(if(moving) PadGreen else Color(0xff66767d),12f,pos)
                drawCircle(if(button in p) Color.White else edge,12f,pos,style=Stroke(1.5f))
            }
            stick(Offset(109f,90f),input.leftX,input.leftY,CameraPadButton.LEFT_STICK)
            stick(Offset(204f,128f),input.rightX,input.rightY,CameraPadButton.RIGHT_STICK)
            fun face(button:CameraPadButton,x:Float,y:Float,tint:Color) {
                val on=button in p; val center=Offset(x,y)
                if(on) drawCircle(tint.copy(alpha=.24f),16f,center)
                drawCircle(if(on) tint else dark,10.5f,center)
                drawCircle(tint,10.5f,center,style=Stroke(if(on) 2f else 1f))
                label(button.name,x,y,if(on) dark else tint,11f)
            }
            face(CameraPadButton.Y,245f,73f,PadYellow)
            face(CameraPadButton.X,228f,90f,PadBlue)
            face(CameraPadButton.B,262f,90f,PadRed)
            face(CameraPadButton.A,245f,107f,PadGreen)
            fun dpad(x:Float,y:Float,button:CameraPadButton) {
                rounded(x,y,14f,14f,if(button in p) PadGreen else dark,2f)
            }
            dpad(126f,111f,CameraPadButton.UP)
            dpad(112f,125f,CameraPadButton.LEFT)
            rounded(126f,125f,14f,14f,dark,2f)
            dpad(140f,125f,CameraPadButton.RIGHT)
            dpad(126f,139f,CameraPadButton.DOWN)
            drawCircle(Color(0xffbecac6),11f,Offset(170f,78f))
            drawLine(shell,Offset(164f,72f),Offset(176f,84f),2f)
            drawLine(shell,Offset(176f,72f),Offset(164f,84f),2f)
            drawCircle(if(CameraPadButton.BACK in p) PadGreen else dark,6f,Offset(151f,100f))
            drawCircle(if(CameraPadButton.START in p) PadGreen else dark,6f,Offset(189f,100f))
            label("−",151f,100f,Color.White,10f); label("≡",189f,100f,Color.White,10f)
        }
    }
}
