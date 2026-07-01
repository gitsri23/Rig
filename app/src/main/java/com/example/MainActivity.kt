package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.ProjectListScreen
import com.example.ui.screens.StudioScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AnimationViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val viewModel: AnimationViewModel = viewModel()
                    MainAppContent(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun MainAppContent(
    viewModel: AnimationViewModel,
    modifier: Modifier = Modifier
) {
    if (viewModel.currentProjectEntity == null) {
        ProjectListScreen(
            viewModel = viewModel,
            modifier = modifier
        )
    } else {
        StudioScreen(
            viewModel = viewModel,
            modifier = modifier
        )
    }
}
