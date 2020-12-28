package com.example.connectsix

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.util.Log
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.*
import kotlinx.android.synthetic.main.twoplayer_gameboard.*

class TwoplayerGameboard : AppCompatActivity() {

    var clickCnt = 64 // 초기 확대 값(default 값)은 53

    var database = FirebaseDatabase.getInstance().reference.child("roomId").child("tempRoomId")

    var player1Name : String = ""
    var player2Name : String = ""
    var myNickName : String = ""
    var enemyName : String = ""

    var serverUser1Name : Boolean = false
    var serverUser2Name : Boolean = false

    var nameFinished : Boolean = false
    var isPlayer1Name : Boolean = false
    var isPlayer2Name : Boolean = false

    var turnNum : Int = 1 // turnNum 변수는 돌이 몇 개 착수되었는지 확인하기 위한 변수
                          // 이거를 하나씩 올리면서 지금까지 돌이 몇개 올려졌는지 확인하고
                          // DB의 stone+n값의 n값을 설정한다.

    var turnDataList = mutableListOf<Turn>()

    // 19*19 사이즈의 Boolean 배열(초기값 false)을 만들고 착수한 곳의 좌표를 확인한 후 true로 바꿔주기기
    var stoneCheckArray: Array<BooleanArray> = Array<BooleanArray>(19){ BooleanArray(19) } // 해당 위치에 돌이 착수되었는지 확인하는 array
    var stoneColorArray: Array<IntArray> = Array<IntArray>(19){ IntArray(19) } // 해당 위치에 무슨 색의 돌이 착수되었는지 확인하는 array 0 : 착수 안됨, 1 : 검은색, 2: 흰색
    var checkStoneArray: Array<IntArray> = Array<IntArray>(19){ IntArray(19) }

    var winnerStr : String = ""

    var clickStoneChanged : Boolean = false

    @SuppressLint("ShowToast", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.twoplayer_gameboard)

        if(intent.hasExtra("player1NickName")) myNickName = intent.getStringExtra("player1NickName").toString() // MainActivity에서 설정한 이름을 intent로 불러와서 player1Name에 저장한다.

        getUserName() // 이름 설정을 합니다.
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

        //상대편이 들어왔을 경우 화면에 이름 띄워주고 승리했을 때 이름 띄워주기 위해 설정하는 부분
        // 상대방이 들어온 것을 감지 후 nickname을 설정한다.
        database.child("player1Id").addChildEventListener(object:ChildEventListener{
            @SuppressLint("ResourceType")
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                println("@@@ something changed : ${snapshot.value.toString().length}")
                if(snapshot.value.toString().isNotEmpty()){ // User가 들어왔을 경우
                   user1Nickname.text = snapshot.value.toString()
                }else if(snapshot.value.toString().isEmpty()){ // User가 나갔을 경우
                    user1Nickname.text = findViewById(R.string.waiting)
                }
            }
            @SuppressLint("ResourceType")
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                if(snapshot.value.toString().isNotEmpty()){ // User가 들어왔을 경우
                    user1Nickname.text = snapshot.value.toString()
                }else if(snapshot.value.toString().isEmpty()){ // User가 나갔을 경우
                    user1Nickname.text = findViewById(R.string.waiting)
                }
            }
            @SuppressLint("ResourceType")
            override fun onChildRemoved(snapshot: DataSnapshot) {
            }
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        })
        //TODO player1Id와 player2Id가 서버에서 삭제되었을 때(즉 상대방이 나갔을 때) 상대방 닉네임 뜨는 부분을 Waiting...으로 처리해야 함
        database.child("player2Id").addChildEventListener(object:ChildEventListener{
            @SuppressLint("ResourceType")
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                println("@@@ something changed : ${snapshot.value.toString().length}")
                if(snapshot.value.toString().isNotEmpty()){ // User가 들어왔을 경우
                    user2Nickname.text = snapshot.value.toString()
                }else if(snapshot.value.toString().isEmpty()){ // User가 나갔을 경우
                    user2Nickname.text = findViewById(R.string.waiting)
                }
            }

            @SuppressLint("ResourceType")
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                if(snapshot.value.toString().isNotEmpty()){ // User가 들어왔을 경우
                    user2Nickname.text = snapshot.value.toString()
                }else if(snapshot.value.toString().isEmpty()){ // User가 나갔을 경우
                    user2Nickname.text = findViewById(R.string.waiting)
                }
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {
            }

            override fun onCancelled(error: DatabaseError) {
            }
        })

        //데이터의 변화가 있을 때, 즉 상대방이 돌을 두면 DB 변화를 감지해서 내 화면에 변경된 점을 뿌려주는 부분
        database.child("stones").addChildEventListener(object:ChildEventListener{
           override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
               var coX : String = snapshot.child("coX").getValue().toString()
               var coXInt = coX.toInt()

               var coY : String = snapshot.child("coY").getValue().toString()
               var coYInt : Int = coY.toInt()

               var boardCoordinate : String = changeCoordinateToBoard(coXInt, coYInt)
               var target : Int = resources.getIdentifier(boardCoordinate ,"id", packageName)
               var imageButton : ImageButton = findViewById(target)

               clickStoneChanged = false
               turnDataList.add(Turn("", "", coXInt, coYInt))

               when(turnNum%4){
                   1,0 -> { // turnNum(시도 횟수)가 4로 나눴을 때 1 또는 0이면 player 1 차례다.
                       setStoneDependOnSize(imageButton, "black", coXInt, coYInt)
                   }
                   2,3 ->{ // 2,3이면 player 2 차례다
                       setStoneDependOnSize(imageButton, "white", coXInt, coYInt)
                   }
               }
               println("@@@turnNum is $turnNum")

               if(turnNum>=3){
                   //var stoneStr = "stone".plus(i.toString())
                   println("####DBDB#### ${turnDataList[turnNum-3].coX}, ${turnDataList[turnNum-3].coY}")
                   var z = 0
                   for(i in turnDataList){
                        //println("$i : ${turnDataList[z++].coX}, ${turnDataList[z++].coY}")
                       println("${z++} : $i")
                   }
                   val coX : Int = turnDataList[turnNum-3].coX
                   val coY : Int = turnDataList[turnNum-3].coY
                   var boardFinalStone = "board".plus(if(coX<10) "0".plus(coX) else coX.toString()).plus(if(coY<10) "0".plus(coY) else coY.toString())
                   //boardFinalStone에는 원래 돌로 바꿔야할 위치의 좌표값이 들어있습니다.
                   var targetStone : Int = resources.getIdentifier(boardFinalStone, "id", packageName)
                   //targetStone에는 R.id.board0000 꼴로 바꿔야할 id가 들어있습니다.

                   var targetImageButton : ImageButton = findViewById(targetStone)

                   //println("######### ${turnDataList[turnNum-3].coX}, ${turnDataList[turnNum-3].coY}")

                   when(turnNum%4){
                       3,2 -> {
                           checkStoneArray[coX][coY] = 0 // 체크 된 돌을 일반 돌로 바꾼다
                           changetoOriginalStone(targetImageButton, "black", coX, coY)
                       }
                       0,1 ->{
                           checkStoneArray[coX][coY] = 0 // 체크 된 돌을 일반 돌로 바꾼다
                           changetoOriginalStone(targetImageButton, "white", coX, coY)
                       }
                   }
               }

               turnNum++ // 한 턴이 진행되었음을 의미. 중요한 부분

               database.child("turnNum").setValue(turnNum.toString())

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

        database.child("turnNum").addChildEventListener(object:ChildEventListener{
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                var tempStr = snapshot.getValue().toString()
                turnNum = tempStr.toInt()
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

        //내가 화면에서 돌을 두기 위해 imageButton을 클릭하면 src의 변화를 주고, DB에 데이터를 변경시키는 부분
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
                    println("@@@@setonclicklist $turnNum@@@@")
                    var stoneStrUpload : String = ""
                    var stoneData : Turn = Turn("ERROR", "ERROR", -1, -1)

                    clickStoneChanged = false
                    stoneStrUpload = "stone".plus((turnNum).toString()) // "stone0" 꼴로 stoneStr에 저장이 된다.
                                                                     // 그 후 turnNum에 1을 더해 turn이 한 번 돌아갔음을 의미한다.

                    if(turnNum == 0){//만약 돌을 처음 두는 것이라면 앞으로 둘 돌들을 저장한 stones를 Firebase에 추가한다.
                        database.push().setValue("stones") // 처음 돌을 두는 과정이라면 push한다
                    }
                    when(turnNum%4){
                        1,0 -> { // turnNum(시도 횟수)가 4로 나눴을 때 1 또는 0이면 player 1 차례다.
                            stoneData = Turn(player1Name, stoneStrUpload, i, j)
                            checkStoneArray[i][j] = 1 // 지금 체크된 돌임을 명시
                            setStoneDependOnSize(imageButton, "black", i, j)
                        }
                        2,3 ->{ // 2,3이면 player 2 차례다
                            stoneData = Turn(player2Name, stoneStrUpload, i, j)
                            stoneData = Turn(player2Name, stoneStrUpload, i, j)
                            checkStoneArray[i][j] = 1 // 지금 체크된 돌임을 명시
                            setStoneDependOnSize(imageButton, "white", i, j)
                        }
                    }
                    database.child("stones").push().setValue(stoneData) // stoneData Db에 업로드합니다.

                    //turnDataList.add(stoneData) // 최근 돌의 색을 변화주기 위해서 지금까지 입력했던 데이터를 turnDataList에 저장합니다.
                    /*
                    if(turnNum>=3){
                        //var stoneStr = "stone".plus(i.toString())
                        println("####NOTDB##### ${turnDataList[turnNum-3].coX}, ${turnDataList[turnNum-3].coY}")
                        val coX : Int = turnDataList[turnNum-3].coX
                        val coY : Int = turnDataList[turnNum-3].coY
                        var boardFinalStone = "board".plus(if(coX<10) zero.plus(coX) else coX.toString()).plus(if(coY<10) zero.plus(coY) else coY.toString())
                        //boardFinalStone에는 원래 돌로 바꿔야할 위치의 좌표값이 들어있습니다.
                        var targetStone : Int = resources.getIdentifier(boardFinalStone, "id", packageName)
                        //targetStone에는 R.id.board0000 꼴로 바꿔야할 id가 들어있습니다.

                        var targetImageButton : ImageButton = findViewById(targetStone)

                        //println("######### ${turnDataList[turnNum-3].coX}, ${turnDataList[turnNum-3].coY}")

                        when(turnNum%4){
                            3,2 -> { // turnNum(시도 횟수)가 4로 나눴을 때 1 또는 0이면 player 1 차례다.
                                checkStoneArray[coX][coY] = 0 // 체크 된 돌을 일반 돌로 바꾼다
                                changetoOriginalStone(targetImageButton, "black", coX, coY)//바로 전 돌의 색을 초록색 돌로 바꿔준다.
                            }
                            1,0 ->{ // 2,3이면 player 2 차례다
                                checkStoneArray[coX][coY] = 0 // 체크 된 돌을 일반 돌로 바꾼다
                                changetoOriginalStone(targetImageButton, "white", coX, coY)//바로 전 돌의 색을 초록색 돌로 바꿔준다.
                            }
                        }
                    }

                     */

                    println("${turnNum}번째 돌 착수")
                    stoneCheckArray[i][j] = true // 좌표를 확인해서 배열에 착수가 되었다고 true 값 대입
                    imageButton.isClickable = false // 한 번 돌이 착수된 곳은 다시 클릭하지 못하도록 설정

                }
            }
        }

    }

    //초록색으로 두어졌던 돌들을 원래 초록색을 빼고 일반적인 돌로 바꿔줍니다.
    private fun changetoOriginalStone(imageButton: ImageButton, color:String,  i:Int, j : Int){
        println("%%% ${imageButton}, ${color}, ${i}, ${j}, ${clickCnt}")
        when(clickCnt){
            16->{ // size가 16이면
                if(color == "white") { // 흰색 둘 차례면
                    if(i==0 && j==0){ // 왼쪽 상단 코너일 때
                        imageButton.setImageResource(R.drawable.left_top_corner_circle_white16)
                    }else if(i==0 && j==18){ // 오른쪽 상단 코너일 때
                        imageButton.setImageResource(R.drawable.right_top_corner_circle_white16)
                    }
                    else if(i==0){ // 위일 때(0행)
                        imageButton.setImageResource(R.drawable.top_circle_white16)
                    }
                    else if(i==18 && j==0){//왼쪽 하단일 때
                        imageButton.setImageResource(R.drawable.left_bottom_corner_circle_white16)
                    }
                    else if(i==18&&j==18){ // 오른쪽 하단일 때
                        imageButton.setImageResource(R.drawable.right_bottom_corner_circle_white16)
                    }
                    else if(i==18){ // 아래일때(18행일 때)
                        imageButton.setImageResource(R.drawable.bottom_circle_white16)
                    }
                    else if(j==0){ // 왼쪽일 때(0열일때)
                        imageButton.setImageResource(R.drawable.left_circle_white16)
                    }
                    else if(j==18){ // 오른쪽일 때(18열일때)
                        imageButton.setImageResource(R.drawable.right_circle_white16)
                    }
                    else{ // 나머지일 때
                        imageButton.setImageResource(R.drawable.circle_white16)
                    }
                }
                else { // 검은색 둘 차례면
                    if(i==0 && j==0){ // 왼쪽 상단 코너일 때
                        imageButton.setImageResource(R.drawable.left_top_corner_circle_black16)
                    }else if(i==0 && j==18){ // 오른쪽 상단 코너일 때
                        imageButton.setImageResource(R.drawable.right_top_corner_circle_black16)
                    }
                    else if(i==0){ // 위일 때(0행)
                        imageButton.setImageResource(R.drawable.top_circle_black16)
                    }
                    else if(i==18 && j==0){//왼쪽 하단일 때
                        imageButton.setImageResource(R.drawable.left_bottom_corner_circle_black16)
                    }
                    else if(i==18&&j==18){ // 오른쪽 하단일 때
                        imageButton.setImageResource(R.drawable.right_bottom_corner_circle_black16)
                    }
                    else if(i==18){ // 아래일때(18행일 때)
                        imageButton.setImageResource(R.drawable.bottom_circle_black16)
                    }
                    else if(j==0){ // 왼쪽일 때(0열일때)
                        imageButton.setImageResource(R.drawable.left_circle_black16)
                    }
                    else if(j==18){ // 오른쪽일 때(18열일때)
                        imageButton.setImageResource(R.drawable.right_circle_black16)
                    }
                    else{ // 나머지일 때
                        imageButton.setImageResource(R.drawable.circle_black16)
                    }
                }
            }
            32->{ // size가 32이면
                if(color == "white"){
                    if(i==0 && j==0){ // 왼쪽 상단 코너일 때
                        imageButton.setImageResource(R.drawable.left_top_corner_circle_white32)
                    }else if(i==0 && j==18){ // 오른쪽 상단 코너일 때
                        imageButton.setImageResource(R.drawable.right_top_corner_circle_white32)
                    }
                    else if(i==0){ // 위일 때(0행)
                        imageButton.setImageResource(R.drawable.top_circle_white32)
                    }
                    else if(i==18 && j==0){//왼쪽 하단일 때
                        imageButton.setImageResource(R.drawable.left_bottom_corner_circle_white32)
                    }
                    else if(i==18&&j==18){ // 오른쪽 하단일 때
                        imageButton.setImageResource(R.drawable.right_bottom_corner_circle_white32)
                    }
                    else if(i==18){ // 아래일때(18행일 때)
                        imageButton.setImageResource(R.drawable.bottom_circle_white32)
                    }
                    else if(j==0){ // 왼쪽일 때(0열일때)
                        imageButton.setImageResource(R.drawable.left_circle_white32)
                    }
                    else if(j==18){ // 오른쪽일 때(18열일때)
                        imageButton.setImageResource(R.drawable.right_circle_white32)
                    }
                    else{ // 나머지일 때
                        imageButton.setImageResource(R.drawable.circle_white32)
                    }
                }
                else {
                    if(i==0 && j==0){ // 왼쪽 상단 코너일 때
                        imageButton.setImageResource(R.drawable.left_top_corner_circle_black32)
                    }else if(i==0 && j==18){ // 오른쪽 상단 코너일 때
                        imageButton.setImageResource(R.drawable.right_top_corner_circle_black32)
                    }
                    else if(i==0){ // 위일 때(0행)
                        imageButton.setImageResource(R.drawable.top_circle_black32)
                    }
                    else if(i==18 && j==0){//왼쪽 하단일 때
                        imageButton.setImageResource(R.drawable.left_bottom_corner_circle_black32)
                    }
                    else if(i==18&&j==18){ // 오른쪽 하단일 때
                        imageButton.setImageResource(R.drawable.right_bottom_corner_circle_black32)
                    }
                    else if(i==18){ // 아래일때(18행일 때)
                        imageButton.setImageResource(R.drawable.bottom_circle_black32)
                    }
                    else if(j==0){ // 왼쪽일 때(0열일때)
                        imageButton.setImageResource(R.drawable.left_circle_black32)
                    }
                    else if(j==18){ // 오른쪽일 때(18열일때)
                        imageButton.setImageResource(R.drawable.right_circle_black32)
                    }
                    else{ // 나머지일 때
                        imageButton.setImageResource(R.drawable.circle_black32)
                    }
                }
            }
            64->{ // size가 64이면
                if(color == "white") {
                    if(i==0 && j==0){ // 왼쪽 상단 코너일 때
                        imageButton.setImageResource(R.drawable.left_top_corner_circle_white64)
                    }else if(i==0 && j==18){ // 오른쪽 상단 코너일 때
                        imageButton.setImageResource(R.drawable.right_top_corner_circle_white64)
                    }
                    else if(i==0){ // 위일 때(0행)
                        imageButton.setImageResource(R.drawable.top_circle_white64)
                    }
                    else if(i==18 && j==0){//왼쪽 하단일 때
                        imageButton.setImageResource(R.drawable.left_bottom_corner_circle_white64)
                    }
                    else if(i==18&&j==18){ // 오른쪽 하단일 때
                        imageButton.setImageResource(R.drawable.right_bottom_corner_circle_white64)
                    }
                    else if(i==18){ // 아래일때(18행일 때)
                        imageButton.setImageResource(R.drawable.bottom_circle_white64)
                    }
                    else if(j==0){ // 왼쪽일 때(0열일때)
                        imageButton.setImageResource(R.drawable.left_circle_white64)
                    }
                    else if(j==18){ // 오른쪽일 때(18열일때)
                        imageButton.setImageResource(R.drawable.right_circle_white64)
                    }
                    else{ // 나머지일 때
                        imageButton.setImageResource(R.drawable.circle_white64)
                    }
                }
                else {
                    if(i==0 && j==0){ // 왼쪽 상단 코너일 때
                        imageButton.setImageResource(R.drawable.left_top_corner_circle_black64)
                    }else if(i==0 && j==18){ // 오른쪽 상단 코너일 때
                        imageButton.setImageResource(R.drawable.right_top_corner_circle_black64)
                    }
                    else if(i==0){ // 위일 때(0행)
                        imageButton.setImageResource(R.drawable.top_circle_black64)
                    }
                    else if(i==18 && j==0){//왼쪽 하단일 때
                        imageButton.setImageResource(R.drawable.left_bottom_corner_circle_black64)
                    }
                    else if(i==18&&j==18){ // 오른쪽 하단일 때
                        imageButton.setImageResource(R.drawable.right_bottom_corner_circle_black64)
                    }
                    else if(i==18){ // 아래일때(18행일 때)
                        imageButton.setImageResource(R.drawable.bottom_circle_black64)
                    }
                    else if(j==0){ // 왼쪽일 때(0열일때)
                        imageButton.setImageResource(R.drawable.left_circle_black64)
                    }
                    else if(j==18){ // 오른쪽일 때(18열일때)
                        imageButton.setImageResource(R.drawable.right_circle_black64)
                    }
                    else{ // 나머지일 때
                        imageButton.setImageResource(R.drawable.circle_black64)
                    }
                }
            }


        }
    }

    /*지금 화면에 보여지는 한 타일(바둑판 한 칸의 사이즈)의 크기(16,32,64)와 플레이어의 차례에 따라 색을 구분해
    * 바둑판에 돌을 두는 함수입니다.*/
    private fun setStoneDependOnSize(imageButton: ImageButton, color : String, i : Int, j : Int){
        //println("i : $i, j : $j")
        when(clickCnt){
            16->{ // size가 16이면
                if(color == "white") { // 흰색 둘 차례면
                    if(i==0 && j==0){ // 왼쪽 상단 코너일 때
                        imageButton.setImageResource(R.drawable.left_top_corner_circle_white_check16)
                    }else if(i==0 && j==18){ // 오른쪽 상단 코너일 때
                        imageButton.setImageResource(R.drawable.right_top_corner_circle_white_check16)
                    }
                    else if(i==0){ // 위일 때(0행)
                        imageButton.setImageResource(R.drawable.top_circle_white_check16)
                    }
                    else if(i==18 && j==0){//왼쪽 하단일 때
                        imageButton.setImageResource(R.drawable.left_bottom_corner_circle_white_check16)
                    }
                    else if(i==18&&j==18){ // 오른쪽 하단일 때
                        imageButton.setImageResource(R.drawable.right_bottom_corner_circle_white_check16)
                    }
                    else if(i==18){ // 아래일때(18행일 때)
                        imageButton.setImageResource(R.drawable.bottom_circle_white_check16)
                    }
                    else if(j==0){ // 왼쪽일 때(0열일때)
                        imageButton.setImageResource(R.drawable.left_circle_white_check16)
                    }
                    else if(j==18){ // 오른쪽일 때(18열일때)
                        imageButton.setImageResource(R.drawable.right_circle_white_check16)
                    }
                    else{ // 나머지일 때
                        imageButton.setImageResource(R.drawable.circle_white_check16)
                    }
                    stoneColorArray[i][j] = 2
                }
                else { // 검은색 둘 차례면
                    if(i==0 && j==0){ // 왼쪽 상단 코너일 때
                        imageButton.setImageResource(R.drawable.left_top_corner_circle_black_check16)
                    }else if(i==0 && j==18){ // 오른쪽 상단 코너일 때
                        imageButton.setImageResource(R.drawable.right_top_corner_circle_black_check16)
                    }
                    else if(i==0){ // 위일 때(0행)
                        imageButton.setImageResource(R.drawable.top_circle_black_check16)
                    }
                    else if(i==18 && j==0){//왼쪽 하단일 때
                        imageButton.setImageResource(R.drawable.left_bottom_corner_circle_black_check16)
                    }
                    else if(i==18&&j==18){ // 오른쪽 하단일 때
                        imageButton.setImageResource(R.drawable.right_bottom_corner_circle_black_check16)
                    }
                    else if(i==18){ // 아래일때(18행일 때)
                        imageButton.setImageResource(R.drawable.bottom_circle_black_check16)
                    }
                    else if(j==0){ // 왼쪽일 때(0열일때)
                        imageButton.setImageResource(R.drawable.left_circle_black_check16)
                    }
                    else if(j==18){ // 오른쪽일 때(18열일때)
                        imageButton.setImageResource(R.drawable.right_circle_black_check16)
                    }
                    else{ // 나머지일 때
                        imageButton.setImageResource(R.drawable.circle_black_check16)
                    }
                    stoneColorArray[i][j] = 1
                }

            }
            32->{ // size가 32이면
                if(color == "white"){
                    if(i==0 && j==0){ // 왼쪽 상단 코너일 때
                        imageButton.setImageResource(R.drawable.left_top_corner_circle_white_check32)
                    }else if(i==0 && j==18){ // 오른쪽 상단 코너일 때
                        imageButton.setImageResource(R.drawable.right_top_corner_circle_white_check32)
                    }
                    else if(i==0){ // 위일 때(0행)
                        imageButton.setImageResource(R.drawable.top_circle_white_check32)
                    }
                    else if(i==18 && j==0){//왼쪽 하단일 때
                        imageButton.setImageResource(R.drawable.left_bottom_corner_circle_white_check32)
                    }
                    else if(i==18&&j==18){ // 오른쪽 하단일 때
                        imageButton.setImageResource(R.drawable.right_bottom_corner_circle_white_check32)
                    }
                    else if(i==18){ // 아래일때(18행일 때)
                        imageButton.setImageResource(R.drawable.bottom_circle_white_check32)
                    }
                    else if(j==0){ // 왼쪽일 때(0열일때)
                        imageButton.setImageResource(R.drawable.left_circle_white_check32)
                    }
                    else if(j==18){ // 오른쪽일 때(18열일때)
                        imageButton.setImageResource(R.drawable.right_circle_white_check32)
                    }
                    else{ // 나머지일 때
                        imageButton.setImageResource(R.drawable.circle_white_check32)
                    }
                    stoneColorArray[i][j] = 2
                }
                else {
                    if(i==0 && j==0){ // 왼쪽 상단 코너일 때
                        imageButton.setImageResource(R.drawable.left_top_corner_circle_black_check32)
                    }else if(i==0 && j==18){ // 오른쪽 상단 코너일 때
                        imageButton.setImageResource(R.drawable.right_top_corner_circle_black_check32)
                    }
                    else if(i==0){ // 위일 때(0행)
                        imageButton.setImageResource(R.drawable.top_circle_black_check32)
                    }
                    else if(i==18 && j==0){//왼쪽 하단일 때
                        imageButton.setImageResource(R.drawable.left_bottom_corner_circle_black_check32)
                    }
                    else if(i==18&&j==18){ // 오른쪽 하단일 때
                        imageButton.setImageResource(R.drawable.right_bottom_corner_circle_black_check32)
                    }
                    else if(i==18){ // 아래일때(18행일 때)
                        imageButton.setImageResource(R.drawable.bottom_circle_black_check32)
                    }
                    else if(j==0){ // 왼쪽일 때(0열일때)
                        imageButton.setImageResource(R.drawable.left_circle_black_check32)
                    }
                    else if(j==18){ // 오른쪽일 때(18열일때)
                        imageButton.setImageResource(R.drawable.right_circle_black_check32)
                    }
                    else{ // 나머지일 때
                        imageButton.setImageResource(R.drawable.circle_black_check32)
                    }
                    stoneColorArray[i][j] = 1
                }
            }
            64->{ // size가 64이면
                if(color == "white") {
                    if(i==0 && j==0){ // 왼쪽 상단 코너일 때
                        imageButton.setImageResource(R.drawable.left_top_corner_circle_white_check64)
                    }else if(i==0 && j==18){ // 오른쪽 상단 코너일 때
                        imageButton.setImageResource(R.drawable.right_top_corner_circle_white_check64)
                    }
                    else if(i==0){ // 위일 때(0행)
                        imageButton.setImageResource(R.drawable.top_circle_white_check64)
                    }
                    else if(i==18 && j==0){//왼쪽 하단일 때
                        imageButton.setImageResource(R.drawable.left_bottom_corner_circle_white_check64)
                    }
                    else if(i==18&&j==18){ // 오른쪽 하단일 때
                        imageButton.setImageResource(R.drawable.right_bottom_corner_circle_white_check64)
                    }
                    else if(i==18){ // 아래일때(18행일 때)
                        imageButton.setImageResource(R.drawable.bottom_circle_white_check64)
                    }
                    else if(j==0){ // 왼쪽일 때(0열일때)
                        imageButton.setImageResource(R.drawable.left_circle_white_check64)
                    }
                    else if(j==18){ // 오른쪽일 때(18열일때)
                        imageButton.setImageResource(R.drawable.right_circle_white_check64)
                    }
                    else{ // 나머지일 때
                        imageButton.setImageResource(R.drawable.circle_white_check64)
                    }
                    stoneColorArray[i][j] = 2
                }
                else {
                    if(i==0 && j==0){ // 왼쪽 상단 코너일 때
                        imageButton.setImageResource(R.drawable.left_top_corner_circle_black_check64)
                    }else if(i==0 && j==18){ // 오른쪽 상단 코너일 때
                        imageButton.setImageResource(R.drawable.right_top_corner_circle_black_check64)
                    }
                    else if(i==0){ // 위일 때(0행)
                        imageButton.setImageResource(R.drawable.top_circle_black_check64)
                    }
                    else if(i==18 && j==0){//왼쪽 하단일 때
                        imageButton.setImageResource(R.drawable.left_bottom_corner_circle_black_check64)
                    }
                    else if(i==18&&j==18){ // 오른쪽 하단일 때
                        imageButton.setImageResource(R.drawable.right_bottom_corner_circle_black_check64)
                    }
                    else if(i==18){ // 아래일때(18행일 때)
                        imageButton.setImageResource(R.drawable.bottom_circle_black_check64)
                    }
                    else if(j==0){ // 왼쪽일 때(0열일때)
                        imageButton.setImageResource(R.drawable.left_circle_black_check64)
                    }
                    else if(j==18){ // 오른쪽일 때(18열일때)
                        imageButton.setImageResource(R.drawable.right_circle_black_check64)
                    }
                    else{ // 나머지일 때
                        imageButton.setImageResource(R.drawable.circle_black_check64)
                    }
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
        var tempCnt = 0
        database.addValueEventListener(object : ValueEventListener{
            @SuppressLint("ResourceType", "SetTextI18n")
            override fun onDataChange(snapshot: DataSnapshot) {
                for(i in snapshot.children){
                    tempCnt++
                    //println("##### tempCnt : $tempCnt")
                    //println("##### $nameFinished ${i.key.equals("player1Id")} ${i.value.toString().isNotEmpty()} ")
                    if(i.key.equals("player1Id") && i.value.toString().isNotEmpty() && !nameFinished && !isPlayer1Name){ // player1Id가 존재한다면
                        player1Name = enemyName
                        player2Name = myNickName
                        nameFinished = true
                        isPlayer1Name = true
                        //println("##### 1번들어옴")
                        user1Nickname.text = myNickName
                        user2Nickname.text = i.value.toString() // 상대편이 존재하는 것이므로 user2의 nickname을 설정해줍니다.
                        enemyName = i.value.toString()
                        serverUser2Name = true // 내가 서버에서 user2에 저장되어있음을 확인
                        database.child("player2Id").setValue(myNickName) // 나의 이름을 player2에 저장합니다.
                        break
                    }
                    else if(i.key.equals("player2Id") && i.value.toString().isNotEmpty() && !nameFinished && !isPlayer2Name){ // player2Id에 이미 누가 있을 경우
                        player1Name = myNickName
                        player2Name = enemyName
                        nameFinished = true
                        isPlayer2Name = true
                        //println("##### 2번들어옴")
                        user1Nickname.text = myNickName
                        user2Nickname.text = i.value.toString() // 상대편이 존재하는 것이므로 user2의 nickname을 설정해줍니다.
                        enemyName = i.value.toString()
                        serverUser1Name = true // 내가 서버에서 user1에 저장되어있음을 확인
                        database.child("player1Id").setValue(myNickName) // 나의 이름을 player1에 저장합니다.
                        break
                    }

                    if(i.key.equals("player1Id") && i.value.toString().isEmpty() ) { //player1이 나갔을 경우
                        user2Nickname.text = "Waiting..."
                    }
                    else if(i.key.equals("player2Id") && i.value.toString().isEmpty()){ // player2가 나갔을 때
                        user2Nickname.text = "Waiting..."
                    }

                } // 이 for문의 조건문에 맞는 항목이 없다면 방에 사람이 없다는 뜻이므로 아래에서 user1과 user1 tv위치에 값들을 설정해줍니다.

                //방에 사람이 아무도 없을 때
                if(!isPlayer1Name && !isPlayer2Name && (tempCnt>=3)) {
                    player1Name = myNickName
                    player2Name = enemyName
                    isPlayer1Name = true
                    user1Nickname.text = myNickName
                    database.child("player1Id").setValue(myNickName)
                }

            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase Error", "$error")
            }

        })
        /*
        if(!isPlayer1Name && !isPlayer2Name && !nameFinished){ // 만약 빈 방일 경우에, player1에 myNickname 설정
            println("##### $isPlayer1Name $isPlayer2Name $nameFinished")
            nameFinished = true
            isPlayer1Name = true
            user1Nickname.text = myNickName
            user2Nickname.text = "Waiting..."
            database.child("player1Id").setValue(myNickName) // 나의 이름을 player1에 저장합니다.
        }
        */
        /*if(intent.hasExtra("player1NickName")) player1Name = intent.getStringExtra("player1NickName").toString() // MainActivity에서 설정한 이름을 intent로 불러와서 player1Name에 저장한다.
        database.addValueEventListener(object : ValueEventListener{
            override fun onDataChange(snapshot: DataSnapshot) {
                for(i in snapshot.children){
                    /*if(i.key.equals("player1Id")){
                        player1Name = i.value as String
                    }*/
                    if(i.key.equals("player2Id")){
                        player2Name = i.value as String
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Name Error", "Can't get user name")
            }
        })*/


    }

    fun zoomBoard32(){
        for(i in 0..18){ // 행
            var str = "0"
            var boardStr = "board"

            var firstNum = if(i<10) str.plus(i.toString()) else i.toString() // board 뒤에 바로 붙는 첫 번째 수

            boardStr = boardStr.plus(firstNum) // board00까지 완성
            //Log.d("boardStr", boardStr)

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
                    1 -> { // TODO 여기서 네모체크 되어 있는 돌들 걸러줘서 그려야 할듯
                        if(checkStoneArray[i][j] == 1){ // 만약 체크된 돌이면 체크표시를 해서 그린다
                            imageButton.setImageResource(R.drawable.circle_black_check32)
                        }else {
                            imageButton.setImageResource(R.drawable.circle_black32)
                        }
                        continue // 돌에 대한 처리를 when에서 해주기 때문에 (i,j)의 돌에 대한 처리는 여기서 해줄 필요 없다.
                    }
                    2 -> {
                        if(checkStoneArray[i][j] == 1) { // 만약 체크된 돌이면 체크표시를 해서 그린다
                            imageButton.setImageResource(R.drawable.circle_white_check32)
                        }else {
                            imageButton.setImageResource(R.drawable.circle_white32)
                        }
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
            //Log.d("boardStr", boardStr)

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
                        if(checkStoneArray[i][j] == 1){ // 만약 체크된 돌이면 체크표시를 해서 그린다
                            imageButton.setImageResource(R.drawable.circle_black_check64)
                        }else {
                            imageButton.setImageResource(R.drawable.circle_black64)
                        }
                        continue // 돌에 대한 처리를 when에서 해주기 때문에 (i,j)의 돌에 대한 처리는 여기서 해줄 필요 없다.
                    }
                    2 -> {
                        if(checkStoneArray[i][j] == 1) { // 만약 체크된 돌이면 체크표시를 해서 그린다
                            imageButton.setImageResource(R.drawable.circle_white_check64)
                        }else {
                            imageButton.setImageResource(R.drawable.circle_white64)
                        }
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
            //Log.d("boardStr", boardStr)

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
                        if(checkStoneArray[i][j] == 1){ // 만약 체크된 돌이면 체크표시를 해서 그린다
                            imageButton.setImageResource(R.drawable.circle_black_check16)
                        }else {
                            imageButton.setImageResource(R.drawable.circle_black16)
                        }
                        continue // 돌에 대한 처리를 when에서 해주기 때문에 (i,j)의 돌에 대한 처리는 여기서 해줄 필요 없다.
                    }
                    2 -> {
                        if(checkStoneArray[i][j] == 1) { // 만약 체크된 돌이면 체크표시를 해서 그린다
                            imageButton.setImageResource(R.drawable.circle_white_check16)
                        }else {
                            imageButton.setImageResource(R.drawable.circle_white16)
                        }
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
        //println("@@@@PRINT BOARD@@@@")
        //Log.d("Print Board", "Print Board")
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
            print("$i : ")
            for(j in 0..18){
                print("${checkStoneArray[i][j]}".padStart(8))
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
    @SuppressLint("ResourceType")
    override fun onBackPressed(){
        if(System.currentTimeMillis() > backKeyPressedTime + 2500){ // 만약 클릭한 시간이 2.5초가 지났다면연(연속적으로 클릭한 것이 아닐 때)
            backKeyPressedTime = System.currentTimeMillis()
            Toast.makeText(this, "주의 : 한번 더 누르시면 게임이 나가집니다!", Toast.LENGTH_SHORT).show()
            return
        }

        if(System.currentTimeMillis() <= backKeyPressedTime + 2500){ // 만약 클릭한 시간이 2.5초가 이하라면(연속적으로 클릭했을 때)
            if(serverUser1Name){ // 서버의 player1Id에 내 닉네임이 저장되어 있다면
                var updateUserName = mutableMapOf<String, Any>()
                updateUserName["player1Id"] = "" // 내 이름이 있던 player1Id를 빈 곳(String)으로 변경
                database.updateChildren(updateUserName)//Firebase에 업데이트
            }else if(serverUser2Name){
                var updateUserName = mutableMapOf<String, Any>()
                updateUserName["player2Id"] = "" // 내 이름이 있던 player1Id를 빈 곳(String)으로 변경
                database.updateChildren(updateUserName)//Firebase에 업데이트
            }

            database.child("turnNum").setValue(1.toString()) // 이부분도 마찬가지로 beta때만 있으면 될듯, 어차피 방이 없어지면 다 삭제 되니..
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
        for(i in checkStoneArray.indices){
            for( j in checkStoneArray[i].indices){
                checkStoneArray[i][j] = 0 // 0은 일반 돌임을 명시
            }
        }
        printColorBoard()
    }

}


