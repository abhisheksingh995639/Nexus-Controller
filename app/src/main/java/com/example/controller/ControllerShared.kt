package com.example.controller
 
import androidx.compose.ui.zIndex
import androidx.compose.material.icons.rounded.Info

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*

object AppColors {
    val BackgroundDark = Color(0xFF101622)
    val BackgroundGradientStart = Color(0xFF1a202e)
    val BackgroundGradientEnd = Color.Black
    val Surface = Color(0xFF1A1D26)
    val SurfaceHighlight = Color(0xFF2a2f3a)
    val Primary = Color(0xFF0d59f2)
    val Accent = Color(0xFF3b82f6)
    val TextWhite = Color.White
    val TextGray = Color(0xFFAAAAAA)
    
    // Stitch Colors
    val GlowBlue = Color(0x660D59F2)
    val GlowGreen = Color(0x6622C55E)
    
    // Racing Colors
    val NeonBlue = Color(0xFF00F3FF)
    val NeonRed = Color(0xFFFF003C)
    val NeonYellow = Color(0xFFFBBF24)
    val CarbonDark = Color(0xFF080808)

    val BackgroundLight = Color(0xFFf5f6f8)
}

// Data class to hold layout config
class CompConfig(
    initialX: Float,
    initialY: Float,
    initialScale: Float = 1f,
    initialRotation: Float = 0f,
    initialKey: Int = 0,
    initialTurbo: Boolean = false
) {
    var x by mutableFloatStateOf(initialX)
    var y by mutableFloatStateOf(initialY)
    var scale by mutableFloatStateOf(initialScale)
    var rotation by mutableFloatStateOf(initialRotation)
    var mappedKey by mutableIntStateOf(initialKey)
    var isTurbo by mutableStateOf(initialTurbo)

    fun copy() = CompConfig(x, y, scale, rotation, mappedKey, isTurbo)
}

@Composable
fun EditableComponent(
    id: String, 
    isEditMode: Boolean, 
    isSelected: Boolean, 
    config: CompConfig, 
    onSelect: (String) -> Unit, 
    onDelete: () -> Unit = {}, 
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .offset { IntOffset(config.x.roundToInt(), config.y.roundToInt()) }
            .scale(config.scale)
            .pointerInput(isEditMode) { if (isEditMode) detectTapGestures { onSelect(id) } }
            .pointerInput(isEditMode) {
                if (isEditMode) {
                    detectTransformGestures { _, pan, _, _ ->
                         if (!isSelected) onSelect(id)
                         
                         val dx = pan.x * config.scale
                         val dy = pan.y * config.scale
                         
                         config.x += dx
                         config.y += dy
                    }
                }
            }
            .then(
                if (isEditMode && isSelected) {
                    Modifier
                        .shadow(12.dp, RoundedCornerShape(8.dp), spotColor = AppColors.Primary)
                        .border(2.dp, AppColors.Primary, RoundedCornerShape(8.dp))
                        .background(AppColors.Primary.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                } else if (isEditMode) {
                     Modifier.border(1.dp, Color.Yellow.copy(alpha=0.3f), RoundedCornerShape(4.dp))
                } else Modifier
            )
    ) {
        content()
        
        if (isEditMode) {
             Box(Modifier.matchParentSize().pointerInput(Unit) { detectTapGestures { onSelect(id) } })
             
             if (isSelected) {
                 if (id.startsWith("BTN_")) {
                     Box(
                         modifier = Modifier
                             .align(Alignment.TopEnd)
                             .offset(x = 12.dp, y = (-12).dp)
                             .size(24.dp)
                             .background(Color.Red, CircleShape)
                             .border(1.dp, Color.White, CircleShape)
                             .pointerInput(Unit) { detectTapGestures { onDelete() } }
                     ) {
                         Text("X", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
                     }
                 }
                 
                 if (id.startsWith("BTN_") || id in listOf("FACE", "DPAD", "L1", "R1", "L2", "R2")) {
                     Box(
                         modifier = Modifier
                             .align(Alignment.BottomEnd)
                             .offset(x = 12.dp, y = 12.dp)
                             .size(24.dp)
                             .background(if(config.isTurbo) Color.Green else Color.Gray, CircleShape)
                             .border(1.dp, Color.White, CircleShape)
                             .pointerInput(Unit) { detectTapGestures { config.isTurbo = !config.isTurbo } }
                     ) {
                         Text("T", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
                     }
                 }
                 
                 Box(
                     modifier = Modifier
                         .align(Alignment.Center)
                         .offset(y = 40.dp)
                         .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                         .padding(horizontal = 4.dp, vertical = 2.dp)
                 ) {
                     Text(
                         "∠${config.rotation.toInt()}° | ${String.format("%.1f", config.scale)}x",
                         color = Color.White,
                         fontSize = 10.sp,
                         fontFamily = FontFamily.Monospace,
                         fontWeight = FontWeight.Bold
                     )
                 }
             }
        }
    }
}

@Composable
fun PSTriggerShape(label: String, modifier: Modifier, bg: Color, txt: Color, onValue: (Float) -> Unit) {
    var triggerValue by remember { mutableStateOf(0f) }
    val brush = Brush.linearGradient(colors = listOf(AppColors.SurfaceHighlight, AppColors.Surface))
    val shape = if (label == "L2") RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 4.dp)
                else RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 4.dp, bottomEnd = 24.dp)

    Box(
        modifier = modifier
            .shadow(4.dp, shape)
            .size(90.dp, 60.dp)
            .clip(shape)
            .background(brush)
            .border(1.dp, Color.White.copy(alpha=0.1f), shape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val height = size.height.toFloat()
                    var y = down.position.y.coerceIn(0f, height)
                    triggerValue = y / height
                    onValue(triggerValue)
                    
                    var dragging = true
                    while (dragging) {
                        val event = awaitPointerEvent()
                        val change = event.changes.find { it.id == down.id }
                        if (change != null && change.pressed) {
                             y = change.position.y.coerceIn(0f, height)
                             triggerValue = y / height
                             onValue(triggerValue)
                        } else dragging = false
                    }
                    triggerValue = 0f
                    onValue(0f)
                }
            }
    ) {
         Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(triggerValue).align(Alignment.TopCenter).background(AppColors.Primary.copy(alpha=0.4f)))
         Text(label, Modifier.align(Alignment.Center), color=Color.White.copy(alpha=0.6f), fontWeight=FontWeight.Bold, fontSize = 14.sp, letterSpacing = 2.sp)
         Box(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color.White.copy(alpha=0.05f)))
    }
}

@Composable
fun PSBumperShape(label: String, modifier: Modifier, bg: Color, txt: Color, mask: Int, onVibrate: () -> Unit, onEvent: (Int, Boolean) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    LaunchedEffect(isPressed) { if(isPressed) { onVibrate(); onEvent(mask, true) } else onEvent(mask, false) }
    
    val brush = Brush.linearGradient(
        colors = if (isPressed) listOf(AppColors.Primary, AppColors.Accent) else listOf(AppColors.SurfaceHighlight, AppColors.Surface)
    )
    
    Box(
        modifier = modifier
            .size(90.dp, 40.dp)
            .shadow(if (isPressed) 8.dp else 4.dp, RoundedCornerShape(8.dp), spotColor = AppColors.Primary)
            .clip(RoundedCornerShape(8.dp))
            .background(brush)
            .border(1.dp, Color.White.copy(alpha=0.1f), RoundedCornerShape(8.dp))
            .pointerInput(Unit) { detectTapGestures(onPress = { onEvent(mask, true); tryAwaitRelease(); onEvent(mask, false) }) }
    ) {
        Text(label, Modifier.align(Alignment.Center), color = if(isPressed) Color.White else Color.White.copy(alpha=0.7f), fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 2.sp)
    }
}

@Composable
fun PSShapeButtonSimple(label: String, modifier: Modifier = Modifier, mask: Int, onVibrate: () -> Unit, onEvent: (Int, Boolean) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    LaunchedEffect(isPressed) { if(isPressed) { onVibrate(); onEvent(mask, true) } else onEvent(mask, false) }

    val brush = Brush.linearGradient(
        colors = if (isPressed) listOf(AppColors.Primary, AppColors.Accent) else listOf(AppColors.SurfaceHighlight, AppColors.Surface)
    )
    
    Box(
        modifier = modifier
            .size(70.dp)
            .shadow(if (isPressed) 12.dp else 4.dp, CircleShape, spotColor = AppColors.Primary)
            .clip(CircleShape)
            .background(brush)
            .border(2.dp, if(isPressed) Color.White.copy(alpha=0.3f) else Color.White.copy(alpha=0.1f), CircleShape)
            .pointerInput(Unit) { detectTapGestures(onPress = { onEvent(mask, true); tryAwaitRelease(); onEvent(mask, false) }) },
        contentAlignment = Alignment.Center
    ) {
        val symbol = when(label) { "CROSS"->"✕"; "CIRCLE"->"○"; "SQUARE"->"□"; "TRIANGLE"->"△"; else->label }
        Text(symbol, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PSDpadDetailed(bg: Color, txt: Color, onPress: (Int, Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .size(180.dp)
            .background(AppColors.Surface.copy(alpha = 0.2f), CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.05f), CircleShape)
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Spacer(Modifier.size(50.dp))
                DpadButton("arrow_drop_up", 0, onPress)
                Spacer(Modifier.size(50.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DpadButton("arrow_left", 2, onPress)
                Spacer(Modifier.size(50.dp))
                DpadButton("arrow_right", 3, onPress)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Spacer(Modifier.size(50.dp))
                DpadButton("arrow_drop_down", 1, onPress)
                Spacer(Modifier.size(50.dp))
            }
        }
    }
}

@Composable
fun DpadButton(icon: String, dir: Int, onPress: (Int, Boolean) -> Unit) {
    var isPressed by remember { mutableStateOf(false) }
    val brush = Brush.linearGradient(colors = if (isPressed) listOf(AppColors.Primary, AppColors.Accent) else listOf(AppColors.SurfaceHighlight, AppColors.Surface))
    Box(
        modifier = Modifier
            .shadow(if (isPressed) 12.dp else 4.dp, RoundedCornerShape(12.dp), spotColor = AppColors.Primary)
            .size(50.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(brush)
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            .pointerInput(Unit) { detectTapGestures(onPress = { isPressed=true; onPress(dir, true); tryAwaitRelease(); isPressed=false; onPress(dir, false) }) },
        contentAlignment = Alignment.Center
    ) {
        val arrow = when(icon) { "arrow_drop_up"->"▲"; "arrow_drop_down"->"▼"; "arrow_left"->"◄"; "arrow_right"->"►"; else->"?" }
        Text(arrow, color = if (isPressed) Color.White else Color.White.copy(alpha = 0.5f), fontSize = 20.sp)
    }
}

@Composable
fun PSCenterButton(
    label: String, 
    modifier: Modifier = Modifier, 
    bg: Color, 
    mask: Int, 
    isConnected: Boolean = false,
    onVibrate: () -> Unit, 
    onEvent: (Int, Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    LaunchedEffect(isPressed) { if(isPressed) { onVibrate(); onEvent(mask, true) } else onEvent(mask, false) }
    
    val isPS = label.uppercase() == "PS"
    val accentColor = if (isPS) {
        if (isConnected) Color(0xFF22C55E) else Color(0xFFEF4444)
    } else Color.White.copy(alpha=0.6f)

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(if (isPS) 68.dp else 44.dp, if (isPS) 68.dp else 26.dp)
                .shadow(if (isPressed) 16.dp else 4.dp, if (isPS) CircleShape else RoundedCornerShape(50), spotColor = if(isPS) accentColor else Color.Black)
                .clip(if (isPS) CircleShape else RoundedCornerShape(50))
                .background(brush = if (isPS) Brush.linearGradient(listOf(AppColors.SurfaceHighlight, AppColors.Surface)) else Brush.verticalGradient(listOf(AppColors.SurfaceHighlight, AppColors.Surface)))
                .border(2.dp, if(isPS && isPressed) accentColor else Color.White.copy(alpha = 0.1f), if (isPS) CircleShape else RoundedCornerShape(50))
                .pointerInput(Unit) { detectTapGestures(onPress = { onEvent(mask, true); tryAwaitRelease(); onEvent(mask, false) }) },
            contentAlignment = Alignment.Center
        ) {
            if (isPS) {
                Canvas(modifier = Modifier.size(28.dp)) {
                    val c = if(isPressed) Color.White else accentColor
                    val sw = 5f
                    val w = size.width
                    val h = size.height
                    val p = Path().apply {
                         moveTo(0f, h * 0.4f); quadraticBezierTo(0f, 0f, w * 0.3f, 0f); lineTo(w * 0.7f, 0f); quadraticBezierTo(w, 0f, w, h * 0.4f)
                         lineTo(w * 0.9f, h); quadraticBezierTo(w * 0.8f, h * 0.7f, w * 0.6f, h * 0.7f); lineTo(w * 0.4f, h * 0.7f)
                         quadraticBezierTo(w * 0.2f, h * 0.7f, w * 0.1f, h); close()
                    }
                    drawPath(p, c, style = Stroke(width = sw, join = StrokeJoin.Round))
                }
            } else if (label == "SHARE") {
                  Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { repeat(2) { Box(Modifier.size(2.dp, 10.dp).background(Color.White.copy(alpha=0.4f), CircleShape)) } }
            } else if (label == "OPTIONS") {
                  Column(verticalArrangement = Arrangement.spacedBy(3.dp)) { repeat(3) { Box(Modifier.size(10.dp, 2.dp).background(Color.White.copy(alpha=0.4f), CircleShape)) } }
            }
        }
    }
}

@Composable
fun PSFaceButtonsDetailed(bg: Color, onPress: (Int, Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .size(180.dp)
            .background(AppColors.Surface.copy(alpha = 0.2f), CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.05f), CircleShape)
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Spacer(Modifier.size(50.dp))
                PSShapeButtonSimple("TRIANGLE", Modifier.size(50.dp), 0x08, onPress); Spacer(Modifier.size(50.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PSShapeButtonSimple("SQUARE", Modifier.size(50.dp), 0x04, onPress); Spacer(Modifier.size(50.dp))
                PSShapeButtonSimple("CIRCLE", Modifier.size(50.dp), 0x02, onPress)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Spacer(Modifier.size(50.dp))
                PSShapeButtonSimple("CROSS", Modifier.size(50.dp), 0x01, onPress); Spacer(Modifier.size(50.dp))
            }
        }
    }
}

@Composable
fun PSShapeButtonSimple(shape: String, modifier: Modifier, mask: Int, onEvent: (Int, Boolean) -> Unit) {
    var isPressed by remember { mutableStateOf(false) }
    val glowColor = when(shape) { "TRIANGLE" -> Color(0xFF22C55E); "SQUARE" -> Color(0xFFEC4899); "CIRCLE" -> Color(0xFFEF4444); "CROSS" -> Color(0xFF3B82F6); else -> Color.White }
    val brush = Brush.linearGradient(colors = if (isPressed) listOf(glowColor, glowColor.copy(alpha=0.8f)) else listOf(AppColors.SurfaceHighlight, AppColors.Surface))
    Box(
        modifier = modifier
            .shadow(if (isPressed) 12.dp else 4.dp, CircleShape, spotColor = glowColor)
            .clip(CircleShape).background(brush).border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
            .pointerInput(Unit) { detectTapGestures(onPress = { isPressed=true; onEvent(mask, true); tryAwaitRelease(); isPressed=false; onEvent(mask, false) }) },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(24.dp)) {
            val c = if (isPressed) Color.White else glowColor
            val sw = 6f
            val w = size.width
            val h = size.height
            when(shape) {
                "TRIANGLE" -> drawPath(Path().apply { moveTo(w/2, 0f); lineTo(w, h); lineTo(0f, h); close() }, c, style=Stroke(width=sw, join=StrokeJoin.Round))
                "CIRCLE" -> drawCircle(c, style=Stroke(width=sw))
                "CROSS" -> { drawLine(c, Offset(0f,0f), Offset(w,h), sw, cap=StrokeCap.Round); drawLine(c, Offset(w,0f), Offset(0f,h), sw, cap=StrokeCap.Round) }
                "SQUARE" -> drawRect(c, style=Stroke(width=sw))
            }
        }
    }
}

@Composable
fun PSJoystickSimple(label: String, bg: Color, stroke: Color, onMoved: (Float, Float) -> Unit) {
    var knobPosition by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val isMoving = knobPosition != Offset.Zero
    Box(
        modifier = Modifier.size(110.dp).onSizeChanged { containerSize = it }.background(Color(0xFF0F1218), CircleShape).border(2.dp, Color.White.copy(alpha=0.05f), CircleShape).shadow(15.dp, CircleShape, clip = false),
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier.offset { IntOffset(knobPosition.x.toInt(), knobPosition.y.toInt()) }.size(60.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF454C5B), Color(0xFF1A1D24))), CircleShape).border(1.5.dp, Color.White.copy(alpha = 0.12f), CircleShape))
            Box(Modifier.fillMaxSize(0.65f).background(Brush.radialGradient(0f to Color(0xFF333A47), 1f to Color(0xFF10141C)), CircleShape), contentAlignment = Alignment.Center) {
                Text(if(label=="L") "L3" else "R3", color = if(isMoving) AppColors.Accent else Color.White.copy(alpha=0.35f), fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }
        Box(Modifier.fillMaxSize().pointerInput(Unit) {
            detectDragGestures(onDragEnd = { knobPosition = Offset.Zero; onMoved(0f, 0f) }, onDrag = { change, dragAmount -> 
                change.consume()
                val radius = containerSize.width / 2f
                if (radius > 0) {
                    val newPos = knobPosition + dragAmount; val distance = sqrt(newPos.x * newPos.x + newPos.y * newPos.y); val maxDist = radius * 0.75f 
                    knobPosition = if (distance > maxDist) { val angle = atan2(newPos.y, newPos.x); Offset(cos(angle) * maxDist, sin(angle) * maxDist) } else newPos
                    onMoved(knobPosition.x / maxDist, knobPosition.y / maxDist)
                }
            })
        })
    }
}

@Composable
fun EditorGridBackground(isLight: Boolean) {
    androidx.compose.foundation.Canvas(androidx.compose.ui.Modifier.fillMaxSize()) {
        val lineColor = if(isLight) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.12f)
        val step = 40.dp.toPx()
        
        for (x in 0..size.width.toInt() step step.toInt()) {
            drawLine(lineColor, Offset(x.toFloat(), 0f), Offset(x.toFloat(), size.height), strokeWidth = 1f)
        }
        for (y in 0..size.height.toInt() step step.toInt()) {
            drawLine(lineColor, Offset(0f, y.toFloat()), Offset(size.width, y.toFloat()), strokeWidth = 1f)
        }
    }
}

@Composable
fun GlobalToast(
    message: String?,
    onDismiss: () -> Unit
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = message != null,
        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically(initialOffsetY = { -it }),
        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically(targetOffsetY = { -it })
    ) {
        val currentMessage = message ?: return@AnimatedVisibility
        
        LaunchedEffect(currentMessage) {
            kotlinx.coroutines.delay(3000)
            onDismiss()
        }
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 40.dp) // Slightly higher for premium feel
                .zIndex(2000f),
            contentAlignment = Alignment.TopCenter
        ) {
              Box(
                  modifier = Modifier
                      .padding(horizontal = 32.dp)
                      .shadow(12.dp, RoundedCornerShape(24.dp))
                      .background(
                          Brush.horizontalGradient(listOf(Color(0xFF29B6F6), Color(0xFF039BE5))), 
                          RoundedCornerShape(24.dp)
                      ) // Light Blue Gradient
                      .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                      .padding(horizontal = 24.dp, vertical = 12.dp)
              ) {
                   Row(
                       verticalAlignment = Alignment.CenterVertically, 
                       horizontalArrangement = Arrangement.spacedBy(10.dp),
                       modifier = Modifier.widthIn(max = 400.dp) // Prevent too wide on tablets
                   ) {
                       Icon(
                           androidx.compose.material.icons.Icons.Rounded.Info, 
                           null, 
                           tint = Color.White, 
                           modifier = Modifier.size(22.dp)
                       )
                       Text(
                           text = currentMessage, 
                           color = Color.White, 
                           fontWeight = FontWeight.SemiBold,
                           fontSize = 14.sp,
                           textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                           lineHeight = 18.sp
                       )
                   }
              }
        }
    }
}
