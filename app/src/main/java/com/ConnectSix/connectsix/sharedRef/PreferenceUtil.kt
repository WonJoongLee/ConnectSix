package com.ConnectSix.connectsix.sharedRef

import android.content.Context
import android.content.SharedPreferences

class PreferenceUtil(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("nickName", Context.MODE_PRIVATE)
    /*private val ratio: SharedPreferences = context.getSharedPreferences("ratio", Context.MODE_PRIVATE)

    fun getRatio(key:String, defValue : String) : String{
        return prefs.getString(key, defValue).toString()
    }
    fun setRatio(key:String, str : String){
        prefs.edit().putString(key, str).apply()
    }
     */


    fun getName(key:String, defValue : String) : String{
        return prefs.getString(key, defValue).toString()
    }
    fun setName(key:String, str : String){
        prefs.edit().putString(key, str).apply()
    }
}