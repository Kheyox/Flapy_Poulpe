package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.example.data.GameDatabase
import com.example.data.HighScoreRepository
import com.example.game.GameScreen
import com.example.game.GameViewModel
import com.example.game.GameViewModelFactory
import com.example.game.SoundManager
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Initialize low latency Sound Synth
    SoundManager.init()

    // Initialize Room Database & repositories
    val database = GameDatabase.getDatabase(applicationContext)
    val repository = HighScoreRepository(database.highScoreDao())

    // Instantiate Main Video Game ViewModel using simple factory pattern in constructor injection
    val viewModel = ViewModelProvider(this, GameViewModelFactory(repository))[GameViewModel::class.java]

    setContent {
      MyApplicationTheme {
        Surface(
          modifier = Modifier.fillMaxSize()
        ) {
          GameScreen(
            viewModel = viewModel,
            modifier = Modifier.fillMaxSize()
          )
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    // Resume background music on app focus
    SoundManager.startMusic()
  }

  override fun onPause() {
    super.onPause()
    // Pause background music to save battery and follow Android standards
    SoundManager.stopMusic()
  }

  override fun onDestroy() {
    super.onDestroy()
    SoundManager.release()
  }
}

