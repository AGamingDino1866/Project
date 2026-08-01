package com.sketchstudio.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sketchstudio.app.ui.editor.EditorScreen
import com.sketchstudio.app.ui.home.HomeScreen
import com.sketchstudio.app.ui.theme.SketchStudioTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SketchStudioTheme {
                var pickedImageUri by remember { mutableStateOf<Uri?>(null) }
                val navController = rememberNavController()

                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            onImagePicked = { uri ->
                                pickedImageUri = uri
                                navController.navigate("editor")
                            }
                        )
                    }
                    composable("editor") {
                        val uri = pickedImageUri
                        if (uri != null) {
                            EditorScreen(
                                imageUri = uri,
                                onClose = {
                                    pickedImageUri = null
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
