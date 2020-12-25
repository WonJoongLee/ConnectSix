package com.example.connectsix

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.FirebaseDatabase
import kotlinx.android.synthetic.main.twoplayer_gameboard.*

class TwoplayerGameboard : AppCompatActivity() {

    var clickCnt = 64 // 초기 확대 값(default 값)은 53
    var database = FirebaseDatabase.getInstance()
    var roomId = database.getReference().child("roomId").child("tempRoomId") // TODO 방 새로 만드는 함수 만든 후 path에 방 위치를 넣어야 함

    @SuppressLint("ShowToast")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.twoplayer_gameboard)

        var Id = roomId.child("stone1").child("playerId") // TODO 201226은 여기서부터 서버와 통신하면서 멀티플레이 기능 만들면 된다.

        println("@@@@$Id")

        Log.d("intentCheck", "SUCCESS : TwoplayerGameboard.kt")

        /*zoom out button이 눌려지면 각 button의 크기를 줄임
        * default 값은 64*/
        zoomOutButton.setOnClickListener { //축소
            println("@@@축소clickec!!")
            when(clickCnt){
                64 -> {
                    zoomBoard32()
                    clickCnt = 32
                }
                32 -> {
                    zoomBoard16()
                    clickCnt = 16
                }
            }
        }

        zoomInButton.setOnClickListener { // 확대
            println("@@@확대clicked!!")
            when(clickCnt){
                16 -> {
                    zoomBoard32()
                    clickCnt = 32

                }
                32 -> {
                    zoomBoard64()
                    clickCnt = 64
                }
            }
        }

        for(i in 0..18){
            var str = "0"
            var boardStr = "board"
            var firstNum = if(i<10) str.plus(i.toString()) else i.toString() // board 뒤에 바로 붙는 첫 번째 수

            boardStr = boardStr.plus(firstNum) // board00까지 완성

            for(j in 0..18){
                var boardAndFirstNum = boardStr // boardStr값은 변경되지 않도록 함
                var zero = "0"
                var boardFinal = boardAndFirstNum.plus(if(j<10) zero.plus(j) else j.toString()) // 만약 j가 10보다 작으면 j 앞에 0을 붙이고, j가 10이상이면 그대로 j를 저장
                //최종적으로 boardFinal에는 board0000꼴로 id가 저장되게 된다.

                var target : Int = resources.getIdentifier(boardFinal, "id", packageName) // id 값을 target에 저장
                var imageButton : ImageButton = findViewById(target) // id값(target)과 findViewById를 통해 imageButton 변수에 좌표에 해당하는 값 할당
                imageButton.setOnClickListener {
                    when(clickCnt){
                        16->{ // size가 16이면
                            imageButton.setImageResource(R.drawable.circle_white16)
                        }
                        32->{ // size가 32이면
                            imageButton.setImageResource(R.drawable.circle_white32)
                        }
                        64->{ // size가 64이면
                            imageButton.setImageResource(R.drawable.circle_white64)
                        }
                    }
                }
            }
        }

    }

    fun zoomBoard32(){
        for(i in 0..18){ // 행
            var str = "0"
            var boardStr = "board"

            var firstNum = if(i<10) str.plus(i.toString()) else i.toString() // board 뒤에 바로 붙는 첫 번째 수

            boardStr = boardStr.plus(firstNum) // board00까지 완성
            Log.d("boardStr", boardStr)

            for(j in 0..18){ // 열
                var boardAndFirstNum = boardStr // boardStr값은 변경되지 않도록 함
                var zero = "0"
                var boardFinal = boardAndFirstNum.plus(if(j<10) zero.plus(j) else j.toString()) // 만약 j가 10보다 작으면 j 앞에 0을 붙이고, j가 10이상이면 그대로 j를 저장
                //최종적으로 boardFinal에는 board0000꼴로 id가 저장되게 된다.

                //println("@@@$boardFinal")

                /*좌표 위치를 통해 ImageButton의 크기를 줄이는 부분*/
                var target : Int = resources.getIdentifier(boardFinal, "id", packageName) // id 값을 target에 저장
                var imageButton : ImageButton = findViewById(target) // id값(target)과 findViewById를 통해 imageButton 변수에 좌표에 해당하는 값 할당
                if ((i==3 && j == 3)||(i==3 && j == 9)||(i==3 && j == 15)||(i==9 && j == 3)||(i==9 && j == 9)||(i==9 && j == 15)||(i==15 && j == 3)||(i==15 && j == 9)||(i==15 && j == 15)) imageButton.setImageResource(R.drawable.point32)
                else if(i!=0 && j==0 && i!=18) imageButton.setImageResource(R.drawable.left32) // 왼쪽 부분일 때
                else if (i == 0 && j == 0) imageButton.setImageResource(R.drawable.left_top_corner32) // 왼쪽 위 코너일 때, (0,0)일 때
                else if (i == 18 && j == 0) imageButton.setImageResource(R.drawable.left_bottom_corner32) // 왼쪽 아래 코너일 때, (18,0)일 때
                else if (i!=0 && j==18 && i != 18) imageButton.setImageResource(R.drawable.right32) // 오른쪽 일 때
                else if (i==0 && j==18) imageButton.setImageResource(R.drawable.right_top_corner32) // 오른쪽 위 코너일 때, (0,18)일 때
                else if (i==18 && j==18) imageButton.setImageResource(R.drawable.right_bottom_corner32) // 오른쪽 아래 코너일 때, (18,18)일 때
                else if (i==18 && j!=0 && j!=19) imageButton.setImageResource(R.drawable.bottom32) // 아래일 때
                else if (i==0 && j!=0 && j!=19) imageButton.setImageResource(R.drawable.top32) // 위일 때
                else imageButton.setImageResource(R.drawable.standard32) // imageButton의 image를 32 size로 축소

            }
        }
    }

    fun zoomBoard64(){
        for(i in 0..18){ // 행
            var str = "0"
            var boardStr = "board"

            var firstNum = if(i<10) str.plus(i.toString()) else i.toString() // board 뒤에 바로 붙는 첫 번째 수

            str = if(i <10) str.plus(i.toString()) else i.toString()

            boardStr = boardStr.plus(firstNum) // board00까지 완성
            Log.d("boardStr", boardStr)

            for(j in 0..18){ // 열
                var boardAndFirstNum = boardStr // boardStr값은 변경되지 않도록 함
                var zero = "0"
                var boardFinal = boardAndFirstNum.plus(if(j<10) zero.plus(j) else j.toString()) // 만약 j가 10보다 작으면 j 앞에 0을 붙이고, j가 10이상이면 그대로 j를 저장
                //최종적으로 boardFinal에는 board0000꼴로 id가 저장되게 된다.

                //println("@@@$boardFinal")

                /*좌표 위치를 통해 ImageButton의 크기를 줄이는 부분*/
                var target : Int = resources.getIdentifier(boardFinal, "id", packageName) // id 값을 target에 저장
                var imageButton : ImageButton = findViewById(target) // id값(target)과 findViewById를 통해 imageButton 변수에 좌표에 해당하는 값 할당
                if ((i==3 && j == 3)||(i==3 && j == 9)||(i==3 && j == 15)||(i==9 && j == 3)||(i==9 && j == 9)||(i==9 && j == 15)||(i==15 && j == 3)||(i==15 && j == 9)||(i==15 && j == 15)) imageButton.setImageResource(R.drawable.point64)
                else if(i!=0 && j==0 && i!=18) imageButton.setImageResource(R.drawable.left64) // 왼쪽 부분일 때
                else if (i == 0 && j == 0) imageButton.setImageResource(R.drawable.left_top_corner64) // 왼쪽 위 코너일 때, (0,0)일 때
                else if (i == 18 && j == 0) imageButton.setImageResource(R.drawable.left_bottom_corner64) // 왼쪽 아래 코너일 때, (18,0)일 때
                else if (i!=0 && j==18 && i != 18) imageButton.setImageResource(R.drawable.right64) // 오른쪽 일 때
                else if (i==0 && j==18) imageButton.setImageResource(R.drawable.right_top_corner64) // 오른쪽 위 코너일 때, (0,18)일 때
                else if (i==18 && j==18) imageButton.setImageResource(R.drawable.right_bottom_corner64) // 오른쪽 아래 코너일 때, (18,18)일 때
                else if (i==18 && j!=0 && j!=19) imageButton.setImageResource(R.drawable.bottom64) // 아래일 때
                else if (i==0 && j!=0 && j!=19) imageButton.setImageResource(R.drawable.top64) // 위일 때
                else imageButton.setImageResource(R.drawable.standard64) // imageButton의 image를 32 size로 축소

            }
        }
    }

    fun zoomBoard16(){
        for(i in 0..18){ // 행
            var str = "0"
            var boardStr = "board"

            var firstNum = if(i<10) str.plus(i.toString()) else i.toString() // board 뒤에 바로 붙는 첫 번째 수

            str = if(i <10) str.plus(i.toString()) else i.toString()

            boardStr = boardStr.plus(firstNum) // board00까지 완성
            Log.d("boardStr", boardStr)

            for(j in 0..18){ // 열
                var boardAndFirstNum = boardStr // boardStr값은 변경되지 않도록 함
                var zero = "0"
                var boardFinal = boardAndFirstNum.plus(if(j<10) zero.plus(j) else j.toString()) // 만약 j가 10보다 작으면 j 앞에 0을 붙이고, j가 10이상이면 그대로 j를 저장
                //최종적으로 boardFinal에는 board0000꼴로 id가 저장되게 된다.

                //println("@@@$boardFinal")

                /*좌표 위치를 통해 ImageButton의 크기를 줄이는 부분*/
                var target : Int = resources.getIdentifier(boardFinal, "id", packageName) // id 값을 target에 저장
                var imageButton : ImageButton = findViewById(target) // id값(target)과 findViewById를 통해 imageButton 변수에 좌표에 해당하는 값 할당
                if ((i==3 && j == 3)||(i==3 && j == 9)||(i==3 && j == 15)||(i==9 && j == 3)||(i==9 && j == 9)||(i==9 && j == 15)||(i==15 && j == 3)||(i==15 && j == 9)||(i==15 && j == 15)) imageButton.setImageResource(R.drawable.point16)
                else if(i!=0 && j==0 && i!=18) imageButton.setImageResource(R.drawable.left16) // 왼쪽 부분일 때
                else if (i == 0 && j == 0) imageButton.setImageResource(R.drawable.left_top_corner16) // 왼쪽 위 코너일 때, (0,0)일 때
                else if (i == 18 && j == 0) imageButton.setImageResource(R.drawable.left_bottom_corner16) // 왼쪽 아래 코너일 때, (18,0)일 때
                else if (i!=0 && j==18 && i != 18) imageButton.setImageResource(R.drawable.right16) // 오른쪽 일 때
                else if (i==0 && j==18) imageButton.setImageResource(R.drawable.right_top_corner16) // 오른쪽 위 코너일 때, (0,18)일 때
                else if (i==18 && j==18) imageButton.setImageResource(R.drawable.right_bottom_corner16) // 오른쪽 아래 코너일 때, (18,18)일 때
                else if (i==18 && j!=0 && j!=19) imageButton.setImageResource(R.drawable.bottom16) // 아래일 때
                else if (i==0 && j!=0 && j!=19) imageButton.setImageResource(R.drawable.top16) // 위일 때
                else imageButton.setImageResource(R.drawable.standard16) // imageButton의 image를 32 size로 축소

            }
        }
    }
}