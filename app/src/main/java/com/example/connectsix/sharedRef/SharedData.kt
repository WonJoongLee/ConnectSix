package com.example.connectsix.sharedRef

import android.app.Application

class SharedData : Application() {
    companion object{
        lateinit var prefs : PreferenceUtil
        /*lateinit var ratio : PreferenceUtil*/
    }

    override fun onCreate() {
        prefs = PreferenceUtil(applicationContext)
        /*ratio = PreferenceUtil(applicationContext)*/
        super.onCreate()
    }
}