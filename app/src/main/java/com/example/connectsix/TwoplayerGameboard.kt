package com.example.connectsix

import android.os.Bundle
import android.os.PersistableBundle
import android.util.Log
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.twoplayer_gameboard.*

class TwoplayerGameboard : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.twoplayer_gameboard)

        Log.d("intentCheck", "SUCCESS : TwoplayerGameboard.kt")

        /*zoom out button이 눌려지면 각 button의 크기를 줄임*/
        zoomOutButton.setOnClickListener {
            board0000.setImageResource(R.drawable.left_top_corner32)
            /*1행만 시범적으로 해보는 것*/
            for(i in 1..17){
                var num = i
                var str = "0"
                var boardStr = "board00"

                str = if(num<10) str.plus(num.toString()) else num.toString()

                boardStr = boardStr.plus(str)
                Log.d("boardStr", boardStr)

                var target : Int = resources.getIdentifier(boardStr, "id", packageName)
                var imageButton : ImageButton = findViewById(target)
                imageButton.setImageResource(R.drawable.top32)
            }
            board0018.setImageResource(R.drawable.right_top_corner32)
        }
    }
}