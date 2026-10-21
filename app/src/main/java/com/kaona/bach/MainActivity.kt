package com.kaona.bach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.kaona.bach.ui.BachMainScreen
import com.kaona.bach.ui.theme.BachTheme
import com.kaona.bach.viewmodel.BachViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: BachViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BachTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BachTheme.colors.background
                ) {
                    BachMainScreen(viewModel = viewModel)
                }
            }
        }
    }
}
