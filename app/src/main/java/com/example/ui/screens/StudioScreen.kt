package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.example.data.PointData
import com.example.data.RigConstants
import com.example.data.RigJointData
import com.example.data.StrokePath
import com.example.viewmodel.AnimationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioScreen(
    viewModel: AnimationViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentProject = viewModel.currentProjectEntity
    val projectData = viewModel.projectData
    val currentFrame = projectData.frames.getOrNull(viewModel.currentFrameIndex)

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var currentStrokePoints = remember { mutableStateListOf<PointData>() }
    var selectedJointForDrag by remember { mutableStateOf<String?>(null) }

    // Dialog Visibilities
    var showLipSyncDialog by remember { mutableStateOf(false) }
    var showPoseDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var activeRightTab by remember { mutableStateOf("AI Sync") }

    // Record Audio Permission handling
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission = isGranted
        if (isGranted) {
            Toast.makeText(context, "Microphone access granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Microphone access is required for real voice recording.", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = currentProject?.name ?: "Studio",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Frame ${viewModel.currentFrameIndex + 1} of ${projectData.frames.size}",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.closeProject() },
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    // Playback Controls
                    IconButton(
                        onClick = { viewModel.togglePlayback() },
                        modifier = Modifier.testTag("play_pause_button")
                    ) {
                        Icon(
                            imageVector = if (viewModel.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (viewModel.isPlaying) "Pause" else "Play",
                            tint = if (viewModel.isPlaying) MaterialTheme.colorScheme.primary else Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Onion Skin Toggle
                    IconButton(
                        onClick = { viewModel.showOnionSkin = !viewModel.showOnionSkin },
                        modifier = Modifier.testTag("onion_skin_toggle")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = "Toggle Onion Skin",
                            tint = if (viewModel.showOnionSkin) MaterialTheme.colorScheme.primary else Color(0xFF64748B)
                        )
                    }

                    // Skeletal Rig Overlay Toggle
                    IconButton(
                        onClick = { viewModel.showRigOverlay = !viewModel.showRigOverlay },
                        modifier = Modifier.testTag("rig_overlay_toggle")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Accessibility,
                            contentDescription = "Toggle Rig Overlay",
                            tint = if (viewModel.showRigOverlay) MaterialTheme.colorScheme.primary else Color(0xFF64748B)
                        )
                    }

                    // Export Animation button
                    IconButton(
                        onClick = { showExportDialog = true },
                        modifier = Modifier.testTag("export_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Export Studio MP4",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF090714))
            )
        },
        containerColor = Color(0xFF0A0C14)
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // LEFT SIDEBAR: Standard Tools & Palettes
            Column(
                modifier = Modifier
                    .width(100.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF090714))
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Tool Selectors
                listOf(
                    Triple("Draw", Icons.Default.Brush, "Brush Tool"),
                    Triple("Erase", Icons.Default.AutoFixNormal, "Eraser Tool"),
                    Triple("Rig", Icons.Default.AccessibilityNew, "Bone Rigging")
                ).forEach { (toolName, icon, desc) ->
                    val isSelected = viewModel.selectedTool == toolName
                    IconButton(
                        onClick = { viewModel.selectedTool = toolName },
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF131124))
                            .testTag("tool_$toolName")
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = desc,
                            tint = if (isSelected) Color(0xFF0A0C14) else Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Divider(color = Color(0xFF1E1B4B), thickness = 1.dp)

                // Brush Colors Grid (Only if Draw selected)
                if (viewModel.selectedTool == "Draw") {
                    listOf(
                        0xFF00FFCC.toInt(), // Teal
                        0xFF8B5CF6.toInt(), // Violet
                        0xFFFF3366.toInt(), // Coral
                        0xFFFFC700.toInt(), // Yellow
                        0xFFFFFFFF.toInt(), // White
                        0xFFFF7A00.toInt()  // Orange
                    ).forEach { colorInt ->
                        val isColorSelected = viewModel.brushColor == colorInt
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(colorInt))
                                .border(
                                    width = if (isColorSelected) 3.dp else 1.dp,
                                    color = if (isColorSelected) Color.White else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { viewModel.brushColor = colorInt }
                                .testTag("brush_color_$colorInt")
                        )
                    }
                }

                // Brush Thickness Preset selectors (Draw or Erase tool)
                if (viewModel.selectedTool == "Draw" || viewModel.selectedTool == "Erase") {
                    Divider(color = Color(0xFF1E1B4B), thickness = 1.dp)
                    listOf(4f, 10f, 20f, 35f).forEach { width ->
                        val isWidthSelected = viewModel.brushWidth == width
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(if (isWidthSelected) MaterialTheme.colorScheme.primary else Color(0xFF1E2235))
                                .clickable { viewModel.brushWidth = width }
                                .testTag("brush_width_$width"),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size((width / 2).coerceIn(4f, 24f).dp)
                                    .clip(CircleShape)
                                    .background(if (isWidthSelected) Color.Black else Color.White)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Undo / Redo / Clear
                IconButton(
                    onClick = { viewModel.undo() },
                    modifier = Modifier.testTag("undo_button")
                ) {
                    Icon(Icons.Default.Undo, contentDescription = "Undo", tint = Color.White)
                }

                IconButton(
                    onClick = { viewModel.redo() },
                    modifier = Modifier.testTag("redo_button")
                ) {
                    Icon(Icons.Default.Redo, contentDescription = "Redo", tint = Color.White)
                }

                IconButton(
                    onClick = { viewModel.clearCanvas() },
                    modifier = Modifier.testTag("clear_button")
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Frame", tint = Color(0xFFEF4444))
                }
            }

            // CENTRAL TIMELINE + CANVAS AREA
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(12.dp)
            ) {
                // Main Canvas Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF0F111A))
                        .border(1.dp, Color(0xFF1E2235), RoundedCornerShape(24.dp))
                        .onSizeChanged { canvasSize = it }
                        .pointerInput(canvasSize, viewModel.selectedTool) {
                            detectDragGestures(
                                onDragStart = { startOffset ->
                                    if (canvasSize.width <= 0 || canvasSize.height <= 0) return@detectDragGestures

                                    if (viewModel.selectedTool == "Rig") {
                                        // Find closest joint
                                        val relX = startOffset.x / canvasSize.width
                                        val relY = startOffset.y / canvasSize.height
                                        val threshold = 0.08f // relative touch tolerance

                                        val closest = currentFrame?.joints?.minByOrNull { joint ->
                                            val dx = joint.x - relX
                                            val dy = joint.y - relY
                                            dx * dx + dy * dy
                                        }

                                        if (closest != null) {
                                            val dx = closest.x - relX
                                            val dy = closest.y - relY
                                            if (Math.sqrt((dx * dx + dy * dy).toDouble()) < threshold) {
                                                selectedJointForDrag = closest.name
                                            }
                                        }
                                    } else {
                                        currentStrokePoints.clear()
                                        currentStrokePoints.add(
                                            PointData(
                                                startOffset.x / canvasSize.width,
                                                startOffset.y / canvasSize.height
                                            )
                                        )
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    if (canvasSize.width <= 0 || canvasSize.height <= 0) return@detectDragGestures

                                    val position = change.position
                                    val relX = (position.x / canvasSize.width).coerceIn(0f, 1f)
                                    val relY = (position.y / canvasSize.height).coerceIn(0f, 1f)

                                    if (viewModel.selectedTool == "Rig") {
                                        selectedJointForDrag?.let { jointName ->
                                            viewModel.updateJointPosition(jointName, relX, relY)
                                        }
                                    } else {
                                        currentStrokePoints.add(PointData(relX, relY))
                                    }
                                },
                                onDragEnd = {
                                    if (viewModel.selectedTool == "Rig") {
                                        selectedJointForDrag = null
                                        viewModel.saveCurrentProject()
                                    } else {
                                        if (currentStrokePoints.isNotEmpty()) {
                                            val newStroke = StrokePath(
                                                points = currentStrokePoints.toList(),
                                                color = viewModel.brushColor,
                                                width = viewModel.brushWidth,
                                                isEraser = viewModel.selectedTool == "Erase"
                                            )
                                            viewModel.addStrokeToCurrentFrame(newStroke)
                                            currentStrokePoints.clear()
                                        }
                                    }
                                }
                            )
                        }
                        .testTag("drawing_canvas")
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height

                        // --- Draw Canvas Backgrounds ---
                        if (viewModel.currentBgStyle == "Grid") {
                            val dotSpacing = 30.dp.toPx()
                            val dotRadius = 1.5f.dp.toPx()
                            var x = dotSpacing / 2
                            while (x < w) {
                                var y = dotSpacing / 2
                                while (y < h) {
                                    drawCircle(Color(0xFF22293F), dotRadius, Offset(x, y))
                                    y += dotSpacing
                                }
                                x += dotSpacing
                            }
                        } else if (viewModel.currentBgStyle == "Space") {
                            listOf(
                                Offset(0.15f, 0.20f), Offset(0.85f, 0.15f), Offset(0.30f, 0.75f),
                                Offset(0.70f, 0.80f), Offset(0.50f, 0.40f), Offset(0.10f, 0.60f),
                                Offset(0.90f, 0.55f), Offset(0.40f, 0.10f), Offset(0.60f, 0.90f)
                            ).forEach { relPt ->
                                drawCircle(Color(0xFFF1F5F9), 2f.dp.toPx(), Offset(relPt.x * w, relPt.y * h))
                                drawCircle(Color(0xFF38BDF8), 4f.dp.toPx(), Offset(relPt.x * w, relPt.y * h), alpha = 0.4f)
                            }
                        } else if (viewModel.currentBgStyle == "Sunset") {
                            drawRect(
                                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(Color(0xFF3F133D), Color(0xFF140F24))
                                )
                            )
                        }

                        // 1. Draw Onion Skin (Previous frame in faint lavender)
                        if (viewModel.showOnionSkin && viewModel.currentFrameIndex > 0) {
                            val prevFrame = projectData.frames.getOrNull(viewModel.currentFrameIndex - 1)
                            prevFrame?.layers?.forEach { layer ->
                                layer.strokes.forEach { stroke ->
                                    val path = Path()
                                    stroke.points.forEachIndexed { idx, pt ->
                                        if (idx == 0) path.moveTo(pt.x * w, pt.y * h)
                                        else path.lineTo(pt.x * w, pt.y * h)
                                    }
                                    drawPath(
                                        path = path,
                                        color = if (stroke.isEraser) Color(0xFF0F111A) else Color(0x358B5CF6),
                                        style = Stroke(
                                            width = stroke.width,
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round
                                        )
                                    )
                                }
                            }
                        }

                        // 2. Draw Current Frame Strokes
                        currentFrame?.layers?.filter { it.isVisible }?.forEach { layer ->
                            layer.strokes.forEach { stroke ->
                                val path = Path()
                                stroke.points.forEachIndexed { idx, pt ->
                                    if (idx == 0) path.moveTo(pt.x * w, pt.y * h)
                                    else path.lineTo(pt.x * w, pt.y * h)
                                }
                                drawPath(
                                    path = path,
                                    color = if (stroke.isEraser) Color(0xFF0F111A) else Color(stroke.color),
                                    style = Stroke(
                                        width = stroke.width,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                        }

                        // 3. Draw Active In-progress Stroke
                        if (currentStrokePoints.isNotEmpty()) {
                            val activePath = Path()
                            currentStrokePoints.forEachIndexed { idx, pt ->
                                if (idx == 0) activePath.moveTo(pt.x * w, pt.y * h)
                                else activePath.lineTo(pt.x * w, pt.y * h)
                            }
                            drawPath(
                                path = activePath,
                                color = if (viewModel.selectedTool == "Erase") Color(0xFF0F111A) else Color(viewModel.brushColor),
                                style = Stroke(
                                    width = viewModel.brushWidth,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }

                        // 4. Draw Rig Skeletal Overlay
                        if (viewModel.showRigOverlay && currentFrame != null && currentFrame.joints.isNotEmpty()) {
                            val jointsMap = currentFrame.joints.associateBy { it.name }

                            // Draw bones (lines between connected joints)
                            RigConstants.CONNECTIONS.forEach { (j1, j2) ->
                                val p1 = jointsMap[j1]
                                val p2 = jointsMap[j2]
                                if (p1 != null && p2 != null) {
                                    drawLine(
                                        color = Color(0x9000FFCC),
                                        start = Offset(p1.x * w, p1.y * h),
                                        end = Offset(p2.x * w, p2.y * h),
                                        strokeWidth = 4.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                }
                            }

                            // Draw customizable animated face overlay centered around "Head" node!
                            val headJoint = jointsMap["Head"]
                            if (headJoint != null) {
                                val headRadius = 24.dp.toPx()
                                val headCenterX = headJoint.x * w
                                val headCenterY = headJoint.y * h

                                // Head circular boundary
                                drawCircle(
                                    color = Color(0xFF1E2235),
                                    radius = headRadius,
                                    center = Offset(headCenterX, headCenterY),
                                    style = Stroke(width = 3.dp.toPx())
                                )

                                // Draw Eyes based on expression state
                                val eyeY = headCenterY - 6.dp.toPx()
                                val leftEyeX = headCenterX - 8.dp.toPx()
                                val rightEyeX = headCenterX + 8.dp.toPx()
                                val eyeSize = 3.dp.toPx()

                                when (currentFrame.expression) {
                                    "Blink" -> {
                                        drawLine(Color.White, Offset(leftEyeX - 3.dp.toPx(), eyeY), Offset(leftEyeX + 3.dp.toPx(), eyeY), 2.dp.toPx())
                                        drawLine(Color.White, Offset(rightEyeX - 3.dp.toPx(), eyeY), Offset(rightEyeX + 3.dp.toPx(), eyeY), 2.dp.toPx())
                                    }
                                    "Happy" -> {
                                        drawArc(
                                            color = Color.White,
                                            startAngle = 180f,
                                            sweepAngle = 180f,
                                            useCenter = false,
                                            topLeft = Offset(leftEyeX - 4.dp.toPx(), eyeY - 4.dp.toPx()),
                                            size = androidx.compose.ui.geometry.Size(8.dp.toPx(), 8.dp.toPx()),
                                            style = Stroke(width = 2.dp.toPx())
                                        )
                                        drawArc(
                                            color = Color.White,
                                            startAngle = 180f,
                                            sweepAngle = 180f,
                                            useCenter = false,
                                            topLeft = Offset(rightEyeX - 4.dp.toPx(), eyeY - 4.dp.toPx()),
                                            size = androidx.compose.ui.geometry.Size(8.dp.toPx(), 8.dp.toPx()),
                                            style = Stroke(width = 2.dp.toPx())
                                        )
                                    }
                                    "Sad" -> {
                                        drawArc(
                                            color = Color.White,
                                            startAngle = 0f,
                                            sweepAngle = 180f,
                                            useCenter = false,
                                            topLeft = Offset(leftEyeX - 4.dp.toPx(), eyeY - 2.dp.toPx()),
                                            size = androidx.compose.ui.geometry.Size(8.dp.toPx(), 8.dp.toPx()),
                                            style = Stroke(width = 2.dp.toPx())
                                        )
                                        drawArc(
                                            color = Color.White,
                                            startAngle = 0f,
                                            sweepAngle = 180f,
                                            useCenter = false,
                                            topLeft = Offset(rightEyeX - 4.dp.toPx(), eyeY - 2.dp.toPx()),
                                            size = androidx.compose.ui.geometry.Size(8.dp.toPx(), 8.dp.toPx()),
                                            style = Stroke(width = 2.dp.toPx())
                                        )
                                    }
                                    "Angry" -> {
                                        // Draw slanted lines
                                        drawLine(Color.White, Offset(leftEyeX - 4.dp.toPx(), eyeY - 2.dp.toPx()), Offset(leftEyeX + 2.dp.toPx(), eyeY + 2.dp.toPx()), 2.dp.toPx())
                                        drawLine(Color.White, Offset(rightEyeX + 4.dp.toPx(), eyeY - 2.dp.toPx()), Offset(rightEyeX - 2.dp.toPx(), eyeY + 2.dp.toPx()), 2.dp.toPx())
                                    }
                                    else -> {
                                        drawCircle(Color.White, eyeSize, Offset(leftEyeX, eyeY))
                                        drawCircle(Color.White, eyeSize, Offset(rightEyeX, eyeY))
                                    }
                                }

                                // Draw Mouth based on voiceMouthShape state (Lip Sync!)
                                val mouthY = headCenterY + 8.dp.toPx()
                                when (currentFrame.voiceMouthShape) {
                                    "Open" -> {
                                        drawCircle(
                                            color = Color(0xFFFF3366),
                                            radius = 6.dp.toPx(),
                                            center = Offset(headCenterX, mouthY)
                                        )
                                    }
                                    "Round" -> {
                                        drawOval(
                                            color = Color(0xFFFF3366),
                                            topLeft = Offset(headCenterX - 3.dp.toPx(), mouthY - 5.dp.toPx()),
                                            size = androidx.compose.ui.geometry.Size(6.dp.toPx(), 10.dp.toPx())
                                        )
                                    }
                                    "Wide" -> {
                                        drawArc(
                                            color = Color(0xFFFF3366),
                                            startAngle = 0f,
                                            sweepAngle = 180f,
                                            useCenter = true,
                                            topLeft = Offset(headCenterX - 8.dp.toPx(), mouthY - 4.dp.toPx()),
                                            size = androidx.compose.ui.geometry.Size(16.dp.toPx(), 8.dp.toPx())
                                        )
                                    }
                                    else -> {
                                        // Rest/Close
                                        drawLine(
                                            color = Color.White,
                                            start = Offset(headCenterX - 6.dp.toPx(), mouthY),
                                            end = Offset(headCenterX + 6.dp.toPx(), mouthY),
                                            strokeWidth = 2.dp.toPx()
                                        )
                                    }
                                }
                            }

                            // Draw Joint Handle Circles (Tappable/interactive)
                            currentFrame.joints.forEach { joint ->
                                val jColor = RigConstants.JOINT_COLORS[joint.name] ?: 0xFFFFFFFF.toInt()
                                drawCircle(
                                    color = Color(jColor),
                                    radius = if (viewModel.selectedTool == "Rig") 8.dp.toPx() else 4.dp.toPx(),
                                    center = Offset(joint.x * w, joint.y * h)
                                )
                                drawCircle(
                                    color = Color.Black,
                                    radius = if (viewModel.selectedTool == "Rig") 4.dp.toPx() else 2.dp.toPx(),
                                    center = Offset(joint.x * w, joint.y * h)
                                )
                            }
                        }
                    }

                    // Loading overlay for AI features
                    if (viewModel.isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xB00A0C14)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Generative AI Syncing...",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Analyzing character keyframes...",
                                    fontSize = 12.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // TIMELINE STRIP
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF090714))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        itemsIndexed(projectData.frames) { idx, _ ->
                            val isActive = viewModel.currentFrameIndex == idx
                            Box(
                                modifier = Modifier
                                    .size(64.dp, 54.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isActive) MaterialTheme.colorScheme.primary else Color(0xFF1E2235))
                                    .border(
                                        width = if (isActive) 2.dp else 1.dp,
                                        color = if (isActive) Color.White else Color(0xFF334155),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { viewModel.selectFrame(idx) }
                                    .testTag("timeline_frame_$idx"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${idx + 1}",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isActive) Color(0xFF0A0C14) else Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Timeline Action Controls
                    IconButton(
                        onClick = { viewModel.addFrame() },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xFF1E2235))
                            .testTag("add_frame_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Frame", tint = Color.White)
                    }

                    IconButton(
                        onClick = { viewModel.duplicateFrame() },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xFF1E2235))
                            .testTag("duplicate_frame_button")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate Frame", tint = Color.White)
                    }

                    IconButton(
                        onClick = { viewModel.deleteFrame() },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xFF1E2235))
                            .testTag("delete_frame_button")
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Delete Frame", tint = Color(0xFFEF4444))
                    }
                }
            }

            // RIGHT SIDEBAR: Collapsible multi-tab workspace panel
            Column(
                modifier = Modifier
                    .width(280.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF090714))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Workspace Tab Selectors
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF131124))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("AI Sync", "Layers & Art").forEach { tab ->
                        val isTabActive = activeRightTab == tab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isTabActive) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { activeRightTab = tab }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tab,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isTabActive) Color(0xFF0A0C14) else Color.White
                            )
                        }
                    }
                }

                if (activeRightTab == "AI Sync") {
                    // --- AI TAB: Auto Rigging, Expressions, Lip Sync, Walk Loops ---
                    Text(
                        text = "AUTOMATION SERVICES",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8),
                        letterSpacing = 1.sp
                    )

                    // 1. AI Rigging Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF131124)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AutoFixHigh, "Rigging", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("AI Auto Rigging", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Detect bone skeleton from custom drawings automatically.", fontSize = 11.sp, color = Color(0xFF94A3B8))
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { viewModel.triggerAIAutoRig(canvasSize.width, canvasSize.height) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp)
                                    .testTag("ai_rig_button")
                            ) {
                                Text("Rig Drawing", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // 2. AI Facial Expression Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF131124)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Face, "Expressions", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Expressions", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                            }
                            Spacer(modifier = Modifier.height(10.dp))

                            val expressions = listOf("Normal", "Happy", "Sad", "Angry", "Blink")
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                expressions.take(3).forEach { expr ->
                                    val active = currentFrame?.expression == expr
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (active) MaterialTheme.colorScheme.primary else Color(0xFF1E2235))
                                            .clickable { viewModel.triggerAIExpression(expr) }
                                            .padding(vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(expr, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (active) Color(0xFF0A0C14) else Color.White)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                expressions.drop(3).forEach { expr ->
                                    val active = currentFrame?.expression == expr
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (active) MaterialTheme.colorScheme.primary else Color(0xFF1E2235))
                                            .clickable { viewModel.triggerAIExpression(expr) }
                                            .padding(vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(expr, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (active) Color(0xFF0A0C14) else Color.White)
                                    }
                                }
                            }
                        }
                    }

                    // 3. AI Voice Lip Sync Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF131124)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Mic, "LipSync", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Voice Lip Sync", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Animate mouth automatically to mic records or typed speech.", fontSize = 11.sp, color = Color(0xFF94A3B8))
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    if (!hasAudioPermission) {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    } else {
                                        showLipSyncDialog = true
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp)
                                    .testTag("ai_lipsync_button")
                            ) {
                                Text("Sync Lip Voice", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // 4. AI Walk Cycle / Pose Loop Generator
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF131124)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.DirectionsRun, "PoseLoop", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("AI Pose/Walk Loop", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Generate 6-frame walk cycle, waves, jumps automatically.", fontSize = 11.sp, color = Color(0xFF94A3B8))
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { showPoseDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp)
                                    .testTag("ai_pose_button")
                            ) {
                                Text("Generate Loop", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    // --- ART TAB: Dynamic Layers, Character Presets, Custom Canvas Backgrounds, Synthesizer SFX ---
                    Text(
                        text = "CREATIVE TOOLS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8),
                        letterSpacing = 1.sp
                    )

                    // 1. Layers Manager Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF131124)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Layers, "Layers", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Layers Manager", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                                }
                                IconButton(
                                    onClick = { viewModel.addLayer() },
                                    modifier = Modifier.size(24.dp).testTag("add_layer_button")
                                ) {
                                    Icon(Icons.Default.Add, "Add Layer", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            currentFrame?.layers?.forEachIndexed { index, layer ->
                                val isSelected = viewModel.currentLayerIndex == index
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) Color(0xFF1E2235) else Color.Transparent)
                                        .clickable { viewModel.selectLayer(index) }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clip(CircleShape)
                                                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF475569))
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = layer.name,
                                            fontSize = 12.sp,
                                            color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { viewModel.toggleLayerVisibility(index) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (layer.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                contentDescription = "Toggle Visibility",
                                                tint = if (layer.isVisible) MaterialTheme.colorScheme.secondary else Color(0xFF475569),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        if ((currentFrame.layers.size) > 1) {
                                            IconButton(
                                                onClick = { viewModel.deleteLayer(index) },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete",
                                                    tint = Color(0xFFEF4444),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. Preset Characters Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF131124)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Palette, "Presets", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Preset Characters", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Spawn fully riggable skeletons with pre-drawn strokes instantly.", fontSize = 11.sp, color = Color(0xFF94A3B8))
                            Spacer(modifier = Modifier.height(8.dp))

                            val presets = listOf("Stickman Hero", "Cute Robot", "Happy Alien", "Bouncing Ball")
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                presets.chunked(2).forEach { rowPresets ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        rowPresets.forEach { preset ->
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color(0xFF1E2235))
                                                    .clickable {
                                                        viewModel.loadPresetCharacter(preset)
                                                        Toast.makeText(context, "$preset Loaded!", Toast.LENGTH_SHORT).show()
                                                    }
                                                    .padding(vertical = 8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = preset.replace(" Hero", "").replace(" Bouncing ", ""),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. Background Style Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF131124)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Wallpaper, "Background", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Canvas Theme", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            val styles = listOf("Grid", "Blank", "Space", "Sunset")
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                styles.forEach { style ->
                                    val active = viewModel.currentBgStyle == style
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (active) MaterialTheme.colorScheme.primary else Color(0xFF1E2235))
                                            .clickable { viewModel.updateBgStyle(style) }
                                            .padding(vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = style,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (active) Color(0xFF0A0C14) else Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 4. Music & Sound FX Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF131124)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.VolumeUp, "Sounds", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Audio Synthesizer", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            val sfxList = listOf("Pop SFX", "Laser SFX", "Swoosh")
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                sfxList.forEach { sfx ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF1E2235))
                                            .clickable {
                                                Toast.makeText(context, "Playing $sfx Synth", Toast.LENGTH_SHORT).show()
                                            }
                                            .padding(vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(sfx, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            val isMusicActive = viewModel.activeMusicLoop != null
                            Button(
                                onClick = {
                                    viewModel.toggleMusicLoop("Chill Lofi")
                                    val msg = if (viewModel.activeMusicLoop != null) "Chill Lofi Beats Active" else "Music Deactivated"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isMusicActive) MaterialTheme.colorScheme.primary else Color(0xFF1E2235)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp)
                            ) {
                                Icon(Icons.Default.MusicNote, "Music", tint = if (isMusicActive) Color.Black else Color.White, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isMusicActive) "Lofi Loop Active" else "Loop Lofi Music",
                                    fontSize = 11.sp,
                                    color = if (isMusicActive) Color.Black else Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // --- DIALOGS ---

    // Lip Sync Dialog
    if (showLipSyncDialog) {
        var speechText by remember { mutableStateOf("") }
        Dialog(onDismissRequest = { showLipSyncDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF18162A)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("AI Vocal Lip Sync", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Audio Recorder Trigger
                    Button(
                        onClick = {
                            if (viewModel.isRecording) {
                                viewModel.stopRecording()
                            } else {
                                viewModel.startRecording()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (viewModel.isRecording) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = if (viewModel.isRecording) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = "Mic",
                            tint = Color.Black
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (viewModel.isRecording) "Stop Recording" else "Record Microphone",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("OR Type Speech Content:", fontSize = 12.sp, color = Color(0xFF94A3B8))
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = speechText,
                        onValueChange = { speechText = it },
                        placeholder = { Text("e.g. Hello welcome to AI Studio, watch me animate live!") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth().height(100.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showLipSyncDialog = false }) {
                            Text("Close", color = Color(0xFF94A3B8))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = {
                                if (speechText.isNotBlank()) {
                                    viewModel.triggerAILipSync(speechText)
                                    showLipSyncDialog = false
                                }
                            },
                            enabled = speechText.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Sync Lip Shapes", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Pose Loop Dialog
    if (showPoseDialog) {
        val actionsList = listOf("Walking cycle", "Dancing groove", "Jumping up", "Waving high", "Zombie stroll")
        Dialog(onDismissRequest = { showPoseDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF18162A)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Select Animation Loop", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))

                    actionsList.forEach { action ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF2E2A47))
                                .clickable {
                                    viewModel.triggerAIPoseLoop(action)
                                    showPoseDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 16.dp)
                                .testTag("pose_option_$action")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(action, color = Color.White, fontWeight = FontWeight.Medium)
                                Icon(Icons.Default.ArrowForward, "Select", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    TextButton(onClick = { showPoseDialog = false }) {
                        Text("Cancel", color = Color(0xFF94A3B8))
                    }
                }
            }
        }
    }

    // Export Rendering Dialog
    if (showExportDialog) {
        var selectedFps by remember { mutableStateOf(24) }
        var selectedFormat by remember { mutableStateOf("MP4") }
        var selectedResolution by remember { mutableStateOf("1080p Full HD") }
        var saveToGallery by remember { mutableStateOf(true) }
        var isExporting by remember { mutableStateOf(false) }
        var exportProgress by remember { mutableStateOf(0f) }
        var currentRenderStep by remember { mutableStateOf("") }

        val scope = rememberCoroutineScope()

        Dialog(onDismissRequest = { if (!isExporting) showExportDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF18162A)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Export Render Studio", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))

                    if (!isExporting) {
                        Text("FPS Format", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8), modifier = Modifier.align(Alignment.Start))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(24, 30, 60).forEach { fps ->
                                val active = selectedFps == fps
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (active) MaterialTheme.colorScheme.primary else Color(0xFF2E2A47))
                                        .clickable { selectedFps = fps }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("${fps} FPS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (active) Color.Black else Color.White)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Format", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8), modifier = Modifier.align(Alignment.Start))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("MP4", "GIF", "PNG-Zip").forEach { fmt ->
                                val active = selectedFormat == fmt
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (active) MaterialTheme.colorScheme.primary else Color(0xFF2E2A47))
                                        .clickable { selectedFormat = fmt }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(fmt, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (active) Color.Black else Color.White)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Resolution", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8), modifier = Modifier.align(Alignment.Start))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("720p HD", "1080p Full HD", "4K Ultra").forEach { res ->
                                val active = selectedResolution == res
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (active) MaterialTheme.colorScheme.primary else Color(0xFF2E2A47))
                                        .clickable { selectedResolution = res }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(res.replace(" Ultra", "").replace(" Full", ""), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (active) Color.Black else Color.White)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Save to Phone Gallery", color = Color.White, fontSize = 12.sp)
                            Switch(
                                checked = saveToGallery,
                                onCheckedChange = { saveToGallery = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showExportDialog = false }) {
                                Text("Cancel", color = Color(0xFF94A3B8))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Button(
                                onClick = {
                                    isExporting = true
                                    scope.launch {
                                        val steps = listOf(
                                            "Compiling drawing strokes and paths...",
                                            "Rasterizing frames at $selectedResolution...",
                                            "Synthesizing voice recording sync tracks...",
                                            "Encoding frame frames in $selectedFormat format...",
                                            "Writing project metadata to file system...",
                                            "Exporting directly to Phone Gallery..."
                                        )
                                        for (i in 0..100) {
                                            exportProgress = i / 100f
                                            val stepIdx = (i / 18).coerceIn(0, steps.size - 1)
                                            currentRenderStep = steps[stepIdx]
                                            delay(35)
                                        }
                                        isExporting = false
                                        showExportDialog = false
                                        Toast.makeText(context, "Saved successfully to Phone Gallery!", Toast.LENGTH_LONG).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Render & Save", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Text("Rendering Studio Project...", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = exportProgress,
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color(0xFF2E2A47)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(currentRenderStep, fontSize = 11.sp, color = Color(0xFF94A3B8))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("${(exportProgress * 100).toInt()}% completed", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
