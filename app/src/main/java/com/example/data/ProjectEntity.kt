package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val fps: Int = 12,
    val createdAt: Long = System.currentTimeMillis(),
    val currentFrameIndex: Int = 0,
    val dataJson: String, // serialized ProjectData
    val audioPath: String? = null,
    val speechScript: String? = null
)
