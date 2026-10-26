package com.kaona.bach

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import com.kaona.bach.ui.BachMainScreen
import com.kaona.bach.ui.theme.BachTheme
import com.kaona.bach.viewmodel.BachViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("BACH", "MainActivity.onCreate starting")

        var viewModel: BachViewModel? = null
        var initError: Throwable? = null

        try {
            viewModel = ViewModelProvider(
                this,
                ViewModelProvider.AndroidViewModelFactory.getInstance(application)
            )[BachViewModel::class.java]
        } catch (t: Throwable) {
            Log.e("BACH", "Fatal error instantiating BachViewModel", t)
            initError = t
        }

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF0A0A0A),
                    surface = Color(0xFF1A1A1A),
                    onSurface = Color(0xFFE0E0E0),
                    onBackground = Color(0xFFE0E0E0)
                )
            ) {
                if (initError != null || viewModel == null) {
                    ErrorScreen(
                        error = initError ?: RuntimeException("ViewModel não inicializado"),
                        onReset = {
                            getSharedPreferences("bach_presets", Context.MODE_PRIVATE).edit().clear().commit()
                            getSharedPreferences("bach_crash", Context.MODE_PRIVATE).edit().clear().commit()
                            recreate()
                        }
                    )
                } else {
                    BachTheme {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(BachTheme.colors.background)
                        ) {
                            BachMainScreen(viewModel = viewModel!!)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorScreen(error: Throwable, onReset: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "B . A . C . H .",
            color = Color(0xFFD4AF37),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Serif
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Ocorreu um erro ao iniciar:",
            color = Color(0xFFFF5555),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${error::class.java.simpleName}: ${error.message}",
            color = Color(0xFFFFCC00),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = Log.getStackTraceString(error),
            color = Color(0xFFAAAAAA),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onReset,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4AF37))
        ) {
            Text("Limpar Dados e Reiniciar", color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}
