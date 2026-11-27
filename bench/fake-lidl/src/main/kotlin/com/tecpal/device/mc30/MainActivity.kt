package com.tecpal.device.mc30

import android.app.Activity
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.IBinder
import android.view.Gravity
import android.widget.TextView

/** Single screen of the fake Lidl app: recognisable at a glance on the emulator. */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = "FAKE LIDL APP\n(Camybrith test bench)"
            textSize = 32f
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(0, 80, 170))
            setTextColor(Color.WHITE)
        })
    }
}

/** "Motor" service: does nothing; only its enabled/disabled state matters. */
class SerialPortMC30Service : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
