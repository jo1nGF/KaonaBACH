package com.kaona.bach

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Activity pura sem Compose nem bibliotecas externas.
 * Abre de forma 100% garantida em caso de qualquer falha não tratada.
 */
class CrashReportActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val error = intent.getStringExtra("error")
            ?: getSharedPreferences("bach_crash", Context.MODE_PRIVATE).getString("last_crash", "Sem detalhes do erro")

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0A0A0A"))
            setPadding(32, 48, 32, 32)
        }

        val title = TextView(this).apply {
            text = "B . A . C . H .\nRelatório de Inicialização"
            setTextColor(Color.parseColor("#D4AF37"))
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_HORIZONTAL
        }
        root.addView(title)

        val subtitle = TextView(this).apply {
            text = "O aplicativo encontrou um erro ao iniciar:"
            setTextColor(Color.parseColor("#FF5555"))
            textSize = 13f
            setPadding(0, 16, 0, 16)
        }
        root.addView(subtitle)

        val btnReset = Button(this).apply {
            text = "Limpar Dados Salvos e Reiniciar"
            setBackgroundColor(Color.parseColor("#D4AF37"))
            setTextColor(Color.BLACK)
            setOnClickListener {
                getSharedPreferences("bach_presets", Context.MODE_PRIVATE).edit().clear().commit()
                getSharedPreferences("bach_crash", Context.MODE_PRIVATE).edit().clear().commit()
                val restart = Intent(this@CrashReportActivity, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                startActivity(restart)
                finish()
            }
        }
        root.addView(btnReset)

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            ).apply { topMargin = 24 }
        }

        val logView = TextView(this).apply {
            text = error
            setTextColor(Color.parseColor("#CCCCCC"))
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        scrollView.addView(logView)
        root.addView(scrollView)

        setContentView(root)
    }
}
