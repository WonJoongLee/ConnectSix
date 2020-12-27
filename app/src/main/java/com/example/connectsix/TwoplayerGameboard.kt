package com.example.connectsix

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.*
import kotlinx.android.synthetic.main.twoplayer_gameboard.*
import java.lang.Boolean.FALSE

class TwoplayerGameboard : AppCompatActivity() {

    var clickCnt = 64 // 초기 확대 값(default 값)은 53

    var database = FirebaseDatabase.getInstance().reference.child("roomId").child("tempRoomId")

    var player1Name : String = ""
    var player2Name : String = ""

    var turnNum : Int = 1 // turnNum 변수는 돌이 몇 개 착수되었는지 확인하기 위한 변수
                          // 이거를 하나씩 올리면서 지금까지 돌이 몇개 올려졌는지 확인하고
                          // DB의 stone+n값의 n값을 설정한다.



    // 19*19 사이즈의 Boolean 배열(초기값 false)을 만들고 착수한 곳의 좌표를 확인한 후 true로 바꿔주기기
    var stoneCheckArray: Array<BooleanArray> = Array<BooleanArray>(19){ BooleanArray(19) } // 해당 위치에 돌이 착수되었는지 확인하는 array
    var stoneColorArray: Array<IntArray> = Array<IntArray>(19){ IntArray(19) } // 해당 위치에 무슨 색의 돌이 착수되었는지 확인하는 array 0 : 착수 안됨, 1 : 검은색, 2: 흰색

    var winnerStr : String = ""



    @SuppressLint("ShowToast")
    override fun onCreate(savedInstanceState: Bundle?) {
       super.onCreate(savedInstanceState)
       setContentView(R.layout.twoplayer_gameboard)


       getUserName()

       initBoards()


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

       database.child("stones").addChildEventListener(object:ChildEventListener{
           override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
               var coX : String = snapshot.child("coX").getValue().toString()
               var coXInt = coX.toInt()

               var coY : String = snapshot.child("coY").getValue().toString()
               var coYInt : Int = coY.toInt()

               var boardCoordinate : String = changeCoordinateToBoard(coXInt, coYInt)
               var target : Int = resources.getIdentifier(boardCoordinate ,"id", packageName)
               var imageButton : ImageButton = findViewById(target)

               when(turnNum%4){
                   1,0 -> { // turnNum(시도 횟수)가 4로 나눴을 때 1 또는 0이면 player 1 차례다.
                       setStoneDependOnSize(imageButton, "black", coXInt, coYInt)
                   }
                   2,3 ->{ // 2,3이면 player 2 차례다
                       setStoneDependOnSize(imageButton, "white", coXInt, coYInt)
                   }
               }
               println("@@@turnNum is $turnNum")
               turnNum++ // 한 턴이 진행되었음을 의미. 중요한 부분
               judgeVictory()
               //printBoard()
           }

           override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
           }

           override fun onChildRemoved(snapshot: DataSnapshot) {

           }

           override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {

           }

           override fun onCancelled(error: DatabaseError) {

           }

       })

        for(i in 0..18){
            var str = "0"
            var boardStr = "board"
            var stoneStr = "stone"
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

                    var stoneStrUpload : String = ""

                    stoneStrUpload = "stone".plus((turnNum).toString()) // "stone0" 꼴로 stoneStr에 저장이 된다.
                                                                     // 그 후 turnNum에 1을 더해 turn이 한 번 돌아갔음을 의미한다.

                    if(turnNum == 0){//만약 돌을 처음 두는 것이라면 앞으로 둘 돌들을 저장한 stones를 Firebase에 추가한다.
                        database.push().setValue("stones") // 처음 돌을 두는 과정이라면 push한다
                    }
                    when(turnNum%4){
                        1,0 -> { // turnNum(시도 횟수)가 4로 나눴을 때 1 또는 0이면 player 1 차례다.
                           val stoneData : Turn = Turn(player1Name, stoneStrUpload, i, j)
                            setStoneDependOnSize(imageButton, "black", i, j)
                            database.child("stones").push().setValue(stoneData)
                        }
                        2,3 ->{ // 2,3이면 player 2 차례다
                            val stoneData : Turn = Turn(player2Name, stoneStrUpload, i, j)
                            setStoneDependOnSize(imageButton, "white", i, j)
                            database.child("stones").push().setValue(stoneData)
                        }
                    }

                    println("${turnNum}번째 돌 착수")
                    stoneCheckArray[i][j] = true // 좌표를 확인해서 배열에 착수가 되었다고 true 값 대입
                    imageButton.isClickable = false // 한 번 돌이 착수된 곳은 다시 클릭하지 못하도록 설정
                }
            }
        }

    }

    /*지금 화면에 보여지는 한 타일(바둑판 한 칸의 사이즈)의 크기(16,32,64)와 플레이어의 차례에 따라 색을 구분해
    * 바둑판에 돌을 두는 함수입니다.*/
    private fun setStoneDependOnSize(imageButton: ImageButton, color : String, i : Int, j : Int){
        when(clickCnt){
            16->{ // size가 16이면
                if(color == "white") {
                    imageButton.setImageResource(R.drawable.circle_white16)
                    stoneColorArray[i][j] = 2
                } // player1차례면 black
                else {
                    imageButton.setImageResource(R.drawable.circle_black16)
                    stoneColorArray[i][j] = 1
                } // player2 차례면 white

            }
            32->{ // size가 32이면
                if(color == "white"){
                    imageButton.setImageResource(R.drawable.circle_white32)
                    stoneColorArray[i][j] = 2
                }
                else {
                    imageButton.setImageResource(R.drawable.circle_black32)
                    stoneColorArray[i][j] = 1
                }
            }
            64->{ // size가 64이면
                if(color == "white") {
                    imageButton.setImageResource(R.drawable.circle_white64)
                    stoneColorArray[i][j] = 2
                }
                else {
                    imageButton.setImageResource(R.drawable.circle_black64)
                    stoneColorArray[i][j] = 1
                }
            }
        }
    }

    fun changeCoordinateToBoard(x : Int, y : Int) : String{
        val tempXStr : String = if(x<10) "0".plus(x.toString()) else x.toString()
        val tempYStr : String = if(y<10) "0".plus(y.toString()) else y.toString()
        return "board".plus(tempXStr).plus(tempYStr)
    }

    /* Initialize User Name
    *  이 코드에서 유저 이름 사용할 수 있도록 초기화 시키는 부분 */
    fun getUserName(){
        database.addValueEventListener(object : ValueEventListener{
            override fun onDataChange(snapshot: DataSnapshot) {
                for(i in snapshot.children){
                    if(i.key.equals("player1Id")){
                        player1Name = i.value as String
                    }
                    if(i.key.equals("player2Id")){
                        player2Name = i.value as String
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Name Error", "Can't get user name")
            }
        })
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
                when(stoneColorArray[i][j]){// 해당 위치(i,j)에 돌이 착수가 되었다면
                    1 -> {
                        imageButton.setImageResource(R.drawable.circle_black32)
                        continue // 돌에 대한 처리를 when에서 해주기 때문에 (i,j)의 돌에 대한 처리는 여기서 해줄 필요 없다.
                    }
                    2 -> {
                        imageButton.setImageResource(R.drawable.circle_white32)
                        continue
                    }
                }
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
                when(stoneColorArray[i][j]){// 해당 위치(i,j)에 돌이 착수가 되었다면
                    1 -> {
                        imageButton.setImageResource(R.drawable.circle_black64)
                        continue // 돌에 대한 처리를 when에서 해주기 때문에 (i,j)의 돌에 대한 처리는 여기서 해줄 필요 없다.
                    }
                    2 -> {
                        imageButton.setImageResource(R.drawable.circle_white64)
                        continue
                    }
                }
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
                when(stoneColorArray[i][j]){// 해당 위치(i,j)에 돌이 착수가 되었다면
                    1 -> {
                        imageButton.setImageResource(R.drawable.circle_black16)
                        continue // 돌에 대한 처리를 when에서 해주기 때문에 (i,j)의 돌에 대한 처리는 여기서 해줄 필요 없다.
                    }
                    2 -> {
                        imageButton.setImageResource(R.drawable.circle_white16)
                        continue
                    }
                }
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

    fun printColorBoard(){
        println("@@@@PRINT BOARD@@@@")
        Log.d("Print Board", "Print Board")
        for(i in 0..18){
            print("$i : ".padStart(3))
            for(j in 0..18){
                print("${stoneColorArray[i][j]}".padStart(8))
            }
            println()
        }
    }

    fun printBoard(){
        println("@@@@PRINT BOARD@@@@")
        for(i in 0..18){
            for(j in 0..18){
                print("${stoneCheckArray[i][j]}".padStart(8))
            }
            println()
        }
    }

    fun judgeVictory(){
        for(i in 0..13){ // 세로로 다 맞았을 때
            for(j in 0..18){
                if(stoneColorArray[i][j] == 1 && stoneColorArray[i+1][j] == 1 && stoneColorArray[i+2][j] == 1 && stoneColorArray[i+3][j] == 1 && stoneColorArray[i+4][j] == 1 && stoneColorArray[i+5][j] ==  1){
                    winnerStr = player1Name
                    Toast.makeText(this, "1$winnerStr win!", Toast.LENGTH_SHORT).show()
                    printColorBoard()
                    initBoards() // 게임 승리 판정이 나면 초기화
                    turnNum = 1
                }
                else if(stoneColorArray[i][j] == 2 && stoneColorArray[i+1][j] == 2 && stoneColorArray[i+2][j] == 2 && stoneColorArray[i+3][j] == 2 && stoneColorArray[i+4][j] == 2 && stoneColorArray[i+5][j] ==  2){
                    winnerStr = player2Name
                    Toast.makeText(this, "2$winnerStr win!", Toast.LENGTH_SHORT).show()
                    printColorBoard()
                    initBoards()
                    turnNum = 1
                }
            }
        }

        for(j in 0..13){ // 가로로 다 맞았을 때
            for(i in 0..18){
                if(stoneColorArray[i][j] == 1 && stoneColorArray[i][j+1] == 1 && stoneColorArray[i][j+2] == 1 && stoneColorArray[i][j+3] == 1 && stoneColorArray[i][j+4] == 1 && stoneColorArray[i][j+5] ==  1){
                    winnerStr = player1Name
                    Toast.makeText(this, "3$winnerStr win!", Toast.LENGTH_SHORT).show()
                    printColorBoard()
                    initBoards()
                    turnNum = 1
                }
                else if(stoneColorArray[i][j] == 2 && stoneColorArray[i][j+1] == 2 && stoneColorArray[i][j+2] == 2 && stoneColorArray[i][j+3] == 2 && stoneColorArray[i][j+4] == 2 && stoneColorArray[i][j+5] ==  2){
                    winnerStr = player2Name
                    Toast.makeText(this, "4$winnerStr win!", Toast.LENGTH_SHORT).show()
                    printColorBoard()
                    initBoards()
                    turnNum = 1
                }
            }
        }


        for(j in 0..13){ // \(왼쪽 위에서 오른쪽 아래) 대각선 다 맞았을 때
            for(i in 0..13){
                if(stoneColorArray[i][j] == 1 && stoneColorArray[i+1][j+1] == 1 && stoneColorArray[i+2][j+2] == 1 && stoneColorArray[i+3][j+3] == 1 && stoneColorArray[i+4][j+4] == 1 && stoneColorArray[i+5][j+5] ==  1){
                    winnerStr = player1Name
                    Toast.makeText(this, "5$winnerStr win!", Toast.LENGTH_SHORT).show()
                    printColorBoard()
                    initBoards()
                    turnNum = 1
                }
                else if(stoneColorArray[i][j] == 2 && stoneColorArray[i+1][j+1] == 2 && stoneColorArray[i+2][j+2] == 2 && stoneColorArray[i+3][j+3] == 2 && stoneColorArray[i+4][j+4] == 2 && stoneColorArray[i+5][j+5] ==  2){
                    winnerStr = player2Name
                    Toast.makeText(this, "6$winnerStr win!", Toast.LENGTH_SHORT).show()
                    printColorBoard()
                    initBoards()
                    turnNum = 1
                }
            }
        }

        for(j in 0..13){ // /(왼쪽 위에서 오른쪽 아래) 대각선 다 맞았을 때
            for(i in 0..13){
                if(stoneColorArray[19-i-1][j] == 1 && stoneColorArray[19-i-2][j+1] == 1 && stoneColorArray[19-i-3][j+2] == 1 && stoneColorArray[19-i-4][j+3] == 1 && stoneColorArray[19-i-5][j+4] == 1 && stoneColorArray[19-i-6][j+5] ==  1){
                    winnerStr = player1Name
                    Toast.makeText(this, "7$winnerStr win!", Toast.LENGTH_SHORT).show()
                    printColorBoard()
                    initBoards()
                    turnNum = 1
                }
                else if(stoneColorArray[19-i-1][j] == 2 && stoneColorArray[19-i-2][j+1] == 2 && stoneColorArray[19-i-3][j+2] == 2 && stoneColorArray[19-i-4][j+3] == 2 && stoneColorArray[19-i-5][j+4] == 2 && stoneColorArray[19-i-6][j+5] ==  2){
                    winnerStr = player2Name
                    Toast.makeText(this, "8$winnerStr win!", Toast.LENGTH_SHORT).show()
                    printColorBoard()
                    initBoards()
                    turnNum = 1
                }
            }
        }
    }


    var backKeyPressedTime : Long = 0 // 뒤로가기 버튼 클릭 시간 확인을 위한 변수
    //뒤로가기 버튼 클릭 시
    override fun onBackPressed(){
        if(System.currentTimeMillis() > backKeyPressedTime + 2500){ // 만약 클릭한 시간이 2.5초가 지났다면연(연속적으로 클릭한 것이 아닐 때)
            backKeyPressedTime = System.currentTimeMillis()
            Toast.makeText(this, "주의 : 한번 더 누르시면 게임이 나가집니다!", Toast.LENGTH_SHORT).show()
            return
        }

        if(System.currentTimeMillis() <= backKeyPressedTime + 2500){ // 만약 클릭한 시간이 2.5초가 이하라면(연속적으로 클릭했을 때)
            //initBoards()
            //turnNum = 1
            database.child("stones").removeValue() // 적혀져있던 돌들 삭제하는 부분, 베타Beta일 때만 이렇게 두고 실제로 게임 오픈하면 방 자체를 삭제해야 하나.. 고민해보자
            Toast.makeText(this, "이용해 주셔서 감사합니다.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun initBoards(){
        println("Initializing...")
        for(i in stoneCheckArray.indices){
            for( j in stoneCheckArray[i].indices){
                stoneCheckArray[i][j] = false // 돌이 모두 착수가 안된 상태로 초기화
            }
        }
        for(i in stoneColorArray.indices){
            for( j in stoneColorArray[i].indices){
                stoneColorArray[i][j] = 0 // 초기에는 돌이 착수 안됐음을 표시
            }
        }
        printColorBoard()
    }

}


