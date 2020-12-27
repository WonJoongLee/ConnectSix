package com.example.connectsix.sharedRef

import android.app.Application

class SharedData : Application() {
    companion object{
        lateinit var prefs : PreferenceUtil
    }

    override fun onCreate() {
        prefs = PreferenceUtil(applicationContext)
        super.onCreate()
    }
}