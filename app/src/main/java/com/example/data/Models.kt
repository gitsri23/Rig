package com.example.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PointData(
    val x: Float,
    val y: Float
)

@JsonClass(generateAdapter = true)
data class StrokePath(
    val points: List<PointData>,
    val color: Int,
    val width: Float,
    val isEraser: Boolean = false
)

@JsonClass(generateAdapter = true)
data class LayerData(
    val name: String,
    val isVisible: Boolean = true,
    val opacity: Float = 1.0f,
    val strokes: List<StrokePath> = emptyList()
)

@JsonClass(generateAdapter = true)
data class RigJointData(
    val name: String,
    val x: Float, // relative 0.0 to 1.0
    val y: Float
)

@JsonClass(generateAdapter = true)
data class FrameData(
    val layers: List<LayerData> = listOf(LayerData("Layer 1")),
    val joints: List<RigJointData> = emptyList(),
    val expression: String = "Normal", // Normal, Happy, Sad, Angry, Shock, Laugh, Blink
    val voiceMouthShape: String = "Rest" // Rest, Wide, Open, Round, Close
)

@JsonClass(generateAdapter = true)
data class ProjectData(
    val frames: List<FrameData> = listOf(FrameData())
)

object RigConstants {
    val CONNECTIONS = listOf(
        "Head" to "Neck",
        "Neck" to "Torso",
        "Neck" to "L_Shoulder",
        "L_Shoulder" to "L_Elbow",
        "L_Elbow" to "L_Hand",
        "Neck" to "R_Shoulder",
        "R_Shoulder" to "R_Elbow",
        "R_Elbow" to "R_Hand",
        "Torso" to "L_Hip",
        "L_Hip" to "L_Knee",
        "L_Knee" to "L_Foot",
        "Torso" to "R_Hip",
        "R_Hip" to "R_Knee",
        "R_Knee" to "R_Foot"
    )

    val JOINT_COLORS = mapOf(
        "Head" to 0xFFFF5252.toInt(),
        "Neck" to 0xFFFF7A00.toInt(),
        "Torso" to 0xFFFFC700.toInt(),
        "L_Shoulder" to 0xFF3F51B5.toInt(),
        "L_Elbow" to 0xFF2196F3.toInt(),
        "L_Hand" to 0xFF00BCD4.toInt(),
        "R_Shoulder" to 0xFFE91E63.toInt(),
        "R_Elbow" to 0xFF9C27B0.toInt(),
        "R_Hand" to 0xFF673AB7.toInt(),
        "L_Hip" to 0xFF4CAF50.toInt(),
        "L_Knee" to 0xFF8BC34A.toInt(),
        "L_Foot" to 0xFFCDDC39.toInt(),
        "R_Hip" to 0xFF009688.toInt(),
        "R_Knee" to 0xFF4CAF50.toInt(),
        "R_Foot" to 0xFF8BC34A.toInt()
    )

    fun getDefaultJoints(): List<RigJointData> {
        return listOf(
            RigJointData("Head", 0.5f, 0.15f),
            RigJointData("Neck", 0.5f, 0.25f),
            RigJointData("Torso", 0.5f, 0.5f),
            RigJointData("L_Shoulder", 0.4f, 0.28f),
            RigJointData("L_Elbow", 0.35f, 0.4f),
            RigJointData("L_Hand", 0.3f, 0.5f),
            RigJointData("R_Shoulder", 0.6f, 0.28f),
            RigJointData("R_Elbow", 0.65f, 0.4f),
            RigJointData("R_Hand", 0.7f, 0.5f),
            RigJointData("L_Hip", 0.45f, 0.55f),
            RigJointData("L_Knee", 0.42f, 0.7f),
            RigJointData("L_Foot", 0.4f, 0.85f),
            RigJointData("R_Hip", 0.55f, 0.55f),
            RigJointData("R_Knee", 0.58f, 0.7f),
            RigJointData("R_Foot", 0.6f, 0.85f)
        )
    }
}
