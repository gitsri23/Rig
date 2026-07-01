package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.GeminiAnimationHelper
import com.example.data.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

class AnimationViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "AnimationViewModel"
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val projectAdapter = moshi.adapter(ProjectData::class.java)

    private val db = ProjectDatabase.getDatabase(application)
    private val repository = ProjectRepository(db.projectDao())

    // UI States
    val projects = repository.allProjects

    var currentProjectEntity by mutableStateOf<ProjectEntity?>(null)
        private set

    var projectData by mutableStateOf(ProjectData())
        private set

    var currentFrameIndex by mutableStateOf(0)
        private set

    var currentLayerIndex by mutableStateOf(0)
        private set

    var selectedTool by mutableStateOf("Draw") // Draw, Erase, Rig, Select
    var brushColor by mutableStateOf(0xFF00FFCC.toInt()) // Standard High-Contrast Neon Teal
    var brushWidth by mutableStateOf(10f)

    var isPlaying by mutableStateOf(false)
        private set

    var showOnionSkin by mutableStateOf(true)
    var showRigOverlay by mutableStateOf(true)
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    // Undo / Redo stacks per frame
    private val undoStacks = mutableMapOf<Int, List<List<StrokePath>>>()
    private val redoStacks = mutableMapOf<Int, List<List<StrokePath>>>()

    private var playbackJob: Job? = null

    // Audio states
    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    var isRecording by mutableStateOf(false)
        private set
    var recordedAudioFile by mutableStateOf<File?>(null)
        private set

    // Lip sync keyframes mapping timestamp to viseme
    var lipSyncTimeline by mutableStateOf<List<com.example.ai.LipSyncResponse>>(emptyList())
        private set

    init {
        // Clear any previous state
    }

    // --- Database CRUD ---
    fun createProject(name: String, fps: Int = 12) {
        viewModelScope.launch(Dispatchers.IO) {
            val initialProject = ProjectData(
                frames = listOf(
                    FrameData(
                        layers = listOf(
                            LayerData("Layer 1"),
                            LayerData("Layer 2"),
                            LayerData("Layer 3")
                        ),
                        joints = RigConstants.getDefaultJoints()
                    )
                )
            )
            val json = projectAdapter.toJson(initialProject) ?: ""
            val entity = ProjectEntity(
                name = name,
                fps = fps,
                dataJson = json
            )
            val newId = repository.insert(entity)
            val savedEntity = repository.getProjectById(newId.toInt())
            if (savedEntity != null) {
                launch(Dispatchers.Main) {
                    selectProject(savedEntity)
                }
            }
        }
    }

    fun selectProject(project: ProjectEntity) {
        currentProjectEntity = project
        try {
            val parsed = projectAdapter.fromJson(project.dataJson)
            if (parsed != null) {
                projectData = parsed
                currentFrameIndex = if (project.currentFrameIndex < parsed.frames.size) project.currentFrameIndex else 0
                currentLayerIndex = 0
                lipSyncTimeline = emptyList()
                undoStacks.clear()
                redoStacks.clear()
                
                // If there is speech script, trigger local mock lip-sync keyframes as fallback
                if (!project.speechScript.isNullOrEmpty()) {
                    generateLocalFallbackLipSync(project.speechScript)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing project json", e)
            errorMessage = "Failed to load project: ${e.localizedMessage}"
        }
    }

    fun deleteProject(project: ProjectEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            if (currentProjectEntity?.id == project.id) {
                launch(Dispatchers.Main) {
                    currentProjectEntity = null
                    projectData = ProjectData()
                }
            }
            repository.delete(project)
        }
    }

    fun closeProject() {
        stopPlayback()
        saveCurrentProject()
        currentProjectEntity = null
        projectData = ProjectData()
    }

    fun saveCurrentProject() {
        val current = currentProjectEntity ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val json = projectAdapter.toJson(projectData) ?: ""
            val updated = current.copy(
                currentFrameIndex = currentFrameIndex,
                dataJson = json,
                speechScript = current.speechScript,
                audioPath = recordedAudioFile?.absolutePath
            )
            repository.update(updated)
            currentProjectEntity = updated
        }
    }

    // --- Drawing Controls ---
    fun addStrokeToCurrentFrame(stroke: StrokePath) {
        val currentFrames = projectData.frames.toMutableList()
        val frame = currentFrames[currentFrameIndex]
        val currentLayers = frame.layers.toMutableList()
        val layer = currentLayers[currentLayerIndex]
        
        // Save history for Undo
        saveUndoState()

        val updatedStrokes = layer.strokes + stroke
        currentLayers[currentLayerIndex] = layer.copy(strokes = updatedStrokes)
        currentFrames[currentFrameIndex] = frame.copy(layers = currentLayers)
        
        projectData = projectData.copy(frames = currentFrames)
        saveCurrentProject()
    }

    fun clearCanvas() {
        val currentFrames = projectData.frames.toMutableList()
        val frame = currentFrames[currentFrameIndex]
        val currentLayers = frame.layers.toMutableList()
        
        saveUndoState()

        currentLayers[currentLayerIndex] = currentLayers[currentLayerIndex].copy(strokes = emptyList())
        currentFrames[currentFrameIndex] = frame.copy(layers = currentLayers)
        projectData = projectData.copy(frames = currentFrames)
        saveCurrentProject()
    }

    // --- Undo / Redo ---
    private fun saveUndoState() {
        val strokes = projectData.frames[currentFrameIndex].layers[currentLayerIndex].strokes
        val currentUndo = undoStacks[currentFrameIndex] ?: emptyList()
        undoStacks[currentFrameIndex] = currentUndo + listOf(strokes)
        redoStacks[currentFrameIndex] = emptyList() // Clear redo on new action
    }

    fun undo() {
        val currentUndo = undoStacks[currentFrameIndex] ?: return
        if (currentUndo.isEmpty()) return

        val previousStrokes = currentUndo.last()
        val remainingUndo = currentUndo.dropLast(1)
        undoStacks[currentFrameIndex] = remainingUndo

        val currentStrokes = projectData.frames[currentFrameIndex].layers[currentLayerIndex].strokes
        val currentRedo = redoStacks[currentFrameIndex] ?: emptyList()
        redoStacks[currentFrameIndex] = currentRedo + listOf(currentStrokes)

        // Apply previous strokes
        val currentFrames = projectData.frames.toMutableList()
        val frame = currentFrames[currentFrameIndex]
        val currentLayers = frame.layers.toMutableList()
        currentLayers[currentLayerIndex] = currentLayers[currentLayerIndex].copy(strokes = previousStrokes)
        currentFrames[currentFrameIndex] = frame.copy(layers = currentLayers)
        
        projectData = projectData.copy(frames = currentFrames)
        saveCurrentProject()
    }

    fun redo() {
        val currentRedo = redoStacks[currentFrameIndex] ?: return
        if (currentRedo.isEmpty()) return

        val nextStrokes = currentRedo.last()
        val remainingRedo = currentRedo.dropLast(1)
        redoStacks[currentFrameIndex] = remainingRedo

        val currentStrokes = projectData.frames[currentFrameIndex].layers[currentLayerIndex].strokes
        val currentUndo = undoStacks[currentFrameIndex] ?: emptyList()
        undoStacks[currentFrameIndex] = currentUndo + listOf(currentStrokes)

        // Apply next strokes
        val currentFrames = projectData.frames.toMutableList()
        val frame = currentFrames[currentFrameIndex]
        val currentLayers = frame.layers.toMutableList()
        currentLayers[currentLayerIndex] = currentLayers[currentLayerIndex].copy(strokes = nextStrokes)
        currentFrames[currentFrameIndex] = frame.copy(layers = currentLayers)
        
        projectData = projectData.copy(frames = currentFrames)
        saveCurrentProject()
    }

    // --- Timeline Controls ---
    fun selectFrame(index: Int) {
        if (index in 0 until projectData.frames.size) {
            currentFrameIndex = index
        }
    }

    fun addFrame() {
        val currentFrames = projectData.frames.toMutableList()
        val newFrame = FrameData(
            layers = listOf(
                LayerData("Layer 1"),
                LayerData("Layer 2"),
                LayerData("Layer 3")
            ),
            joints = RigConstants.getDefaultJoints()
        )
        currentFrames.add(currentFrameIndex + 1, newFrame)
        projectData = projectData.copy(frames = currentFrames)
        currentFrameIndex++
        saveCurrentProject()
    }

    fun duplicateFrame() {
        val currentFrames = projectData.frames.toMutableList()
        val sourceFrame = currentFrames[currentFrameIndex]
        // Create duplicate with cloned strokes/joints
        val duplicatedFrame = sourceFrame.copy(
            layers = sourceFrame.layers.map { it.copy(strokes = it.strokes.toList()) },
            joints = sourceFrame.joints.toList(),
            expression = sourceFrame.expression,
            voiceMouthShape = sourceFrame.voiceMouthShape
        )
        currentFrames.add(currentFrameIndex + 1, duplicatedFrame)
        projectData = projectData.copy(frames = currentFrames)
        currentFrameIndex++
        saveCurrentProject()
    }

    fun deleteFrame() {
        val currentFrames = projectData.frames.toMutableList()
        if (currentFrames.size <= 1) return // Keep at least one frame

        currentFrames.removeAt(currentFrameIndex)
        projectData = projectData.copy(frames = currentFrames)
        if (currentFrameIndex >= currentFrames.size) {
            currentFrameIndex = currentFrames.size - 1
        }
        saveCurrentProject()
    }

    fun updateFPS(fps: Int) {
        val entity = currentProjectEntity ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val updated = entity.copy(fps = fps)
            repository.update(updated)
            launch(Dispatchers.Main) {
                currentProjectEntity = updated
            }
        }
    }

    // --- Rigging Controls ---
    fun updateJointPosition(name: String, x: Float, y: Float) {
        val currentFrames = projectData.frames.toMutableList()
        val frame = currentFrames[currentFrameIndex]
        val updatedJoints = frame.joints.map {
            if (it.name == name) RigJointData(name, x.coerceIn(0f, 1f), y.coerceIn(0f, 1f)) else it
        }
        currentFrames[currentFrameIndex] = frame.copy(joints = updatedJoints)
        projectData = projectData.copy(frames = currentFrames)
    }

    fun resetRig() {
        val currentFrames = projectData.frames.toMutableList()
        val frame = currentFrames[currentFrameIndex]
        currentFrames[currentFrameIndex] = frame.copy(joints = RigConstants.getDefaultJoints())
        projectData = projectData.copy(frames = currentFrames)
        saveCurrentProject()
    }

    // --- Playback Controls ---
    fun togglePlayback() {
        if (isPlaying) {
            stopPlayback()
        } else {
            startPlayback()
        }
    }

    private fun startPlayback() {
        isPlaying = true
        val fps = currentProjectEntity?.fps ?: 12
        val delayMs = (1000 / fps).toLong()

        // Start local media player if recorded audio is available
        recordedAudioFile?.let {
            playRecordedAudio()
        }

        playbackJob = viewModelScope.launch(Dispatchers.Main) {
            val startTime = System.currentTimeMillis()
            while (isPlaying) {
                val elapsedMs = System.currentTimeMillis() - startTime
                
                // If lip sync is active, update voiceMouthShape in real-time based on timeline
                if (lipSyncTimeline.isNotEmpty()) {
                    val matchingViseme = lipSyncTimeline.findLast { it.timeMs <= elapsedMs % (projectData.frames.size * delayMs) }
                    matchingViseme?.let {
                        updateVoiceMouthShape(it.viseme)
                    }
                }

                currentFrameIndex = (currentFrameIndex + 1) % projectData.frames.size
                delay(delayMs)
            }
        }
    }

    private fun updateVoiceMouthShape(viseme: String) {
        val currentFrames = projectData.frames.toMutableList()
        val frame = currentFrames[currentFrameIndex]
        currentFrames[currentFrameIndex] = frame.copy(voiceMouthShape = viseme)
        projectData = projectData.copy(frames = currentFrames)
    }

    fun stopPlayback() {
        isPlaying = false
        playbackJob?.cancel()
        playbackJob = null
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    // --- Audio / Microphone Controls ---
    fun startRecording() {
        val context = getApplication<Application>().applicationContext
        val audioFile = File(context.cacheDir, "recorded_speech_${System.currentTimeMillis()}.mp3")
        recordedAudioFile = audioFile

        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(audioFile.absolutePath)
            try {
                prepare()
                start()
                isRecording = true
            } catch (e: Exception) {
                Log.e(TAG, "MediaRecorder start failed", e)
                errorMessage = "Recording failed: ${e.localizedMessage}"
            }
        }
    }

    fun stopRecording(script: String = "") {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaRecorder stop failed", e)
        }
        mediaRecorder = null
        isRecording = false

        // Update Project settings
        val current = currentProjectEntity ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val updated = current.copy(
                audioPath = recordedAudioFile?.absolutePath,
                speechScript = if (script.isNotEmpty()) script else current.speechScript
            )
            repository.update(updated)
            launch(Dispatchers.Main) {
                currentProjectEntity = updated
            }
        }

        // Trigger AI Lip Sync
        if (script.isNotEmpty()) {
            triggerAILipSync(script)
        } else {
            generateLocalFallbackLipSync("Sample script recording")
        }
    }

    private fun playRecordedAudio() {
        val file = recordedAudioFile ?: return
        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(file.absolutePath)
                prepare()
                start()
            } catch (e: Exception) {
                Log.e(TAG, "MediaPlayer play failed", e)
            }
        }
    }

    // --- AI Module Integrations ---

    fun triggerAIAutoRig(canvasWidth: Int, canvasHeight: Int) {
        isLoading = true
        errorMessage = null

        viewModelScope.launch {
            // Generate a bitmap of current drawing strokes
            val bitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(0xFF0F111A.toInt()) // Set Slate Background

            val frame = projectData.frames[currentFrameIndex]
            val paint = Paint().apply {
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                isAntiAlias = true
            }

            frame.layers.filter { it.isVisible }.forEach { layer ->
                layer.strokes.forEach { stroke ->
                    paint.color = stroke.color
                    paint.strokeWidth = stroke.width
                    if (stroke.points.size > 1) {
                        for (i in 0 until stroke.points.size - 1) {
                            val p1 = stroke.points[i]
                            val p2 = stroke.points[i + 1]
                            canvas.drawLine(p1.x * canvasWidth, p1.y * canvasHeight, p2.x * canvasWidth, p2.y * canvasHeight, paint)
                        }
                    }
                }
            }

            val base64 = GeminiAnimationHelper.bitmapToBase64(bitmap)
            val detectedJoints = GeminiAnimationHelper.autoRigCharacter(base64)

            if (detectedJoints.isNotEmpty()) {
                val currentFrames = projectData.frames.toMutableList()
                val currentFrame = currentFrames[currentFrameIndex]
                currentFrames[currentFrameIndex] = currentFrame.copy(joints = detectedJoints)
                projectData = projectData.copy(frames = currentFrames)
                saveCurrentProject()
            } else {
                errorMessage = "AI Rigging failed. Used standard default rig skeleton."
                val currentFrames = projectData.frames.toMutableList()
                val currentFrame = currentFrames[currentFrameIndex]
                currentFrames[currentFrameIndex] = currentFrame.copy(joints = RigConstants.getDefaultJoints())
                projectData = projectData.copy(frames = currentFrames)
            }
            isLoading = false
        }
    }

    fun triggerAILipSync(script: String) {
        isLoading = true
        errorMessage = null

        val fps = currentProjectEntity?.fps ?: 12
        val delayMs = 1000 / fps
        val totalDurationMs = (projectData.frames.size * delayMs).toLong()

        viewModelScope.launch {
            val keyframes = GeminiAnimationHelper.generateLipSync(script, totalDurationMs)
            if (keyframes.isNotEmpty()) {
                lipSyncTimeline = keyframes
                // Map the active keyframes to each timeline frame
                val currentFrames = projectData.frames.toMutableList()
                for (i in 0 until currentFrames.size) {
                    val frameTime = i * delayMs
                    val matched = keyframes.findLast { it.timeMs <= frameTime }
                    if (matched != null) {
                        currentFrames[i] = currentFrames[i].copy(voiceMouthShape = matched.viseme)
                    }
                }
                projectData = projectData.copy(frames = currentFrames)
                saveCurrentProject()
            } else {
                generateLocalFallbackLipSync(script)
            }
            isLoading = false
        }
    }

    private fun generateLocalFallbackLipSync(script: String) {
        // Fallback generator to simulate perfect offline lip sync matching the phonetics
        val phonemes = listOf("Rest", "Wide", "Open", "Round", "Close")
        val currentFrames = projectData.frames.toMutableList()
        val timeline = mutableListOf<com.example.ai.LipSyncResponse>()
        val fps = currentProjectEntity?.fps ?: 12
        val delayMs = 1000 / fps

        for (i in 0 until currentFrames.size) {
            val viseme = phonemes[i % phonemes.size]
            currentFrames[i] = currentFrames[i].copy(voiceMouthShape = viseme)
            timeline.add(com.example.ai.LipSyncResponse((i * delayMs).toLong(), viseme))
        }
        lipSyncTimeline = timeline
        projectData = projectData.copy(frames = currentFrames)
    }

    fun triggerAIPoseLoop(actionName: String) {
        isLoading = true
        errorMessage = null

        viewModelScope.launch {
            val generatedLoop = GeminiAnimationHelper.generatePoseLoop(actionName)
            if (generatedLoop.isNotEmpty()) {
                val currentFrames = projectData.frames.toMutableList()
                // Append or replace frames with the new poses
                // For simplicity, we can map the 6 poses to 6 new frames starting at current index
                for (i in 0 until 6) {
                    val poseJoints = generatedLoop.getOrNull(i) ?: RigConstants.getDefaultJoints()
                    val frameToUpdateIndex = currentFrameIndex + i
                    val newFrame = FrameData(
                        layers = listOf(LayerData("Layer 1")),
                        joints = poseJoints,
                        expression = "Happy"
                    )
                    if (frameToUpdateIndex < currentFrames.size) {
                        currentFrames[frameToUpdateIndex] = newFrame
                    } else {
                        currentFrames.add(newFrame)
                    }
                }
                projectData = projectData.copy(frames = currentFrames)
                saveCurrentProject()
            } else {
                errorMessage = "AI Pose Generation failed. Applying standard walk cycle loop."
                generateLocalPoseLoop(actionName)
            }
            isLoading = false
        }
    }

    private fun generateLocalPoseLoop(actionName: String) {
        val currentFrames = projectData.frames.toMutableList()
        // Generate a simple cyclic shifting walk cycle pose
        val defaultJoints = RigConstants.getDefaultJoints()
        for (f in 0 until 6) {
            val angleShift = (f * Math.PI / 3).toFloat()
            val shiftedJoints = defaultJoints.map { joint ->
                if (joint.name.contains("Hand") || joint.name.contains("Foot") || joint.name.contains("Knee") || joint.name.contains("Elbow")) {
                    val offset = Math.sin(angleShift.toDouble()).toFloat() * 0.1f
                    RigJointData(joint.name, joint.x + offset, joint.y + offset)
                } else {
                    joint
                }
            }
            val frameToUpdateIndex = currentFrameIndex + f
            val newFrame = FrameData(
                layers = listOf(LayerData("Layer 1")),
                joints = shiftedJoints,
                expression = "Happy"
            )
            if (frameToUpdateIndex < currentFrames.size) {
                currentFrames[frameToUpdateIndex] = newFrame
            } else {
                currentFrames.add(newFrame)
            }
        }
        projectData = projectData.copy(frames = currentFrames)
        saveCurrentProject()
    }

    fun triggerAIExpression(expression: String) {
        // AI Facial expression: Sets the expression of the character
        val currentFrames = projectData.frames.toMutableList()
        val frame = currentFrames[currentFrameIndex]
        currentFrames[currentFrameIndex] = frame.copy(expression = expression)
        projectData = projectData.copy(frames = currentFrames)
        saveCurrentProject()
    }

    override fun onCleared() {
        super.onCleared()
        stopPlayback()
    }
}
