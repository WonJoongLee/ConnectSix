package com.connectsix.connectsix

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.media.AudioManager
import android.media.SoundPool
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.Html
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.connectsix.connectsix.sharedRef.SharedData
import com.google.firebase.database.*
import kotlinx.android.synthetic.main.twoplayer_gameboard.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.roundToInt

class TwoplayerGameboard : AppCompatActivity() {

    var clickCnt = 64 // 초기 확대 값(default 값)은 53

    var player1Name: String = "" // 서버에서 player1 name
    var player2Name: String = "" // 서버에서 player2 name
    var myNickName: String = "" // 내 닉네임
    var enemyName: String = "" // 상대방 닉네임

    var serverUser1Name: Boolean = false //
    var serverUser2Name: Boolean = false

    var nameFinished: Boolean = false
    var isPlayer1Name: Boolean = false
    var isPlayer2Name: Boolean = false


    var turnNum: Int = 1 // turnNum 변수는 돌이 몇 개 착수되었는지 확인하기 위한 변수
    // 이거를 하나씩 올리면서 지금까지 돌이 몇개 올려졌는지 확인하고
    // DB의 stone+n값의 n값을 설정한다.

    var turnDataList = mutableListOf<Turn>()

    // 19*19 사이즈의 Boolean 배열(초기값 false)을 만들고 착수한 곳의 좌표를 확인한 후 true로 바꿔주기기
    var stoneCheckArray: Array<BooleanArray> =
        Array<BooleanArray>(19) { BooleanArray(19) } // 해당 위치에 돌이 착수되었는지 확인하는 array
    var stoneColorArray: Array<IntArray> =
        Array<IntArray>(19) { IntArray(19) } // 해당 위치에 무슨 색의 돌이 착수되었는지 확인하는 array 0 : 착수 안됨, 1 : 검은색, 2: 흰색
    var checkStoneArray: Array<IntArray> = Array<IntArray>(19) { IntArray(19) }
    var checkDiamondStone: Array<IntArray> = Array<IntArray>(19) { IntArray(19) }

    var winnerStr: String = "" // 이긴 사람 닉네임 string

    var clickStoneChanged: Boolean = false

    var roomKey = "" // 서버에서 방 key값
    var roomNum = "" // 방 번호

    var playerTurn = 0 // 1이면 player1차례이고, 2면 player2차례입니다.

    var stoneClickCnt = 0 // 빨간 다이아모든 위에 두 번 째 클릭인지 확인하는 변수입니다.

    var confirmX = -1 // user가 두기로 했던 빨간 diamond와 같은 위치에 돌을 두는지 확인하는 변수들입니다.
    var confirmY = -1 // 결국 좌표를 확인하기 위한 변수입니다.

    var myWinLoseRatio = "" // 승패 비율

    var isUserExit = false // 플레이어가 dialog에서 exit을 누르면 게임 보드 화면에서 나가는 것이므로 이 때 true로 바꿔줍니다.
    // 이 변수가 필요한 이유는, 이 변수를 사용하지 않으면 dialog가 두 번 띄워지는 오류가 발생합니다.

    var toastDone = false // 상대방이 나갔을 때 toast message 띄워주었는지 확인하기 위한 Boolean 값

    var isGameStart = false // turnNum이 2가 되면 게임 시작한 것으로 판단하며
    // 이 때부터 게임을 나가면 패배가 기록된다.

    var iAmGoingOut = false // 내가 나가려고 하는 경우 (onBackPressed를 연속적으로 두 번 눌렀을 때), 이 변수가 true 처리한다.
    // 이 변수가 필요한 이유는 showDialog가 승리, 패배 두 번 뜨는 것을 방지하기 위함이다.


    @SuppressLint("ShowToast", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.twoplayer_gameboard)
        startService(Intent(this, UnCatchTaskService::class.java))

        when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_NO -> { // Dark Mode 아닐 때
                user1Nickname.setTextColor(
                    ContextCompat.getColor(
                        applicationContext,
                        R.color.black
                    )
                )
                user2Nickname.setTextColor(
                    ContextCompat.getColor(
                        applicationContext,
                        R.color.black
                    )
                )
            }
            Configuration.UI_MODE_NIGHT_YES -> {
                user1Nickname.setTextColor(
                    ContextCompat.getColor(
                        applicationContext,
                        R.color.white
                    )
                )
                user2Nickname.setTextColor(
                    ContextCompat.getColor(
                        applicationContext,
                        R.color.white
                    )
                )
            }
        }

        var soundPool = SoundPool(5, AudioManager.STREAM_MUSIC, 0)
        var soundID = soundPool.load(this, R.raw.sound_stone, 1)

        if (intent.hasExtra("player1NickName")) myNickName =
            intent.getStringExtra("player1NickName")
                .toString() // MainActivity에서 설정한 이름을 intent로 불러와서 player1Name에 저장한다.
        if (intent.hasExtra("roomKey")) roomKey =
            intent.getStringExtra("roomKey").toString() // 유저가 접속하려는 방의 키를 불러와서 roomKey변수에 저장합니다.
        if (intent.hasExtra("roomNum")) roomNum = intent.getStringExtra("roomNum").toString()
        if (intent.hasExtra("winLoseRatio")) myWinLoseRatio =
            intent.getStringExtra("winLoseRatio").toString()// 승률 비율을 MainActivity로부터 넘겨받습니다.

        volumeControlStream = AudioManager.STREAM_MUSIC // 볼륨 키 올렸을 때 미디어 소리 제어 가능하도록 설정

        currentRoomNumber.text = roomNum

        val database: DatabaseReference =
            FirebaseDatabase.getInstance().reference.child("roomId").child(roomKey)

        database.child("player1Id").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                println("@@@@여기로 들어옴!!")
                if (snapshot.value == "") {
                    Log.e("USER", "LEFT")
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })


        val userDatabase = FirebaseDatabase.getInstance().reference.child("Users")
        userDatabase.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (i in snapshot.children) {
                    if (i.child("nickName").value.toString() == myNickName) {
                        user1Record.text =
                            i.child("win").value.toString().substringBefore(".").plus(
                                "/"
                            )
                                .plus(i.child("lose").value.toString()).substringBefore(".")
                                .plus("/")
                                .plus(i.child("ratio").value.toString())
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
            }

        })


        println(roomKey)

        getUserName(database) // 이름 설정을 합니다.
        initBoards()

        /*zoom out button이 눌려지면 각 button의 크기를 줄임
        * default 값은 64*/
        zoomOutButton.setOnClickListener { //축소
            println("@@@ $clickCnt!!")
            when (clickCnt) {
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
            when (clickCnt) {
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

        //database.child("player1Id").on

        //상대편이 들어왔을 경우 화면에 이름 띄워주고 승리했을 때 이름 띄워주기 위해 설정하는 부분
        // 상대방이 들어온 것을 감지 후 nickname을 설정한다.
        database.child("player1Id").addChildEventListener(object : ChildEventListener {
            @SuppressLint("ResourceType")
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                println("@@@ got in player1ID CHANGEd")
                //println("@@@ something changed : ${snapshot.value.toString().length}")
                if (snapshot.value.toString().isNotEmpty()) { // User가 들어왔을 경우
                    user1Nickname.text = snapshot.value.toString()

                } else if (snapshot.value.toString().isEmpty()) { // User가 나갔을 경우
                    user1Nickname.text = findViewById(R.string.waiting)
                }
            }

            @SuppressLint("ResourceType")
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
            }

            @SuppressLint("ResourceType")
            override fun onChildRemoved(snapshot: DataSnapshot) {
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        })

        database.child("player2Id").addChildEventListener(object : ChildEventListener {
            @SuppressLint("ResourceType")
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                if (snapshot.value.toString().isNotEmpty()) { // User가 들어왔을 경우
                    user2Nickname.text = snapshot.value.toString()
                    Log.e("User2", "ENTER")
                } else if (snapshot.value.toString().isEmpty()) { // User가 나갔을 경우
                    user2Nickname.text = findViewById(R.string.waiting)
                    user2Record.text = findViewById(R.string.waiting)
                    Log.e("User2", "LEFT")
                }
            }

            @SuppressLint("ResourceType")
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {
            }

            override fun onCancelled(error: DatabaseError) {
            }
        })

        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (serverUser1Name) {
                    when (turnNum % 4) {
                        1, 0 -> {
                            user1Turn.visibility = View.VISIBLE
                            user2Turn.visibility = View.INVISIBLE
                            playerTurn = 1
                            disableClick(playerTurn)
                        }
                        3, 2 -> {
                            user1Turn.visibility = View.INVISIBLE
                            user2Turn.visibility = View.VISIBLE
                            playerTurn = 2
                            disableClick(playerTurn)
                        }
                    }
                } else {
                    when (turnNum % 4) {
                        1, 0 -> {
                            user1Turn.visibility = View.INVISIBLE
                            user2Turn.visibility = View.VISIBLE
                            playerTurn = 1
                            disableClick(playerTurn)
                        }
                        2, 3 -> {
                            user1Turn.visibility = View.VISIBLE
                            user2Turn.visibility = View.INVISIBLE
                            playerTurn = 2
                            disableClick(playerTurn)
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })

        //데이터의 변화가 있을 때, 즉 상대방이 돌을 두면 DB 변화를 감지해서 내 화면에 변경된 점을 뿌려주는 부분
        database.child("stones").addChildEventListener(object : ChildEventListener {
            @SuppressLint("ResourceAsColor")
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val coX: String = snapshot.child("coX").getValue().toString()
                val coXInt = coX.toInt()

                val coY: String = snapshot.child("coY").getValue().toString()
                val coYInt: Int = coY.toInt()

                val boardCoordinate: String = changeCoordinateToBoard(coXInt, coYInt)
                val target: Int = resources.getIdentifier(boardCoordinate, "id", packageName)
                val imageButton: ImageButton = findViewById(target)

                clickStoneChanged = false
                turnDataList.add(Turn("", "", coXInt, coYInt))

                //println(turnDataList)

                when (turnNum % 4) {
                    1, 0 -> { // turnNum(시도 횟수)가 4로 나눴을 때 1 또는 0이면 player 1 차례다.
                        setStoneDependOnSize(imageButton, "black", coXInt, coYInt)
                    }
                    2, 3 -> { // 2,3이면 player 2 차례다
                        setStoneDependOnSize(imageButton, "white", coXInt, coYInt)
                    }
                }
                println("@@@turnNum is $turnNum")

                if (turnNum >= 3) {
                    val coX: Int = turnDataList[turnNum - 3].coX
                    val coY: Int = turnDataList[turnNum - 3].coY
                    val boardFinalStone =
                        "board".plus(if (coX < 10) "0".plus(coX) else coX.toString())
                            .plus(if (coY < 10) "0".plus(coY) else coY.toString())
                    //boardFinalStone에는 원래 돌로 바꿔야할 위치의 좌표값이 들어있습니다.
                    val targetStone: Int =
                        resources.getIdentifier(boardFinalStone, "id", packageName)
                    //targetStone에는 R.id.board0000 꼴로 바꿔야할 id가 들어있습니다.

                    val targetImageButton: ImageButton = findViewById(targetStone)
                    when (turnNum % 4) {
                        3, 2 -> {
                            checkStoneArray[coX][coY] = 0 // 체크 된 돌을 일반 돌로 바꾼다
                            changetoOriginalStone(targetImageButton, "black", coX, coY)
                        }
                        0, 1 -> {
                            checkStoneArray[coX][coY] = 0 // 체크 된 돌을 일반 돌로 바꾼다
                            changetoOriginalStone(targetImageButton, "white", coX, coY)
                        }
                    }
                }

                when (turnNum % 4) {
                    1, 0 -> { // turnNum(시도 횟수)가 4로 나눴을 때 1 또는 0이면 player 1 차례다.
                        turnNumTextView.text = turnNum.toString()
                        turnNumTextView.setTextColor(
                            ContextCompat.getColor(
                                applicationContext,
                                R.color.white
                            )
                        )
                        turnNumTextView.setBackgroundResource(R.drawable.turn_number_black)

                    }
                    2, 3 -> { // 2,3이면 player 2 차례다
                        turnNumTextView.text = turnNum.toString()
                        turnNumTextView.setTextColor(
                            ContextCompat.getColor(
                                applicationContext,
                                R.color.black
                            )
                        )
                        turnNumTextView.setBackgroundResource(R.drawable.turn_number_white)

                    }
                }

                soundPool.play(soundID, 1f, 1f, 0, 0, 1f);    // 돌 두는 소리가 나는 부분입니다
                turnNum++ // 한 턴이 진행되었음을 의미. 중요한 부분

                database.child("turnNum").setValue(turnNum.toString())

                judgeVictory(database)
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

        database.child("turnNum").addChildEventListener(object : ChildEventListener {
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
        for (i in 0..18) {
            val str = "0"
            var boardStr = "board"
            val firstNum =
                if (i < 10) str.plus(i.toString()) else i.toString() // board 뒤에 바로 붙는 첫 번째 수

            boardStr = boardStr.plus(firstNum) // board00까지 완성

            for (j in 0..18) {
                val boardAndFirstNum = boardStr // boardStr값은 변경되지 않도록 함
                val zero = "0"
                val boardFinal =
                    boardAndFirstNum.plus(if (j < 10) zero.plus(j) else j.toString()) // 만약 j가 10보다 작으면 j 앞에 0을 붙이고, j가 10이상이면 그대로 j를 저장
                //최종적으로 boardFinal에는 board0000꼴로 id가 저장되게 된다.

                val target: Int =
                    resources.getIdentifier(boardFinal, "id", packageName) // id 값을 target에 저장
                val imageButton: ImageButton =
                    findViewById(target) // id값(target)과 findViewById를 통해 imageButton 변수에 좌표에 해당하는 값 할당
                imageButton.setOnClickListener {

                    if (stoneClickCnt == 0) { // 처음 클릭한 것이면 확인하기 위해 클릭을 한 번 더하도록 유도합니다.
                        // 즉 해당 위치를 체크표시합니다.
                        setCheckStone(imageButton, i, j)
                        stoneClickCnt = 1
                        checkDiamondStone[i][j] = 1
                        println("#### checkdiamoned set zero000")

                        confirmX =
                            i // 사용자가 다시 이 위치를 클릭하는지 확인하기 위해 confirmX와 confirmY변수에 사용자가 클릭한 i,j를 저장합니다.
                        confirmY = j
                    } else if (stoneClickCnt == 1) { // 두 번째로 클릭한 것이면 해당 위치에 돌을 둡니다.
                        //println("@@@@setonclicklist $turnNum@@@@")
                        if (i == confirmX && j == confirmY) { // 두 번째로 돌을 둔 곳이 같은 위치라면 해당 위치에 돌을 둡니다.

                            var stoneStrUpload: String = ""
                            var stoneData: Turn = Turn("ERROR", "ERROR", -1, -1)

                            clickStoneChanged = false
                            stoneStrUpload =
                                "stone".plus((turnNum).toString()) // "stone0" 꼴로 stoneStr에 저장이 된다.
                            // 그 후 turnNum에 1을 더해 turn이 한 번 돌아갔음을 의미한다.

                            if (turnNum == 0) {//만약 돌을 처음 두는 것이라면 앞으로 둘 돌들을 저장한 stones를 Firebase에 추가한다.
                                database.push().setValue("stones") // 처음 돌을 두는 과정이라면 push한다
                            }
                            when (turnNum % 4) {
                                1, 0 -> { // turnNum(시도 횟수)가 4로 나눴을 때 1 또는 0이면 player 1 차례다.
                                    stoneData = Turn(player1Name, stoneStrUpload, i, j)
                                    checkStoneArray[i][j] = 1 // 지금 체크된 돌임을 명시
                                    setStoneDependOnSize(imageButton, "black", i, j)
                                }
                                2, 3 -> { // 2,3이면 player 2 차례다
                                    stoneData = Turn(player2Name, stoneStrUpload, i, j)
                                    stoneData = Turn(player2Name, stoneStrUpload, i, j)
                                    checkStoneArray[i][j] = 1 // 지금 체크된 돌임을 명시
                                    setStoneDependOnSize(imageButton, "white", i, j)
                                }
                            }
                            database.child("stones").push()
                                .setValue(stoneData) // stoneData Db에 업로드합니다.

                            //println("${turnNum}번째 돌 착수")
                            stoneCheckArray[i][j] = true // 좌표를 확인해서 배열에 착수가 되었다고 true 값 대입
                            imageButton.isClickable = false // 한 번 돌이 착수된 곳은 다시 클릭하지 못하도록 설정

                            stoneClickCnt = 0
                            checkDiamondStone[i][j] = 0
                            println("#### checkdiamoned set zero000")
                        } else { // 두 번째 클릭한 위치가 다른 위치라면(다이아몬드를 옮겨야 함)
                            println("@@@@ confirm : $confirmX $confirmY")
                            checkDiamondStone[confirmX][confirmY] =
                                0 // 해당 위치에서 다이아몬드가 사라졌기 때문에 다시 0으로 원상복귀합니다.

                            changeStoneDependSize(confirmX, confirmY)

                            setCheckStone(imageButton, i, j)
                            checkDiamondStone[i][j] = 1
                            confirmX = i
                            confirmY = j
                        }
                    }
                }
            }
        }

    }

    private fun changeStoneDependSize(i: Int, j: Int) {
        val boardStr = "board".plus(if (i < 10) "0".plus(i.toString()) else i.toString())
            .plus(if (j < 10) "0".plus(j.toString()) else j.toString())
        val target: Int = resources.getIdentifier(boardStr, "id", packageName)
        val imageButton: ImageButton = findViewById(target)

        when (clickCnt) {
            16 -> {
                if ((i == 3 && j == 3) || (i == 3 && j == 9) || (i == 3 && j == 15) || (i == 9 && j == 3) || (i == 9 && j == 9) || (i == 9 && j == 15) || (i == 15 && j == 3) || (i == 15 && j == 9) || (i == 15 && j == 15)) imageButton.setImageResource(
                    R.drawable.point16
                )
                else if (i != 0 && j == 0 && i != 18) imageButton.setImageResource(R.drawable.left16) // 왼쪽 부분일 때
                else if (i == 0 && j == 0) imageButton.setImageResource(R.drawable.left_top_corner16) // 왼쪽 위 코너일 때, (0,0)일 때
                else if (i == 18 && j == 0) imageButton.setImageResource(R.drawable.left_bottom_corner16) // 왼쪽 아래 코너일 때, (18,0)일 때
                else if (i != 0 && j == 18 && i != 18) imageButton.setImageResource(R.drawable.right16) // 오른쪽 일 때
                else if (i == 0 && j == 18) imageButton.setImageResource(R.drawable.right_top_corner16) // 오른쪽 위 코너일 때, (0,18)일 때
                else if (i == 18 && j == 18) imageButton.setImageResource(R.drawable.right_bottom_corner16) // 오른쪽 아래 코너일 때, (18,18)일 때
                else if (i == 18 && j != 0 && j != 19) imageButton.setImageResource(R.drawable.bottom16) // 아래일 때
                else if (i == 0 && j != 0 && j != 19) imageButton.setImageResource(R.drawable.top16) // 위일 때
                else imageButton.setImageResource(R.drawable.standard16) // imageButton의 image를 32 size로 축소
            }
            32 -> {
                if ((i == 3 && j == 3) || (i == 3 && j == 9) || (i == 3 && j == 15) || (i == 9 && j == 3) || (i == 9 && j == 9) || (i == 9 && j == 15) || (i == 15 && j == 3) || (i == 15 && j == 9) || (i == 15 && j == 15)) imageButton.setImageResource(
                    R.drawable.point32
                )
                else if (i != 0 && j == 0 && i != 18) imageButton.setImageResource(R.drawable.left32) // 왼쪽 부분일 때
                else if (i == 0 && j == 0) imageButton.setImageResource(R.drawable.left_top_corner32) // 왼쪽 위 코너일 때, (0,0)일 때
                else if (i == 18 && j == 0) imageButton.setImageResource(R.drawable.left_bottom_corner32) // 왼쪽 아래 코너일 때, (18,0)일 때
                else if (i != 0 && j == 18 && i != 18) imageButton.setImageResource(R.drawable.right32) // 오른쪽 일 때
                else if (i == 0 && j == 18) imageButton.setImageResource(R.drawable.right_top_corner32) // 오른쪽 위 코너일 때, (0,18)일 때
                else if (i == 18 && j == 18) imageButton.setImageResource(R.drawable.right_bottom_corner32) // 오른쪽 아래 코너일 때, (18,18)일 때
                else if (i == 18 && j != 0 && j != 19) imageButton.setImageResource(R.drawable.bottom32) // 아래일 때
                else if (i == 0 && j != 0 && j != 19) imageButton.setImageResource(R.drawable.top32) // 위일 때
                else imageButton.setImageResource(R.drawable.standard32) // imageButton의 image를 32 size로 축소
            }
            64 -> {
                if ((i == 3 && j == 3) || (i == 3 && j == 9) || (i == 3 && j == 15) || (i == 9 && j == 3) || (i == 9 && j == 9) || (i == 9 && j == 15) || (i == 15 && j == 3) || (i == 15 && j == 9) || (i == 15 && j == 15)) imageButton.setImageResource(
                    R.drawable.point64
                )
                else if (i != 0 && j == 0 && i != 18) imageButton.setImageResource(R.drawable.left64) // 왼쪽 부분일 때
                else if (i == 0 && j == 0) imageButton.setImageResource(R.drawable.left_top_corner64) // 왼쪽 위 코너일 때, (0,0)일 때
                else if (i == 18 && j == 0) imageButton.setImageResource(R.drawable.left_bottom_corner64) // 왼쪽 아래 코너일 때, (18,0)일 때
                else if (i != 0 && j == 18 && i != 18) imageButton.setImageResource(R.drawable.right64) // 오른쪽 일 때
                else if (i == 0 && j == 18) imageButton.setImageResource(R.drawable.right_top_corner64) // 오른쪽 위 코너일 때, (0,18)일 때
                else if (i == 18 && j == 18) imageButton.setImageResource(R.drawable.right_bottom_corner64) // 오른쪽 아래 코너일 때, (18,18)일 때
                else if (i == 18 && j != 0 && j != 19) imageButton.setImageResource(R.drawable.bottom64) // 아래일 때
                else if (i == 0 && j != 0 && j != 19) imageButton.setImageResource(R.drawable.top64) // 위일 때
                else imageButton.setImageResource(R.drawable.standard64) // imageButton의 image를 32 size로 축소
            }
        }
    }

    private fun disableClick(pTurn: Int) {
        if ((player1Name == myNickName && pTurn == 2) || (player2Name == myNickName && pTurn == 1)) {
                // 서버 player1Id가 내 닉네임이고, 내 차례가 아니라면(pTurn == 2) 또는 서버 player2Id가 내 닉네임이고, 내 차례가 아니라면(pTurn==1)
                // 즉, 모든 버튼을 클릭 불가능하도록 설정하는 부분입니다.
            var boardStr: String
            var boardIStr: String // board00꼴까지 만들고 board00에 저장합니다.
            for (i in 0..18) {
                boardStr = "board"
                boardStr =
                    boardStr.plus(if (i < 10) "0".plus(i.toString()) else i.toString()) // board00까지 만들고 boardStr에 저장합니다.
                for (j in 0..18) {
                    boardIStr = boardStr
                    boardIStr =
                        boardIStr.plus(if (j < 10) "0".plus(j.toString()) else j.toString()) // board0000까지 만들고 boardIStr에 저장합니다.
                    val imageButton: ImageButton =
                        findViewById(resources.getIdentifier(boardIStr, "id", packageName))
                    imageButton.isClickable = false
                }
            }
        } else { // 위 상황이 아니라면 imageButton을 click 가능하게 합니다.
            var boardStr: String
            var boardIStr: String
            for (i in 0..18) {
                boardStr = "board"
                boardStr = boardStr.plus(if (i < 10) "0".plus(i.toString()) else i.toString())
                for (j in 0..18) {
                    boardIStr = boardStr
                    boardIStr = boardIStr.plus(if (j < 10) "0".plus(j.toString()) else j.toString())
                    val imageButton: ImageButton =
                        findViewById(resources.getIdentifier(boardIStr, "id", packageName))
                    for (k in turnDataList) { // turnDatalist에 있는 x좌표 y좌표는 계속 clickable이 false여야 한다. 그러므로 이 for문에서 걸러줍니다.
                        if (k.coX == i && k.coY == j) { // 지금 현재 가공하는 i,j가 turnDataList에 있는 x좌표, y좌표와 같다면
                            imageButton.isClickable =
                                false // clickable을 false로 바꾸고 for문을 탈출한다. 더 이상 clickable이 true로 바뀌지 않도록 탈출해야 합니다.
                            break
                        } else {
                            imageButton.isClickable = true
                        }
                    }
                }
            }
        }
    }


    // 최근에 둔 돌이어서 체크되었던 돌들을 체크되지 않은 일반적인 돌로 바꿔줍니다.
    private fun changetoOriginalStone(imageButton: ImageButton, color: String, i: Int, j: Int) {
        //println("%%% ${imageButton}, ${color}, ${i}, ${j}, $clickCnt")
        when (clickCnt) {
            16 -> { // size가 16이면
                if (color == "white") { // 흰색 둘 차례면
                    if (i == 0 && j == 0) { // 왼쪽 상단 코너일 때
                        imageButton.setImageResource(R.drawable.left_top_corner_circle_white16)
                    } else if (i == 0 && j == 18) { // 오른쪽 상단 코너일 때
                        imageButton.setImageResource(R.drawable.right_top_corner_circle_white16)
                    } else if (i == 0) { // 위일 때(0행)
                        imageButton.setImageResource(R.drawable.top_circle_white16)
                    } else if (i == 18 && j == 0) {//왼쪽 하단일 때
                        imageButton.setImageResource(R.drawable.left_bottom_corner_circle_white16)
                    } else if (i == 18 && j == 18) { // 오른쪽 하단일 때
                        imageButton.setImageResource(R.drawable.right_bottom_corner_circle_white16)
                    } else if (i == 18) { // 아래일때(18행일 때)
                        imageButton.setImageResource(R.drawable.bottom_circle_white16)
                    } else if (j == 0) { // 왼쪽일 때(0열일때)
                        imageButton.setImageResource(R.drawable.left_circle_white16)
                    } else if (j == 18) { // 오른쪽일 때(18열일때)
                        imageButton.setImageResource(R.drawable.right_circle_white16)
                    } else { // 나머지일 때
                        imageButton.setImageResource(R.drawable.circle_white16)
                    }
                } else { // 검은색 둘 차례면
                    if (i == 0 && j == 0) { // 왼쪽 상단 코너일 때
                        imageButton.setImageResource(R.drawable.left_top_corner_circle_black16)
                    } else if (i == 0 && j == 18) { // 오른쪽 상단 코너일 때
                        imageButton.setImageResource(R.drawable.right_top_corner_circle_black16)
                    } else if (i == 0) { // 위일 때(0행)
                        imageButton.setImageResource(R.drawable.top_circle_black16)
                    } else if (i == 18 && j == 0) {//왼쪽 하단일 때
                        imageButton.setImageResource(R.drawable.left_bottom_corner_circle_black16)
                    } else if (i == 18 && j == 18) { // 오른쪽 하단일 때
                        imageButton.setImageResource(R.drawable.right_bottom_corner_circle_black16)
                    } else if (i == 18) { // 아래일때(18행일 때)
                        imageButton.setImageResource(R.drawable.bottom_circle_black16)
                    } else if (j == 0) { // 왼쪽일 때(0열일때)
                        imageButton.setImageResource(R.drawable.left_circle_black16)
                    } else if (j == 18) { // 오른쪽일 때(18열일때)
                        imageButton.setImageResource(R.drawable.right_circle_black16)
                    } else { // 나머지일 때
                        imageButton.setImageResource(R.drawable.circle_black16)
                    }
                }
            }
            32 -> { // size가 32이면
                if (color == "white") {
                    if (i == 0 && j == 0) { // 왼쪽 상단 코너일 때
                        imageButton.setImageResource(R.drawable.left_top_corner_circle_white32)
                    } else if (i == 0 && j == 18) { // 오른쪽 상단 코너일 때
                        imageButton.setImageResource(R.drawable.right_top_corner_circle_white32)
                    } else if (i == 0) { // 위일 때(0행)
                        imageButton.setImageResource(R.drawable.top_circle_white32)
                    } else if (i == 18 && j == 0) {//왼쪽 하단일 때
                        imageButton.setImageResource(R.drawable.left_bottom_corner_circle_white32)
                    } else if (i == 18 && j == 18) { // 오른쪽 하단일 때
                        imageButton.setImageResource(R.drawable.right_bottom_corner_circle_white32)
                    } else if (i == 18) { // 아래일때(18행일 때)
                        imageButton.setImageResource(R.drawable.bottom_circle_white32)
                    } else if (j == 0) { // 왼쪽일 때(0열일때)
                        imageButton.setImageResource(R.drawable.left_circle_white32)
                    } else if (j == 18) { // 오른쪽일 때(18열일때)
                        imageButton.setImageResource(R.drawable.right_circle_white32)
                    } else { // 나머지일 때
                        imageButton.setImageResource(R.drawable.circle_white32)
                    }
                } else {
                    if (i == 0 && j == 0) { // 왼쪽 상단 코너일 때
                        imageButton.setImageResource(R.drawable.left_top_corner_circle_black32)
                    } else if (i == 0 && j == 18) { // 오른쪽 상단 코너일 때
                        imageButton.setImageResource(R.drawable.right_top_corner_circle_black32)
                    } else if (i == 0) { // 위일 때(0행)
                        imageButton.setImageResource(R.drawable.top_circle_black32)
                    } else if (i == 18 && j == 0) {//왼쪽 하단일 때
                        imageButton.setImageResource(R.drawable.left_bottom_corner_circle_black32)
                    } else if (i == 18 && j == 18) { // 오른쪽 하단일 때
                        imageButton.setImageResource(R.drawable.right_bottom_corner_circle_black32)
                    } else if (i == 18) { // 아래일때(18행일 때)
                        imageButton.setImageResource(R.drawable.bottom_circle_black32)
                    } else if (j == 0) { // 왼쪽일 때(0열일때)
                        imageButton.setImageResource(R.drawable.left_circle_black32)
                    } else if (j == 18) { // 오른쪽일 때(18열일때)
                        imageButton.setImageResource(R.drawable.right_circle_black32)
                    } else { // 나머지일 때
                        imageButton.setImageResource(R.drawable.circle_black32)
                    }
                }
            }
            64 -> { // size가 64이면
                if (color == "white") {
                    if (i == 0 && j == 0) { // 왼쪽 상단 코너일 때
                        imageButton.setImageResource(R.drawable.left_top_corner_circle_white64)
                    } else if (i == 0 && j == 18) { // 오른쪽 상단 코너일 때
                        imageButton.setImageResource(R.drawable.right_top_corner_circle_white64)
                    } else if (i == 0) { // 위일 때(0행)
                        imageButton.setImageResource(R.drawable.top_circle_white64)
                    } else if (i == 18 && j == 0) {//왼쪽 하단일 때
                        imageButton.setImageResource(R.drawable.left_bottom_corner_circle_white64)
                    } else if (i == 18 && j == 18) { // 오른쪽 하단일 때
                        imageButton.setImageResource(R.drawable.right_bottom_corner_circle_white64)
                    } else if (i == 18) { // 아래일때(18행일 때)
                        imageButton.setImageResource(R.drawable.bottom_circle_white64)
                    } else if (j == 0) { // 왼쪽일 때(0열일때)
                        imageButton.setImageResource(R.drawable.left_circle_white64)
                    } else if (j == 18) { // 오른쪽일 때(18열일때)
                        imageButton.setImageResource(R.drawable.right_circle_white64)
                    } else { // 나머지일 때
                        imageButton.setImageResource(R.drawable.circle_white64)
                    }
                } else {
                    if (i == 0 && j == 0) { // 왼쪽 상단 코너일 때
                        imageButton.setImageResource(R.drawable.left_top_corner_circle_black64)
                    } else if (i == 0 && j == 18) { // 오른쪽 상단 코너일 때
                        imageButton.setImageResource(R.drawable.right_top_corner_circle_black64)
                    } else if (i == 0) { // 위일 때(0행)
                        imageButton.setImageResource(R.drawable.top_circle_black64)
                    } else if (i == 18 && j == 0) {//왼쪽 하단일 때
                        imageButton.setImageResource(R.drawable.left_bottom_corner_circle_black64)
                    } else if (i == 18 && j == 18) { // 오른쪽 하단일 때
                        imageButton.setImageResource(R.drawable.right_bottom_corner_circle_black64)
                    } else if (i == 18) { // 아래일때(18행일 때)
                        imageButton.setImageResource(R.drawable.bottom_circle_black64)
                    } else if (j == 0) { // 왼쪽일 때(0열일때)
                        imageButton.setImageResource(R.drawable.left_circle_black64)
                    } else if (j == 18) { // 오른쪽일 때(18열일때)
                        imageButton.setImageResource(R.drawable.right_circle_black64)
                    } else { // 나머지일 때
                        imageButton.setImageResource(R.drawable.circle_black64)
                    }
                }
            }


        }
    }

    private fun setCheckStoneDependSize(imageButton: ImageButton, i: Int, j: Int, size: Int) {
        when (size) {
            16 -> { // size가 16이면
                if (i == 0 && j == 0) { // 왼쪽 상단 코너일 때
                    imageButton.setImageResource(R.drawable.left_top_corner_confirm16)
                } else if (i == 0 && j == 18) { // 오른쪽 상단 코너일 때
                    imageButton.setImageResource(R.drawable.right_top_corner_confirm16)
                } else if (i == 0) { // 위일 때(0행)
                    imageButton.setImageResource(R.drawable.top_confrim16)
                } else if (i == 18 && j == 0) {//왼쪽 하단일 때
                    imageButton.setImageResource(R.drawable.left_bottom_corner_confirm16)
                } else if (i == 18 && j == 18) { // 오른쪽 하단일 때
                    imageButton.setImageResource(R.drawable.right_bottom_corner_confirm16)
                } else if (i == 18) { // 아래일때(18행일 때)
                    imageButton.setImageResource(R.drawable.bottom_confirm16)
                } else if (j == 0) { // 왼쪽일 때(0열일때)
                    imageButton.setImageResource(R.drawable.left_confirm16)
                } else if (j == 18) { // 오른쪽일 때(18열일때)
                    imageButton.setImageResource(R.drawable.right_confirm16)
                } else { // 나머지일 때
                    println("@@@ 그렸다")
                    imageButton.setImageResource(R.drawable.standard_confirm16)
                }
            }
            32 -> {
                if (i == 0 && j == 0) { // 왼쪽 상단 코너일 때
                    imageButton.setImageResource(R.drawable.left_top_corner_confirm32)
                } else if (i == 0 && j == 18) { // 오른쪽 상단 코너일 때
                    imageButton.setImageResource(R.drawable.right_top_corner_confirm32)
                } else if (i == 0) { // 위일 때(0행)
                    imageButton.setImageResource(R.drawable.top_confrim32)
                } else if (i == 18 && j == 0) {//왼쪽 하단일 때
                    imageButton.setImageResource(R.drawable.left_bottom_corner_confirm32)
                } else if (i == 18 && j == 18) { // 오른쪽 하단일 때
                    imageButton.setImageResource(R.drawable.right_bottom_corner_confirm32)
                } else if (i == 18) { // 아래일때(18행일 때)
                    imageButton.setImageResource(R.drawable.bottom_confirm32)
                } else if (j == 0) { // 왼쪽일 때(0열일때)
                    imageButton.setImageResource(R.drawable.left_confirm32)
                } else if (j == 18) { // 오른쪽일 때(18열일때)
                    imageButton.setImageResource(R.drawable.right_confirm32)
                } else { // 나머지일 때
                    println("@@@ 그렸다")
                    imageButton.setImageResource(R.drawable.standard_confirm32)
                }
            }
            64 -> {
                if (i == 0 && j == 0) { // 왼쪽 상단 코너일 때
                    imageButton.setImageResource(R.drawable.left_top_corner_confirm64)
                } else if (i == 0 && j == 18) { // 오른쪽 상단 코너일 때
                    imageButton.setImageResource(R.drawable.right_top_corner_confirm64)
                } else if (i == 0) { // 위일 때(0행)
                    imageButton.setImageResource(R.drawable.top_confrim64)
                } else if (i == 18 && j == 0) {//왼쪽 하단일 때
                    imageButton.setImageResource(R.drawable.left_bottom_corner_confirm64)
                } else if (i == 18 && j == 18) { // 오른쪽 하단일 때
                    imageButton.setImageResource(R.drawable.right_bottom_corner_confirm64)
                } else if (i == 18) { // 아래일때(18행일 때)
                    imageButton.setImageResource(R.drawable.bottom_confirm64)
                } else if (j == 0) { // 왼쪽일 때(0열일때)
                    imageButton.setImageResource(R.drawable.left_confirm64)
                } else if (j == 18) { // 오른쪽일 때(18열일때)
                    imageButton.setImageResource(R.drawable.right_confirm64)
                } else { // 나머지일 때
                    println("@@@ 그렸다")
                    imageButton.setImageResource(R.drawable.standard_confirm64)
                }
            }
        }
    }


    private fun setCheckStone(imageButton: ImageButton, i: Int, j: Int) {
        println("@@@@@ imageButton : $imageButton i : $i, j : $j")
        println("@@@그리기 직전 cnt : $clickCnt")
        when (clickCnt) {
            16 -> { // size가 16이면
                if (i == 0 && j == 0) { // 왼쪽 상단 코너일 때
                    imageButton.setImageResource(R.drawable.left_top_corner_confirm16)
                } else if (i == 0 && j == 18) { // 오른쪽 상단 코너일 때
                    imageButton.setImageResource(R.drawable.right_top_corner_confirm16)
                } else if (i == 0) { // 위일 때(0행)
                    imageButton.setImageResource(R.drawable.top_confrim16)
                } else if (i == 18 && j == 0) {//왼쪽 하단일 때
                    imageButton.setImageResource(R.drawable.left_bottom_corner_confirm16)
                } else if (i == 18 && j == 18) { // 오른쪽 하단일 때
                    imageButton.setImageResource(R.drawable.right_bottom_corner_confirm16)
                } else if (i == 18) { // 아래일때(18행일 때)
                    imageButton.setImageResource(R.drawable.bottom_confirm16)
                } else if (j == 0) { // 왼쪽일 때(0열일때)
                    imageButton.setImageResource(R.drawable.left_confirm16)
                } else if (j == 18) { // 오른쪽일 때(18열일때)
                    imageButton.setImageResource(R.drawable.right_confirm16)
                } else { // 나머지일 때
                    println("@@@ 그렸다")
                    imageButton.setImageResource(R.drawable.standard_confirm16)
                }
            }
            32 -> {
                if (i == 0 && j == 0) { // 왼쪽 상단 코너일 때
                    imageButton.setImageResource(R.drawable.left_top_corner_confirm32)
                } else if (i == 0 && j == 18) { // 오른쪽 상단 코너일 때
                    imageButton.setImageResource(R.drawable.right_top_corner_confirm32)
                } else if (i == 0) { // 위일 때(0행)
                    imageButton.setImageResource(R.drawable.top_confrim32)
                } else if (i == 18 && j == 0) {//왼쪽 하단일 때
                    imageButton.setImageResource(R.drawable.left_bottom_corner_confirm32)
                } else if (i == 18 && j == 18) { // 오른쪽 하단일 때
                    imageButton.setImageResource(R.drawable.right_bottom_corner_confirm32)
                } else if (i == 18) { // 아래일때(18행일 때)
                    imageButton.setImageResource(R.drawable.bottom_confirm32)
                } else if (j == 0) { // 왼쪽일 때(0열일때)
                    imageButton.setImageResource(R.drawable.left_confirm32)
                } else if (j == 18) { // 오른쪽일 때(18열일때)
                    imageButton.setImageResource(R.drawable.right_confirm32)
                } else { // 나머지일 때
                    println("@@@ 그렸다")
                    imageButton.setImageResource(R.drawable.standard_confirm32)
                }
            }
            64 -> {
                if (i == 0 && j == 0) { // 왼쪽 상단 코너일 때
                    imageButton.setImageResource(R.drawable.left_top_corner_confirm64)
                } else if (i == 0 && j == 18) { // 오른쪽 상단 코너일 때
                    imageButton.setImageResource(R.drawable.right_top_corner_confirm64)
                } else if (i == 0) { // 위일 때(0행)
                    imageButton.setImageResource(R.drawable.top_confrim64)
                } else if (i == 18 && j == 0) {//왼쪽 하단일 때
                    imageButton.setImageResource(R.drawable.left_bottom_corner_confirm64)
                } else if (i == 18 && j == 18) { // 오른쪽 하단일 때
                    imageButton.setImageResource(R.drawable.right_bottom_corner_confirm64)
                } else if (i == 18) { // 아래일때(18행일 때)
                    imageButton.setImageResource(R.drawable.bottom_confirm64)
                } else if (j == 0) { // 왼쪽일 때(0열일때)
                    imageButton.setImageResource(R.drawable.left_confirm64)
                } else if (j == 18) { // 오른쪽일 때(18열일때)
                    imageButton.setImageResource(R.drawable.right_confirm64)
                } else { // 나머지일 때
                    println("@@@ 그렸다")
                    imageButton.setImageResource(R.drawable.standard_confirm64)
                }
            }
        }
    }

    /*지금 화면에 보여지는 한 타일(바둑판 한 칸의 사이즈)의 크기(16,32,64)와 플레이어의 차례에 따라 색을 구분해
    * 바둑판에 돌을 두는 함수입니다.*/
    private fun setStoneDependOnSize(imageButton: ImageButton, color: String, i: Int, j: Int) {
        //println("i : $i, j : $j")
        when (clickCnt) {
            16 -> { // size가 16이면
                if (color == "white") { // 흰색 둘 차례면
                    if (i == 0 && j == 0) { // 왼쪽 상단 코너일 때
                        imageButton.setImageResource(R.drawable.left_top_corner_circle_white_check16)
                    } else if (i == 0 && j == 18) { // 오른쪽 상단 코너일 때
                        imageButton.setImageResource(R.drawable.right_top_corner_circle_white_check16)
                    } else if (i == 0) { // 위일 때(0행)
                        imageButton.setImageResource(R.drawable.top_circle_white_check16)
                    } else if (i == 18 && j == 0) {//왼쪽 하단일 때
                        imageButton.setImageResource(R.drawable.left_bottom_corner_circle_white_check16)
                    } else if (i == 18 && j == 18) { // 오른쪽 하단일 때
                        imageButton.setImageResource(R.drawable.right_bottom_corner_circle_white_check16)
                    } else if (i == 18) { // 아래일때(18행일 때)
                        imageButton.setImageResource(R.drawable.bottom_circle_white_check16)
                    } else if (j == 0) { // 왼쪽일 때(0열일때)
                        imageButton.setImageResource(R.drawable.left_circle_white_check16)
                    } else if (j == 18) { // 오른쪽일 때(18열일때)
                        imageButton.setImageResource(R.drawable.right_circle_white_check16)
                    } else { // 나머지일 때
                        imageButton.setImageResource(R.drawable.circle_white_check16)
                    }
                    stoneColorArray[i][j] = 2
                } else { // 검은색 둘 차례면
                    if (i == 0 && j == 0) { // 왼쪽 상단 코너일 때
                        imageButton.setImageResource(R.drawable.left_top_corner_circle_black_check16)
                    } else if (i == 0 && j == 18) { // 오른쪽 상단 코너일 때
                        imageButton.setImageResource(R.drawable.right_top_corner_circle_black_check16)
                    } else if (i == 0) { // 위일 때(0행)
                        imageButton.setImageResource(R.drawable.top_circle_black_check16)
                    } else if (i == 18 && j == 0) {//왼쪽 하단일 때
                        imageButton.setImageResource(R.drawable.left_bottom_corner_circle_black_check16)
                    } else if (i == 18 && j == 18) { // 오른쪽 하단일 때
                        imageButton.setImageResource(R.drawable.right_bottom_corner_circle_black_check16)
                    } else if (i == 18) { // 아래일때(18행일 때)
                        imageButton.setImageResource(R.drawable.bottom_circle_black_check16)
                    } else if (j == 0) { // 왼쪽일 때(0열일때)
                        imageButton.setImageResource(R.drawable.left_circle_black_check16)
                    } else if (j == 18) { // 오른쪽일 때(18열일때)
                        imageButton.setImageResource(R.drawable.right_circle_black_check16)
                    } else { // 나머지일 때
                        imageButton.setImageResource(R.drawable.circle_black_check16)
                    }
                    stoneColorArray[i][j] = 1
                }

            }
            32 -> { // size가 32이면
                if (color == "white") {
                    if (i == 0 && j == 0) { // 왼쪽 상단 코너일 때
                        imageButton.setImageResource(R.drawable.left_top_corner_circle_white_check32)
                    } else if (i == 0 && j == 18) { // 오른쪽 상단 코너일 때
                        imageButton.setImageResource(R.drawable.right_top_corner_circle_white_check32)
                    } else if (i == 0) { // 위일 때(0행)
                        imageButton.setImageResource(R.drawable.top_circle_white_check32)
                    } else if (i == 18 && j == 0) {//왼쪽 하단일 때
                        imageButton.setImageResource(R.drawable.left_bottom_corner_circle_white_check32)
                    } else if (i == 18 && j == 18) { // 오른쪽 하단일 때
                        imageButton.setImageResource(R.drawable.right_bottom_corner_circle_white_check32)
                    } else if (i == 18) { // 아래일때(18행일 때)
                        imageButton.setImageResource(R.drawable.bottom_circle_white_check32)
                    } else if (j == 0) { // 왼쪽일 때(0열일때)
                        imageButton.setImageResource(R.drawable.left_circle_white_check32)
                    } else if (j == 18) { // 오른쪽일 때(18열일때)
                        imageButton.setImageResource(R.drawable.right_circle_white_check32)
                    } else { // 나머지일 때
                        imageButton.setImageResource(R.drawable.circle_white_check32)
                    }
                    stoneColorArray[i][j] = 2
                } else {
                    if (i == 0 && j == 0) { // 왼쪽 상단 코너일 때
                        imageButton.setImageResource(R.drawable.left_top_corner_circle_black_check32)
                    } else if (i == 0 && j == 18) { // 오른쪽 상단 코너일 때
                        imageButton.setImageResource(R.drawable.right_top_corner_circle_black_check32)
                    } else if (i == 0) { // 위일 때(0행)
                        imageButton.setImageResource(R.drawable.top_circle_black_check32)
                    } else if (i == 18 && j == 0) {//왼쪽 하단일 때
                        imageButton.setImageResource(R.drawable.left_bottom_corner_circle_black_check32)
                    } else if (i == 18 && j == 18) { // 오른쪽 하단일 때
                        imageButton.setImageResource(R.drawable.right_bottom_corner_circle_black_check32)
                    } else if (i == 18) { // 아래일때(18행일 때)
                        imageButton.setImageResource(R.drawable.bottom_circle_black_check32)
                    } else if (j == 0) { // 왼쪽일 때(0열일때)
                        imageButton.setImageResource(R.drawable.left_circle_black_check32)
                    } else if (j == 18) { // 오른쪽일 때(18열일때)
                        imageButton.setImageResource(R.drawable.right_circle_black_check32)
                    } else { // 나머지일 때
                        imageButton.setImageResource(R.drawable.circle_black_check32)
                    }
                    stoneColorArray[i][j] = 1
                }
            }
            64 -> { // size가 64이면
                if (color == "white") {
                    if (i == 0 && j == 0) { // 왼쪽 상단 코너일 때
                        imageButton.setImageResource(R.drawable.left_top_corner_circle_white_check64)
                    } else if (i == 0 && j == 18) { // 오른쪽 상단 코너일 때
                        imageButton.setImageResource(R.drawable.right_top_corner_circle_white_check64)
                    } else if (i == 0) { // 위일 때(0행)
                        imageButton.setImageResource(R.drawable.top_circle_white_check64)
                    } else if (i == 18 && j == 0) {//왼쪽 하단일 때
                        imageButton.setImageResource(R.drawable.left_bottom_corner_circle_white_check64)
                    } else if (i == 18 && j == 18) { // 오른쪽 하단일 때
                        imageButton.setImageResource(R.drawable.right_bottom_corner_circle_white_check64)
                    } else if (i == 18) { // 아래일때(18행일 때)
                        imageButton.setImageResource(R.drawable.bottom_circle_white_check64)
                    } else if (j == 0) { // 왼쪽일 때(0열일때)
                        imageButton.setImageResource(R.drawable.left_circle_white_check64)
                    } else if (j == 18) { // 오른쪽일 때(18열일때)
                        imageButton.setImageResource(R.drawable.right_circle_white_check64)
                    } else { // 나머지일 때
                        imageButton.setImageResource(R.drawable.circle_white_check64)
                    }
                    stoneColorArray[i][j] = 2
                } else {
                    if (i == 0 && j == 0) { // 왼쪽 상단 코너일 때
                        imageButton.setImageResource(R.drawable.left_top_corner_circle_black_check64)
                    } else if (i == 0 && j == 18) { // 오른쪽 상단 코너일 때
                        imageButton.setImageResource(R.drawable.right_top_corner_circle_black_check64)
                    } else if (i == 0) { // 위일 때(0행)
                        imageButton.setImageResource(R.drawable.top_circle_black_check64)
                    } else if (i == 18 && j == 0) {//왼쪽 하단일 때
                        imageButton.setImageResource(R.drawable.left_bottom_corner_circle_black_check64)
                    } else if (i == 18 && j == 18) { // 오른쪽 하단일 때
                        imageButton.setImageResource(R.drawable.right_bottom_corner_circle_black_check64)
                    } else if (i == 18) { // 아래일때(18행일 때)
                        imageButton.setImageResource(R.drawable.bottom_circle_black_check64)
                    } else if (j == 0) { // 왼쪽일 때(0열일때)
                        imageButton.setImageResource(R.drawable.left_circle_black_check64)
                    } else if (j == 18) { // 오른쪽일 때(18열일때)
                        imageButton.setImageResource(R.drawable.right_circle_black_check64)
                    } else { // 나머지일 때
                        imageButton.setImageResource(R.drawable.circle_black_check64)
                    }
                    stoneColorArray[i][j] = 1
                }
            }
        }
    }

    fun changeCoordinateToBoard(x: Int, y: Int): String {
        val tempXStr: String = if (x < 10) "0".plus(x.toString()) else x.toString()
        val tempYStr: String = if (y < 10) "0".plus(y.toString()) else y.toString()
        return "board".plus(tempXStr).plus(tempYStr)
    }

    /* Initialize User Name
    *  이 코드에서 유저 이름 사용할 수 있도록 초기화 시키는 부분 */
    private fun getUserName(database: DatabaseReference) {
        var tempCnt = 0
        database.addValueEventListener(object : ValueEventListener {
            @SuppressLint("ResourceType", "SetTextI18n", "ShowToast")
            override fun onDataChange(snapshot: DataSnapshot) {
                for (i in snapshot.children) {
                    tempCnt++
                    if (i.key.equals("player1Id") && i.value.toString()
                            .isNotEmpty() && !nameFinished && (i.value.toString() != myNickName) && !isPlayer1Name
                    ) { // player1Id가 존재한다면
                        isGameStart = true // 상대방이 들어온 시점을 기준으로 게임 시작처리를 합니다.
                        Toast.makeText(
                            applicationContext,
                            getString(R.string.game_start),
                            Toast.LENGTH_SHORT
                        ).show()
                        player1Name = i.value.toString()
                        player2Name = myNickName // 서버에서는 player2에 내 이름이 저장됩니다.
                        nameFinished = true
                        isPlayer1Name = true
                        user1Nickname.text = myNickName
                        user2Nickname.text =
                            i.value.toString() // 상대편이 존재하는 것이므로 user2의 nickname을 설정해줍니다.
                        updateUserRatio(i.value.toString())
                        enemyName = i.value.toString()
                        serverUser2Name = true // 내가 서버에서 user2에 저장되어있음을 확인
                        database.child("player2Id").setValue(myNickName) // 나의 이름을 player2에 저장합니다.
                        break
                    } else if (i.key.equals("player2Id") && i.value.toString()
                            .isNotEmpty() && !nameFinished && !isPlayer2Name
                    ) { // player2Id에 이미 누가 있을 경우
                        isGameStart = true // 상대방이 들어온 시점을 기준으로 게임 시작처리를 합니다.
                        Toast.makeText(
                            applicationContext,
                            getString(R.string.game_start),
                            Toast.LENGTH_SHORT
                        ).show()
                        player1Name = myNickName
                        player2Name = i.value.toString()
                        nameFinished = true
                        isPlayer2Name = true
                        user1Nickname.text = myNickName
                        user2Nickname.text =
                            i.value.toString() // 상대편이 존재하는 것이므로 user2의 nickname을 설정해줍니다.
                        updateUserRatio(i.value.toString())
                        enemyName = i.value.toString()
                        serverUser1Name = true // 내가 서버에서 user1에 저장되어있음을 확인
                        database.child("player1Id").setValue(myNickName) // 나의 이름을 player1에 저장합니다.
                        break
                    }

                    println("@@@@ + serverPlayer1Id = ${i.value.toString()}")
                    if (i.key.equals("player1Id") && i.value.toString() != myNickName && i.value.toString()
                            .isEmpty() && isGameStart
                    ) { //상대방(player1Id가 empty일 때)이 나갔으면 승리처리합니다.
                        if (!toastDone && player1Name != myNickName) {
                            Toast.makeText(
                                applicationContext,
                                getString(R.string.user_left),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        //toastDone = true
                        if (!iAmGoingOut) showDialog(database, myNickName)
                        //if (!isUserExit && isGameStart) showDialog(database, myNickName)
                    } else if (i.key.equals("player2Id") && i.value.toString() != myNickName && i.value.toString()
                            .isEmpty() && isGameStart
                    ) { //상대방(player1Id가 empty일 때)이 나갔으면 승리처리합니다.
                        if (!toastDone && player2Name != myNickName) {
                            Toast.makeText(
                                applicationContext,
                                getString(R.string.user_left),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        if (!iAmGoingOut) showDialog(database, myNickName)
                        //toastDone = true
                        //if (!isUserExit && isGameStart) showDialog(database, myNickName)
                    }
                } // 이 for문의 조건문에 맞는 항목이 없다면 방에 사람이 없다는 뜻이므로 아래에서 user1과 user1 tv위치에 값들을 설정해줍니다.

                //방에 사람이 아무도 없을 때
                if (!isPlayer1Name && !isPlayer2Name && (tempCnt >= 3)) {
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


    }

    private fun zoomBoard32() {
        for (i in 0..18) { // 행
            val str = "0"
            var boardStr = "board"

            val firstNum =
                if (i < 10) str.plus(i.toString()) else i.toString() // board 뒤에 바로 붙는 첫 번째 수

            boardStr = boardStr.plus(firstNum) // board00까지 완성

            for (j in 0..18) { // 열
                val boardAndFirstNum = boardStr // boardStr값은 변경되지 않도록 함
                val zero = "0"
                val boardFinal =
                    boardAndFirstNum.plus(if (j < 10) zero.plus(j) else j.toString()) // 만약 j가 10보다 작으면 j 앞에 0을 붙이고, j가 10이상이면 그대로 j를 저장
                //최종적으로 boardFinal에는 board0000꼴로 id가 저장되게 된다.

                //println("@@@$boardFinal")

                /*좌표 위치를 통해 ImageButton의 크기를 줄이는 부분*/
                val target: Int =
                    resources.getIdentifier(boardFinal, "id", packageName) // id 값을 target에 저장
                val imageButton: ImageButton =
                    findViewById(target) // id값(target)과 findViewById를 통해 imageButton 변수에 좌표에 해당하는 값 할당


                if (checkDiamondStone[i][j] == 1) {
                    println("&&& checkdiamondStone ($i, $j) : ${checkDiamondStone[i][j]}")
                    // 해당 위치에 돌이 착수되지 않았고(위 if문 (stoneColorArray[i][j]==1 || stoneColorArray[i][j]==2)을 만족하지 않고),
                    // 빨간 다이아몬드가 체크 되었고(checkDiamondStone[i][j]==1)
                    // 금방돌이 아니어서 체크된 돌이 아니라면 (checkStoneArray[i][j]!=1)
                    setCheckStoneDependSize(imageButton, i, j, 32)
                    continue
                } else if (stoneColorArray[i][j] == 1 || stoneColorArray[i][j] == 2) {// 해당 위치(i,j)에 돌이 착수가 되었다면
                    when (stoneColorArray[i][j]) {
                        1 -> { // 검은색 돌이라면
                            if (checkStoneArray[i][j] == 1) { // 만약 체크된 돌이면 체크표시를 해서 그린다
                                imageButton.setImageResource(R.drawable.circle_black_check32)
                            } else {
                                imageButton.setImageResource(R.drawable.circle_black32)
                            }
                            continue // 돌에 대한 처리를 when에서 해주기 때문에 (i,j)의 돌에 대한 처리는 여기서 해줄 필요 없다.
                        }
                        2 -> { // 흰색 돌이라면
                            if (checkStoneArray[i][j] == 1) { // 만약 체크된 돌이면 체크표시를 해서 그린다
                                imageButton.setImageResource(R.drawable.circle_white_check32)
                            } else {
                                imageButton.setImageResource(R.drawable.circle_white32)
                            }
                            continue
                        }
                    }
                }


                if ((i == 3 && j == 3) || (i == 3 && j == 9) || (i == 3 && j == 15) || (i == 9 && j == 3) || (i == 9 && j == 9) || (i == 9 && j == 15) || (i == 15 && j == 3) || (i == 15 && j == 9) || (i == 15 && j == 15)) imageButton.setImageResource(
                    R.drawable.point32
                )
                else if (i != 0 && j == 0 && i != 18) imageButton.setImageResource(R.drawable.left32) // 왼쪽 부분일 때
                else if (i == 0 && j == 0) imageButton.setImageResource(R.drawable.left_top_corner32) // 왼쪽 위 코너일 때, (0,0)일 때
                else if (i == 18 && j == 0) imageButton.setImageResource(R.drawable.left_bottom_corner32) // 왼쪽 아래 코너일 때, (18,0)일 때
                else if (i != 0 && j == 18 && i != 18) imageButton.setImageResource(R.drawable.right32) // 오른쪽 일 때
                else if (i == 0 && j == 18) imageButton.setImageResource(R.drawable.right_top_corner32) // 오른쪽 위 코너일 때, (0,18)일 때
                else if (i == 18 && j == 18) imageButton.setImageResource(R.drawable.right_bottom_corner32) // 오른쪽 아래 코너일 때, (18,18)일 때
                else if (i == 18 && j != 0 && j != 19) imageButton.setImageResource(R.drawable.bottom32) // 아래일 때
                else if (i == 0 && j != 0 && j != 19) imageButton.setImageResource(R.drawable.top32) // 위일 때
                else imageButton.setImageResource(R.drawable.standard32) // imageButton의 image를 32 size로 축소

            }
        }
    }

    private fun zoomBoard64() {
        for (i in 0..18) { // 행
            var str = "0"
            var boardStr = "board"

            val firstNum =
                if (i < 10) str.plus(i.toString()) else i.toString() // board 뒤에 바로 붙는 첫 번째 수

            str = if (i < 10) str.plus(i.toString()) else i.toString()

            boardStr = boardStr.plus(firstNum) // board00까지 완성

            for (j in 0..18) { // 열
                val boardAndFirstNum = boardStr // boardStr값은 변경되지 않도록 함
                val zero = "0"
                val boardFinal =
                    boardAndFirstNum.plus(if (j < 10) zero.plus(j) else j.toString()) // 만약 j가 10보다 작으면 j 앞에 0을 붙이고, j가 10이상이면 그대로 j를 저장
                //최종적으로 boardFinal에는 board0000꼴로 id가 저장되게 된다.

                //println("@@@$boardFinal")

                /*좌표 위치를 통해 ImageButton의 크기를 줄이는 부분*/
                val target: Int =
                    resources.getIdentifier(boardFinal, "id", packageName) // id 값을 target에 저장
                val imageButton: ImageButton =
                    findViewById(target) // id값(target)과 findViewById를 통해 imageButton 변수에 좌표에 해당하는 값 할당

                if (checkDiamondStone[i][j] == 1) {
                    println("&&& checkdiamondStone ($i, $j) : ${checkDiamondStone[i][j]}")
                    // 해당 위치에 돌이 착수되지 않았고(위 if문 (stoneColorArray[i][j]==1 || stoneColorArray[i][j]==2)을 만족하지 않고),
                    // 빨간 다이아몬드가 체크 되었고(checkDiamondStone[i][j]==1)
                    // 금방돌이 아니어서 체크된 돌이 아니라면 (checkStoneArray[i][j]!=1)
                    setCheckStoneDependSize(imageButton, i, j, 64)
                    continue
                } else if (stoneColorArray[i][j] == 1 || stoneColorArray[i][j] == 2) {// 해당 위치(i,j)에 돌이 착수가 되었다면
                    when (stoneColorArray[i][j]) {// 해당 위치(i,j)에 돌이 착수가 되었다면
                        1 -> {
                            if (checkStoneArray[i][j] == 1) { // 만약 체크된 돌이면 체크표시를 해서 그린다
                                imageButton.setImageResource(R.drawable.circle_black_check64)
                            } else {
                                imageButton.setImageResource(R.drawable.circle_black64)
                            }
                            continue // 돌에 대한 처리를 when에서 해주기 때문에 (i,j)의 돌에 대한 처리는 여기서 해줄 필요 없다.
                        }
                        2 -> {
                            if (checkStoneArray[i][j] == 1) { // 만약 체크된 돌이면 체크표시를 해서 그린다
                                imageButton.setImageResource(R.drawable.circle_white_check64)
                            } else {
                                imageButton.setImageResource(R.drawable.circle_white64)
                            }
                            continue
                        }
                    }
                }



                if ((i == 3 && j == 3) || (i == 3 && j == 9) || (i == 3 && j == 15) || (i == 9 && j == 3) || (i == 9 && j == 9) || (i == 9 && j == 15) || (i == 15 && j == 3) || (i == 15 && j == 9) || (i == 15 && j == 15)) imageButton.setImageResource(
                    R.drawable.point64
                )
                else if (i != 0 && j == 0 && i != 18) imageButton.setImageResource(R.drawable.left64) // 왼쪽 부분일 때
                else if (i == 0 && j == 0) imageButton.setImageResource(R.drawable.left_top_corner64) // 왼쪽 위 코너일 때, (0,0)일 때
                else if (i == 18 && j == 0) imageButton.setImageResource(R.drawable.left_bottom_corner64) // 왼쪽 아래 코너일 때, (18,0)일 때
                else if (i != 0 && j == 18 && i != 18) imageButton.setImageResource(R.drawable.right64) // 오른쪽 일 때
                else if (i == 0 && j == 18) imageButton.setImageResource(R.drawable.right_top_corner64) // 오른쪽 위 코너일 때, (0,18)일 때
                else if (i == 18 && j == 18) imageButton.setImageResource(R.drawable.right_bottom_corner64) // 오른쪽 아래 코너일 때, (18,18)일 때
                else if (i == 18 && j != 0 && j != 19) imageButton.setImageResource(R.drawable.bottom64) // 아래일 때
                else if (i == 0 && j != 0 && j != 19) imageButton.setImageResource(R.drawable.top64) // 위일 때
                else imageButton.setImageResource(R.drawable.standard64) // imageButton의 image를 32 size로 축소

            }
        }
    }

    private fun zoomBoard16() {
        for (i in 0..18) { // 행
            var str = "0"
            var boardStr = "board"

            var firstNum =
                if (i < 10) str.plus(i.toString()) else i.toString() // board 뒤에 바로 붙는 첫 번째 수

            str = if (i < 10) str.plus(i.toString()) else i.toString()

            boardStr = boardStr.plus(firstNum) // board00까지 완성

            for (j in 0..18) { // 열
                val boardAndFirstNum = boardStr // boardStr값은 변경되지 않도록 함
                val zero = "0"
                val boardFinal =
                    boardAndFirstNum.plus(if (j < 10) zero.plus(j) else j.toString()) // 만약 j가 10보다 작으면 j 앞에 0을 붙이고, j가 10이상이면 그대로 j를 저장
                //최종적으로 boardFinal에는 board0000꼴로 id가 저장되게 된다.

                //println("@@@$boardFinal")

                /*좌표 위치를 통해 ImageButton의 크기를 줄이는 부분*/
                val target: Int =
                    resources.getIdentifier(boardFinal, "id", packageName) // id 값을 target에 저장
                val imageButton: ImageButton =
                    findViewById(target) // id값(target)과 findViewById를 통해 imageButton 변수에 좌표에 해당하는 값 할당



                if (checkDiamondStone[i][j] == 1) {
                    println("&&& checkdiamondStone ($i, $j) : ${checkDiamondStone[i][j]}")
                    // 해당 위치에 돌이 착수되지 않았고(위 if문 (stoneColorArray[i][j]==1 || stoneColorArray[i][j]==2)을 만족하지 않고),
                    // 빨간 다이아몬드가 체크 되었고(checkDiamondStone[i][j]==1)
                    // 금방돌이 아니어서 체크된 돌이 아니라면 (checkStoneArray[i][j]!=1)
                    setCheckStoneDependSize(imageButton, i, j, 16)
                    continue
                } else if (stoneColorArray[i][j] == 1 || stoneColorArray[i][j] == 2) {// 해당 위치(i,j)에 돌이 착수가 되었다면
                    when (stoneColorArray[i][j]) {// 해당 위치(i,j)에 돌이 착수가 되었다면
                        1 -> {
                            if (checkStoneArray[i][j] == 1) { // 만약 체크된 돌이면 체크표시를 해서 그린다
                                imageButton.setImageResource(R.drawable.circle_black_check16)
                            } else {
                                imageButton.setImageResource(R.drawable.circle_black16)
                            }
                            continue // 돌에 대한 처리를 when에서 해주기 때문에 (i,j)의 돌에 대한 처리는 여기서 해줄 필요 없다.
                        }
                        2 -> {
                            if (checkStoneArray[i][j] == 1) { // 만약 체크된 돌이면 체크표시를 해서 그린다
                                imageButton.setImageResource(R.drawable.circle_white_check16)
                            } else {
                                imageButton.setImageResource(R.drawable.circle_white16)
                            }
                            continue
                        }
                    }
                }

                if ((i == 3 && j == 3) || (i == 3 && j == 9) || (i == 3 && j == 15) || (i == 9 && j == 3) || (i == 9 && j == 9) || (i == 9 && j == 15) || (i == 15 && j == 3) || (i == 15 && j == 9) || (i == 15 && j == 15)) imageButton.setImageResource(
                    R.drawable.point16
                )
                else if (i != 0 && j == 0 && i != 18) imageButton.setImageResource(R.drawable.left16) // 왼쪽 부분일 때
                else if (i == 0 && j == 0) imageButton.setImageResource(R.drawable.left_top_corner16) // 왼쪽 위 코너일 때, (0,0)일 때
                else if (i == 18 && j == 0) imageButton.setImageResource(R.drawable.left_bottom_corner16) // 왼쪽 아래 코너일 때, (18,0)일 때
                else if (i != 0 && j == 18 && i != 18) imageButton.setImageResource(R.drawable.right16) // 오른쪽 일 때
                else if (i == 0 && j == 18) imageButton.setImageResource(R.drawable.right_top_corner16) // 오른쪽 위 코너일 때, (0,18)일 때
                else if (i == 18 && j == 18) imageButton.setImageResource(R.drawable.right_bottom_corner16) // 오른쪽 아래 코너일 때, (18,18)일 때
                else if (i == 18 && j != 0 && j != 19) imageButton.setImageResource(R.drawable.bottom16) // 아래일 때
                else if (i == 0 && j != 0 && j != 19) imageButton.setImageResource(R.drawable.top16) // 위일 때
                else imageButton.setImageResource(R.drawable.standard16) // imageButton의 image를 32 size로 축소

            }
        }
    }

    private fun printColorBoard() {
        //println("@@@@PRINT BOARD@@@@")
        //Log.d("Print Board", "Print Board")
        for (i in 0..18) {
            //print("$i : ".padStart(3))
            for (j in 0..18) {
                //print("${stoneColorArray[i][j]}".padStart(8))
            }
            //println()
        }
    }

    fun printBoard() {
        println("@@@@PRINT BOARD@@@@")
        for (i in 0..18) {
            print("$i : ")
            for (j in 0..18) {
                //print("${checkStoneArray[i][j]}".padStart(8))
            }
            //println()
        }
    }

    fun judgeVictory(database: DatabaseReference) {
        println("@@@ JUDGEVICTORY part")
        for (i in 0..13) { // 세로로 다 맞았을 때
            for (j in 0..18) {
                if (stoneColorArray[i][j] == 1 && stoneColorArray[i + 1][j] == 1 && stoneColorArray[i + 2][j] == 1 && stoneColorArray[i + 3][j] == 1 && stoneColorArray[i + 4][j] == 1 && stoneColorArray[i + 5][j] == 1) {
                    winnerStr = player1Name
                    println("### 1")
                    //Toast.makeText(this, "1$winnerStr win!", Toast.LENGTH_SHORT).show()
                    printColorBoard()
                    initBoards() // 게임 승리 판정이 나면 초기화
                    turnNum = 1
                    showDialog(database, winnerStr)
                    //if(!isUserExit) showDialog(database, myNickName)
                    return
                } else if (stoneColorArray[i][j] == 2 && stoneColorArray[i + 1][j] == 2 && stoneColorArray[i + 2][j] == 2 && stoneColorArray[i + 3][j] == 2 && stoneColorArray[i + 4][j] == 2 && stoneColorArray[i + 5][j] == 2) {
                    winnerStr = player2Name
                    println("### 2")
                    //Toast.makeText(this, "2$winnerStr win!", Toast.LENGTH_SHORT).show()
                    printColorBoard()
                    initBoards()
                    turnNum = 1
                    showDialog(database, winnerStr)
                    //if(!isUserExit) showDialog(database, myNickName)
                    return
                }
            }
        }

        for (j in 0..13) { // 가로로 다 맞았을 때
            for (i in 0..18) {
                if (stoneColorArray[i][j] == 1 && stoneColorArray[i][j + 1] == 1 && stoneColorArray[i][j + 2] == 1 && stoneColorArray[i][j + 3] == 1 && stoneColorArray[i][j + 4] == 1 && stoneColorArray[i][j + 5] == 1) {
                    println("### 3")
                    winnerStr = player1Name
                    //Toast.makeText(this, "3$winnerStr win!", Toast.LENGTH_SHORT).show()
                    printColorBoard()
                    initBoards()
                    turnNum = 1
                    showDialog(database, winnerStr)
                    return
                } else if (stoneColorArray[i][j] == 2 && stoneColorArray[i][j + 1] == 2 && stoneColorArray[i][j + 2] == 2 && stoneColorArray[i][j + 3] == 2 && stoneColorArray[i][j + 4] == 2 && stoneColorArray[i][j + 5] == 2) {
                    println("### 4")
                    winnerStr = player2Name
                    //Toast.makeText(this, "4$winnerStr win!", Toast.LENGTH_SHORT).show()
                    printColorBoard()
                    initBoards()
                    turnNum = 1
                    showDialog(database, winnerStr)
                    return
                }
            }
        }


        for (j in 0..13) { // \(왼쪽 위에서 오른쪽 아래) 대각선 다 맞았을 때
            for (i in 0..13) {
                if (stoneColorArray[i][j] == 1 && stoneColorArray[i + 1][j + 1] == 1 && stoneColorArray[i + 2][j + 2] == 1 && stoneColorArray[i + 3][j + 3] == 1 && stoneColorArray[i + 4][j + 4] == 1 && stoneColorArray[i + 5][j + 5] == 1) {
                    println("### 5")
                    winnerStr = player1Name
                    //Toast.makeText(this, "5$winnerStr win!", Toast.LENGTH_SHORT).show()
                    printColorBoard()
                    initBoards()
                    turnNum = 1
                    showDialog(database, winnerStr)
                    return
                } else if (stoneColorArray[i][j] == 2 && stoneColorArray[i + 1][j + 1] == 2 && stoneColorArray[i + 2][j + 2] == 2 && stoneColorArray[i + 3][j + 3] == 2 && stoneColorArray[i + 4][j + 4] == 2 && stoneColorArray[i + 5][j + 5] == 2) {
                    println("### 6")
                    winnerStr = player2Name
                    //Toast.makeText(this, "6$winnerStr win!", Toast.LENGTH_SHORT).show()
                    printColorBoard()
                    initBoards()
                    turnNum = 1
                    showDialog(database, winnerStr)
                    return
                }
            }
        }

        for (j in 0..13) { // /(오른쪽 위에서 왼쪽 아래) 대각선 다 맞았을 때
            for (i in 0..13) {
                if (stoneColorArray[19 - i - 1][j] == 1 && stoneColorArray[19 - i - 2][j + 1] == 1 && stoneColorArray[19 - i - 3][j + 2] == 1 && stoneColorArray[19 - i - 4][j + 3] == 1 && stoneColorArray[19 - i - 5][j + 4] == 1 && stoneColorArray[19 - i - 6][j + 5] == 1) {
                    println("### 7")
                    winnerStr = player1Name
                    //Toast.makeText(this, "7$winnerStr win!", Toast.LENGTH_SHORT).show()
                    printColorBoard()
                    initBoards()
                    turnNum = 1
                    showDialog(database, winnerStr)
                    return
                } else if (stoneColorArray[19 - i - 1][j] == 2 && stoneColorArray[19 - i - 2][j + 1] == 2 && stoneColorArray[19 - i - 3][j + 2] == 2 && stoneColorArray[19 - i - 4][j + 3] == 2 && stoneColorArray[19 - i - 5][j + 4] == 2 && stoneColorArray[19 - i - 6][j + 5] == 2) {
                    println("### 8")
                    winnerStr = player2Name
                    //Toast.makeText(this, "8$winnerStr win!", Toast.LENGTH_SHORT).show()
                    printColorBoard()
                    initBoards()
                    turnNum = 1
                    showDialog(database, winnerStr)
                    return
                }
            }
        }
    }

    @SuppressLint("InflateParams")
    private fun showDialog(database: DatabaseReference, winnerStr: String) {

        val builder = AlertDialog.Builder(this)
        var winDialogView = layoutInflater.inflate(R.layout.dialog_win, null)
        var loseDialogView = layoutInflater.inflate(R.layout.dialog_lose, null)
        when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_NO -> { // Dark Mode 아닐 때
                winDialogView = layoutInflater.inflate(R.layout.dialog_win, null)
                loseDialogView = layoutInflater.inflate(R.layout.dialog_lose, null)
            }
            Configuration.UI_MODE_NIGHT_YES -> { // Dark Mode 일 때
                winDialogView = layoutInflater.inflate(R.layout.dialog_win_night, null)
                loseDialogView = layoutInflater.inflate(R.layout.dialog_lose_night, null)
            }
        }


        val userDatabase = FirebaseDatabase.getInstance().reference.child("Users")

        println("### winnerStr : $winnerStr, myNickName : $myNickName")

        if (myNickName == winnerStr && (!this.isFinishing)) { // 만약 승리했다면,
            //winDialogView.
            when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
                Configuration.UI_MODE_NIGHT_NO -> { // Dark Mode 아닐 때
                    builder.setView(winDialogView)
                        //.setPositiveButton("RETRY") { _, _ -> //재경기 누르면
                        //Retry누르면 다시 원래 화면으로 돌아가면 되므로 따로 작업할 것이 없다.
                        //}
                        .setNegativeButton(getString(R.string.upper_exit)) { _, _ -> // EXIT 누르면 MainActivity로 이동
                            //.setNegativeButton(getString(R.string.upper_exit)) { _, _ -> // EXIT 누르면 MainActivity로 이동
                            isUserExit = true
                            exitProcess(database)
                        }
                        .setCancelable(false)
                        .show()
                        .getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.BLACK)
                }
                Configuration.UI_MODE_NIGHT_YES -> { // Dark Mode 일 때
                    builder.setView(winDialogView)
                        //.setPositiveButton("RETRY") { _, _ -> //재경기 누르면
                        //Retry누르면 다시 원래 화면으로 돌아가면 되므로 따로 작업할 것이 없다.
                        //}
                        .setNegativeButton(getString(R.string.upper_exit)) { _, _ -> // EXIT 누르면 MainActivity로 이동
                            isUserExit = true
                            exitProcess(database)
                        }
                        .setCancelable(false)
                        .show()
                        .getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.WHITE)
                }
            }
//            builder.setView(winDialogView)
//                //.setPositiveButton("RETRY") { _, _ -> //재경기 누르면
//                //Retry누르면 다시 원래 화면으로 돌아가면 되므로 따로 작업할 것이 없다.
//                //}
//                .setNegativeButton(Html.fromHtml("<font color='#FF7F27'>${R.string.upper_exit}</font>").toString()) { _, _ -> // EXIT 누르면 MainActivity로 이동
//                //.setNegativeButton(getString(R.string.upper_exit)) { _, _ -> // EXIT 누르면 MainActivity로 이동
//                    isUserExit = true
//                    exitProcess(database)
//                }
//                .setCancelable(false)
//                .show()
//                .getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.BLACK)


            //이겼을 때 win 값 setValue해주고, ratio값을 업데이트해줍니다.
            userDatabase.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (i in snapshot.children) {
                        if (myNickName == i.child("nickName").value) { // 같은 이름을 찾아서 win value를 1 올려줍니다.
                            val winData = i.child("win").value.toString()
                            val loseData = i.child("lose").value.toString()
                            var winInt = winData.toFloat()
                            val loseInt = loseData.toFloat()
                            winInt++
                            val ratio =
                                (((winInt / (winInt + loseInt)) * 100 * 100).roundToInt() / 100F).toString()
                                    .plus("%") // 소수점 둘 째자리 까지 표기
                            userDatabase.child(i.key.toString()).child("win")
                                .setValue(winInt.toString())
                            userDatabase.child(i.key.toString()).child("ratio").setValue(ratio)
                            break
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {

                }

            })


        } else if (myNickName != winnerStr && (!this.isFinishing)) { // 만약 패배했다면,

            userDatabase.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (i in snapshot.children) {
                        if (myNickName == i.child("nickName").value) { // 같은 이름을 찾아서 win value를 1 올려줍니다.
                            val winData = i.child("win").value.toString()
                            val loseData = i.child("lose").value.toString()
                            val winInt = winData.toFloat()
                            var loseInt = loseData.toInt()
                            loseInt++
                            val ratio =
                                (((winInt / (winInt + loseInt)) * 100 * 100).roundToInt() / 100F).toString()
                                    .plus("%") // 소수점 둘 째자리 까지 표기
                            userDatabase.child(i.key.toString()).child("lose")
                                .setValue(loseInt.toString())
                            userDatabase.child(i.key.toString()).child("ratio").setValue(ratio)
                            break
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {

                }

            })

            when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
                Configuration.UI_MODE_NIGHT_NO -> { // Dark Mode 아닐 때
                    builder.setView(loseDialogView)
                        .setNegativeButton(getString(R.string.upper_exit)) { _, _ -> // EXIT 누르면 MainActivity로 이동
                            isUserExit = true
                            exitProcess(database)
                        }
                        .setCancelable(false)
                        .show()
                        .getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.BLACK)
                }
                Configuration.UI_MODE_NIGHT_YES -> { // Dark Mode 일 때
                    builder.setView(loseDialogView)
                        .setNegativeButton(getString(R.string.upper_exit)) { _, _ -> // EXIT 누르면 MainActivity로 이동
                            isUserExit = true
                            exitProcess(database)
                        }
                        .setCancelable(false)
                        .show()
                        .getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.WHITE)
                }
            }



        }
    }

    private fun exitProcess(database: DatabaseReference) {
        /** 나가고 방을 없애지 않으려면 아래 코드가 필요하므로 지우면 안된다.
        if (serverUser1Name) { // 서버의 player1Id에 내 닉네임이 저장되어 있다면
        var updateUserName = mutableMapOf<String, Any>()
        updateUserName["player1Id"] = "" // 내 이름이 있던 player1Id를 빈 곳(String)으로 변경
        database.updateChildren(updateUserName)//Firebase에 업데이트
        } else if (serverUser2Name) {
        var updateUserName = mutableMapOf<String, Any>()
        updateUserName["player2Id"] = "" // 내 이름이 있던 player1Id를 빈 곳(String)으로 변경
        database.updateChildren(updateUserName)//Firebase에 업데이트
        }
         */

//        GlobalScope.launch { // 서버에서 사용자 이름란(player1Id, player2Id)에 둘 다 없어야 해당 방을 삭제한다.
//            Log.e("Player1Exit", "${isServerPlayer1Exit()}")
//            Log.e("Player2Exit", "${isServerPlayer2Exit()}")
//            if(isServerPlayer1Exit()&&isServerPlayer2Exit()){
//                database.removeValue()
//                finish()
//            }else{
//                finish()
//            }
//        }

        database.removeValue()
        finish()

    }

    private suspend fun isServerPlayer1Exit() = suspendCoroutine<Boolean> {
        Handler(Looper.getMainLooper()).postDelayed({
            var isPlayer1Empty = false
            val database = FirebaseDatabase.getInstance().reference.child("roomId")
            database.child("player1Id").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.value.toString().isEmpty()) {
                        isPlayer1Empty = true
                        it.resume(isPlayer1Empty)
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
        }, 500)
    }

    private suspend fun isServerPlayer2Exit() = suspendCoroutine<Boolean> {
        Handler(Looper.getMainLooper()).postDelayed({
            var isPlayer2Empty = false
            val database = FirebaseDatabase.getInstance().reference.child("roomId")
            database.child("player2Id").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.value.toString().isEmpty()) {
                        isPlayer2Empty = true
                        it.resume(isPlayer2Empty)
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
        }, 500)
    }

    var backKeyPressedTime: Long = 0 // 뒤로가기 버튼 클릭 시간 확인을 위한 변수

    //뒤로가기 버튼 클릭 시
    @SuppressLint("ResourceType")
    override fun onBackPressed() {
        val database = FirebaseDatabase.getInstance().reference.child("roomId").child(roomKey)

        if (System.currentTimeMillis() > backKeyPressedTime + 2500) { // 만약 클릭한 시간이 2.5초가 지났다면연(연속적으로 클릭한 것이 아닐 때)
            backKeyPressedTime = System.currentTimeMillis()
            Toast.makeText(this, getString(R.string.one_more_click_exit), Toast.LENGTH_SHORT).show()
            return
        }

        if (System.currentTimeMillis() <= backKeyPressedTime + 2500) { // 만약 클릭한 시간이 2.5초가 이하라면(연속적으로 클릭했을 때)
            println("text : ${user2Nickname.text}, waiting = waiting...")
            if (user2Nickname.text == "Waiting…") { // 상대방이 아직 안들어왔는데 그냥 나가려고 시도할 때 패배 dialog를 보여주지 않고 그냥 종료.
                database.removeValue() // 서버에서 방을 없애고 나간다.
                finish()
            } else { // 상대방이 들어왔는데(방에 두 명이 있는데 게임은 시작 안한경우) 나가려고 하는 경우
                iAmGoingOut = true
                showDialog(database, enemyName)
                if (player1Name == myNickName) { // 서버에서 player1name이 내 이름이라면 player1을 서버에서 지워준다.
                    database.child("player2Id")
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(snapshot: DataSnapshot) {
                                if (snapshot.value == "") { // 내가 나가는데 상대방(player2)도 없으면 방 없앤다.
                                    database.removeValue()
                                }
                            }

                            override fun onCancelled(error: DatabaseError) {}
                        })
                    database.child("player1Id").setValue("")
                } else if (player2Name == myNickName) {
                    database.child("player1Id")
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(snapshot: DataSnapshot) {
                                if (snapshot.value == "") { // 내가 나가는데 상대방(player1)도 없으면 방 없앤다.
                                    database.removeValue()
                                }
                            }

                            override fun onCancelled(error: DatabaseError) {}
                        })
                    database.child("player2Id").setValue("")
                }
                //finish()
//                if(isGameStart) showDialog(database, enemyName)
//                else {
//                    if(player1Name==myNickName){ // 서버에서 player1name이 내 이름이라면 player1을 서버에서 지워준다.
//                        database.child("player2Id").addListenerForSingleValueEvent(object:ValueEventListener{
//                            override fun onDataChange(snapshot: DataSnapshot) {
//                                if(snapshot.value==""){ // 내가 나가는데 상대방(player2)도 없으면 방 없앤다.
//                                    database.removeValue()
//                                }
//                            }
//                            override fun onCancelled(error: DatabaseError) { }
//                        })
//                        database.child("player1Id").setValue("")
//                    }else if(player2Name == myNickName){
//                        database.child("player1Id").addListenerForSingleValueEvent(object:ValueEventListener{
//                            override fun onDataChange(snapshot: DataSnapshot) {
//                                if(snapshot.value==""){ // 내가 나가는데 상대방(player1)도 없으면 방 없앤다.
//                                    database.removeValue()
//                                }
//                            }
//                            override fun onCancelled(error: DatabaseError) { }
//                        })
//                        database.child("player2Id").setValue("")
//                    }
//                    finish()
//                }
            }
        }
    }

    fun updateUserRatio(userNickName: String) {
        val database = FirebaseDatabase.getInstance().reference.child("Users")
        var ratio = ""

        database.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (i in snapshot.children) {
                    if (i.child("nickName").value.toString() == userNickName) {
                        ratio = i.child("win").value.toString().substringBefore(".").plus("/")
                            .plus(i.child("lose").value.toString()).substringBefore(".").plus("/")
                            .plus(i.child("ratio").value.toString())
                    }
                }
                user2Record.text = ratio
            }

            override fun onCancelled(error: DatabaseError) {}

        })

    }

    private fun initBoards() {
        println("Initializing...")
        for (i in stoneCheckArray.indices) {
            for (j in stoneCheckArray[i].indices) {
                stoneCheckArray[i][j] = false // 돌이 모두 착수가 안된 상태로 초기화
            }
        }
        for (i in stoneColorArray.indices) {
            for (j in stoneColorArray[i].indices) {
                stoneColorArray[i][j] = 0 // 초기에는 돌이 착수 안됐음을 표시
            }
        }
        for (i in checkStoneArray.indices) {
            for (j in checkStoneArray[i].indices) {
                checkStoneArray[i][j] = 0 // 0은 일반 돌임을 명시
            }
        }
        for (i in checkDiamondStone.indices) {
            for (j in checkDiamondStone[i].indices) {
                checkDiamondStone[i][j] = 0 // 0은 다이아몬드가 없음을 의미
            }
        }
        printColorBoard()
    }

}

class UnCatchTaskService : Service() { //
    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun onTaskRemoved(rootIntent: Intent) {//사용자가 나갔을 때 처리
        Log.e("nickName", SharedData.prefs.getName("nickName", ""))
        val database = FirebaseDatabase.getInstance().reference.child("roomId")
        val myName = SharedData.prefs.getName("nickName", "")
        GlobalScope.launch {
            findUserFromDB(myName) // suspendCoroutine
            println("@@@ 2")
            stopSelf()
        }
        //Toast.makeText(applicationContext, "Warning : App killed", Toast.LENGTH_SHORT).show()
    }

    private suspend fun findUserFromDB(userName: String) = suspendCoroutine<Boolean> {
        Handler(Looper.getMainLooper()).postDelayed({
            val database = FirebaseDatabase.getInstance().reference.child("roomId")
            database.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (i in snapshot.children) {
                        if (i.child("player1Id").value.toString() == SharedData.prefs.getName(
                                "nickName",
                                ""
                            )
                        ) {
                            database.child(i.key!!).child("player1Id").setValue("")
                            Log.e("User Out", "Successfully Closed")
                        } else if (i.child("player2Id").value.toString() == SharedData.prefs.getName(
                                "nickName",
                                ""
                            )
                        ) {
                            database.child(i.key!!).child("player2Id").setValue("")
                            Log.e("User Out", "Successfully Closed")
                        }

                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
        }, 500)
    }
}