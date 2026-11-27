package com.tecpal.device.mc30

import android.app.Activity
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.IBinder
import android.view.Gravity
import android.widget.TextView

/** Écran unique de la fausse appli Lidl : reconnaissable au premier coup d'œil sur l'émulateur. */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = "FAUSSE APPLI LIDL\n(banc d'essai Camybrith)"
            textSize = 32f
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(0, 80, 170))
            setTextColor(Color.WHITE)
        })
    }
}

/** Service « moteur » : ne fait rien ; seul son état activé/désactivé compte. */
class SerialPortMC30Service : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
