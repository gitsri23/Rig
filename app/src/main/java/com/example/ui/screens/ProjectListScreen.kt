package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.ProjectEntity
import com.example.viewmodel.AnimationViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectListScreen(
    viewModel: AnimationViewModel,
    modifier: Modifier = Modifier
) {
    val projectList by viewModel.projects.collectAsState(initial = emptyList())
    var showCreateDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF1E1B4B), Color(0xFF090514)),
                    radius = 2000f
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "ANIMATE AI",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "2D Rigging & Lipsync Studio",
                        fontSize = 14.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Project List
            if (projectList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF131124))
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = "No Projects",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Projects Yet",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF1F5F9)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Create a project to start drawing and animating with AI automatic rigging and vocal lipsync!",
                            fontSize = 14.sp,
                            color = Color(0xFF64748B),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { showCreateDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.testTag("create_first_project_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Create Project")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Create Project", color = Color(0xFF0A0C14), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(projectList) { project ->
                        ProjectItemCard(
                            project = project,
                            onClick = { viewModel.selectProject(project) },
                            onDelete = { viewModel.deleteProject(project) }
                        )
                    }
                }
            }
        }

        // Floating Action Button
        if (projectList.isNotEmpty()) {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color(0xFF0A0C14),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(32.dp)
                    .testTag("add_project_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Project", modifier = Modifier.size(28.dp))
            }
        }
    }

    // Create Project Dialog
    if (showCreateDialog) {
        CreateProjectDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, fps ->
                viewModel.createProject(name, fps)
                showCreateDialog = false
            }
        )
    }
}

@Composable
fun ProjectItemCard(
    project: ProjectEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("MMM dd, yyyy · hh:mm a", Locale.getDefault()) }
    val dateString = formatter.format(Date(project.createdAt))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("project_card_${project.id}"),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131124)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.name,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = "Frames",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${project.fps} FPS",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = "Created At",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = dateString,
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("delete_project_${project.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Project",
                    tint = Color(0xFFEF4444)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateProjectDialog(
    onDismiss: () -> Unit,
    onCreate: (String, Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var fps by remember { mutableIntStateOf(12) }

    Dialog(onDismissRequest = onDismiss) {
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
                Text(
                    text = "New Project",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Project Name") },
                    placeholder = { Text("My Awesome Animation") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = Color(0xFF94A3B8),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("project_name_input")
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Framerate (FPS)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    listOf(12, 24, 30).forEach { rate ->
                        val selected = fps == rate
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) MaterialTheme.colorScheme.primary else Color(0xFF2E2A47))
                                .clickable { fps = rate }
                                .padding(vertical = 12.dp)
                                .testTag("fps_option_$rate"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$rate FPS",
                                fontWeight = FontWeight.Bold,
                                color = if (selected) Color(0xFF0A0C14) else Color.White,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.testTag("cancel_create_button")) {
                        Text("Cancel", color = Color(0xFF94A3B8))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = { if (name.isNotBlank()) onCreate(name, fps) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        enabled = name.isNotBlank(),
                        modifier = Modifier.testTag("confirm_create_button")
                    ) {
                        Text("Create", color = Color(0xFF0A0C14), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
