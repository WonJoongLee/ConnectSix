package com.connectsix.connectsix

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class Turn (
    var playerId : String? = "",
    var stoneNum : String? = "",
    var coX : Int = -1, // -1 is error
    var coY : Int = -1, // -1 is error
)