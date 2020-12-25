package com.example.connectsix

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.d("intentCheck", "SUCCESS : MainActivity.kt")

        val intent = Intent(this, TwoplayerGameboard::class.java)

        tempButton.setOnClickListener {
            Log.d("buttonClick", "SUCCESS")
            startActivity(intent)
        }

    }
}