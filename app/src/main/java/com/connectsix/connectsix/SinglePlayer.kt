package com.connectsix.connectsix

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.res.Configuration
import android.graphics.Color
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Window
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.connectsix.connectsix.sharedRef.SharedData
import kotlinx.android.synthetic.main.singleplayer_gameboard.*
import kotlinx.android.synthetic.main.twoplayer_gameboard.zoomInButton
import kotlinx.android.synthetic.main.twoplayer_gameboard.zoomOutButton
import java.lang.Integer.max
import java.util.*
import kotlin.math.min

class SinglePlayer : AppCompatActivity() {
    var playerColor = "" // 유저가 선택한 돌 색
    var zoomLevel = 64 // 초기 확대 값(default 값)은 64
    var checkStoneArray: Array<IntArray> = Array<IntArray>(19) { IntArray(19) } // 돌이 있는지 확인한다
    var checkDiamondStone: Array<IntArray> =
        Array<IntArray>(19) { IntArray(19) } // 빨간 다이아몬드가 체크 되어 있는지 확인한다.
    var stoneColorArray: Array<IntArray> =
        Array<IntArray>(19) { IntArray(19) } // 해당 위치에 무슨 색의 돌이 착수되었는지 확인하는 array 0 : 착수 안됨, 1 : 검은색, 2: 흰색
    var stoneCheckArray: Array<BooleanArray> =
        Array<BooleanArray>(19) { BooleanArray(19) } // 해당 위치에 돌이 착수되었는지 확인하는 array
    var calcScoreArray = Array(19) { IntArray(19) } // 인공지능이 위험도를 판단하기 위해 점수를 계산하는 array
    var calcPlayerScoreArray =
        Array(19) { IntArray(19) } // 인공지능 입장에서 사용자가 어디에 착수할 지를 예상하기 위해 만든 array다. MiniMax Algorithm에 이용된다.

    //인공지능 기준이므로, 인공지능이 둔 곳 주변을 +1, 사람이 둔 곳 주변을 -1로 기준한다.
    private val myNickName: String =
        SharedData.prefs.getName("nickName", "") // Shared Pref.에서 내 닉네임을 가져온다.
    private var turnNum = 1 // 지금이 몇 번째 Turn인지 알기 위한 변수입니다.
    var stoneClickCnt = 0 // 빨간 다이아모든 위에 두 번 째 클릭인지 확인하는 변수입니다.
    var confirmX = -1 // user가 두기로 했던 빨간 diamond와 같은 위치에 돌을 두는지 확인하는 변수들입니다.
    var confirmY = -1 // 결국 좌표를 확인하기 위한 변수입니다.
    var clickStoneChanged: Boolean = false
    var turnDataList = mutableListOf<Turn>() // 지금까지 착수된 돌들을 list형태로 저장하기 위한 변수입니다.
    var winnerStr = "" // 이긴 사람이 누구인지 dialog에 보여줄 때 필요한 String입니다.

    var playerClicked = 0 // 이 변수는 사용자가 흰 돌일 때, 두 번 째 두는 것인지 확인하기 위함입니다.
    // 두 번째 두는 것인지 확인해야 하는 이유는, 사용자가 두번 째 돌을 착수한 이후 ai도 돌을 두 개 착수해야 하기 때문입니다.

    val dx = arrayOf(-1, -1, -1, 0, 1, 1, 1, 0) // 상하좌우, 대각선 총 여덟 방향 확인
    val dy = arrayOf(-1, 0, 1, 1, 1, 0, -1, -1) // 순서는 왼쪽위(0)부터 시계방향으로 회전(0번~7번)

    data class Point(var i: Int, var j: Int)

    var min1 = Point(0, 0)
    var min2 = Point(0, 0)

    var max1 = Point(0, 0)
    var max2 = Point(0, 0)

    var aiKnowMeCnt = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.singleplayer_gameboard)

        var soundPool = SoundPool(5, AudioManager.STREAM_MUSIC, 0)
        var soundID = soundPool.load(this, R.raw.sound_stone, 1)

        player1Nickname.text = myNickName
        when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_NO -> { // Dark Mode 아닐 때
                player1Nickname.setTextColor(
                    ContextCompat.getColor(
                        applicationContext,
                        R.color.black
                    )
                )
                player2Nickname.setTextColor(
                    ContextCompat.getColor(
                        applicationContext,
                        R.color.black
                    )
                )
            }
            Configuration.UI_MODE_NIGHT_YES -> { // Dark Mode일 때
                player1Nickname.setTextColor(
                    ContextCompat.getColor(
                        applicationContext,
                        R.color.white
                    )
                )
                player2Nickname.setTextColor(
                    ContextCompat.getColor(
                        applicationContext,
                        R.color.white
                    )
                )
            }
        }
        showDialogAndChooseColor() // 사용자에게 어떤 돌로 착수하고 싶은지 선택하게 한다.
        initBoards() // 사용자가 어떤 클릭을 했는지에 따라 반응하기 위해 여러 배열들을 사용하는데
        // 그 배열들을 초기화 한다.
        //setImageClickable()

        /*zoom out button이 눌려지면 각 button의 크기를 줄임
        * default 값은 64*/
        zoomOutButton.setOnClickListener { //축소
            when (zoomLevel) {
                64 -> {
                    zoomBoard32()
                    zoomLevel = 32
                }
                32 -> {
                    zoomBoard16()
                    zoomLevel = 16
                }
            }
        }

        zoomInButton.setOnClickListener { // 확대
            when (zoomLevel) {
                16 -> {
                    zoomBoard32()
                    zoomLevel = 32

                }
                32 -> {
                    zoomBoard64()
                    zoomLevel = 64
                }
            }
        }

        //내가 화면에서 돌을 두기 위해 imageButton을 클릭하면 src의 변화를 주는 부분
        for (i in 0..18) {
            val str = "0"
            var boardStr = "sBoard"
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

                    /** 사용자가 보드 클릭 시*/
                    if (stoneClickCnt == 0) { // 처음 클릭한 것이면 확인하기 위해 클릭을 한 번 더하도록 유도합니다.
                        // 즉 해당 위치를 체크표시합니다.
                        setCheckStone(imageButton, i, j)
                        stoneClickCnt = 1
                        checkDiamondStone[i][j] = 1

                        confirmX =
                            i // 사용자가 다시 이 위치를 클릭하는지 확인하기 위해 confirmX와 confirmY변수에 사용자가 클릭한 i,j를 저장합니다.
                        confirmY = j
                    } else if (stoneClickCnt == 1) { // 두 번째로 클릭한 것이면 해당 위치에 돌을 둡니다.


                        if (i == confirmX && j == confirmY) { // 두 번째로 돌을 둔 곳이 같은 위치라면 해당 위치에 돌을 둡니다.
                            playerClicked += 1
                            println("i : $i, confirmX : $confirmX, j : $j, confirmY : $confirmY")
                            soundPool.play(soundID, 1f, 1f, 0, 0, 1f);    // 돌 두는 소리가 나는 부분입니다
                            turnNum++ // 사용자가 클릭해서 돌을 착수했으므로 turnNum을 증가시킨다.
                            setImageClickable()
                            if (turnNum >= 4) { // 세번째 전에 둔 돌들로부터 최근 돌 마크를 빼고 일반 돌로 바꿔줍니다.
                                val coX: Int = turnDataList[turnNum - 4].coX
                                val coY: Int = turnDataList[turnNum - 4].coY
                                val boardFinalStone =
                                    "sBoard".plus(if (coX < 10) "0".plus(coX) else coX.toString())
                                        .plus(if (coY < 10) "0".plus(coY) else coY.toString())
                                //boardFinalStone에는 원래 돌로 바꿔야할 위치의 좌표값이 들어있습니다.
                                val targetStone: Int =
                                    resources.getIdentifier(boardFinalStone, "id", packageName)
                                //targetStone에는 R.id.board0000 꼴로 바꿔야할 id가 들어있습니다.

                                val targetImageButton: ImageButton = findViewById(targetStone)
                                when (turnNum % 4) {
                                    3, 2 -> {
                                        checkStoneArray[coX][coY] = 0 // 체크 된 돌을 일반 돌로 바꾼다
                                        if (stoneColorArray[coX][coY] == 1) {
                                            changetoOriginalStone(
                                                targetImageButton,
                                                "black",
                                                coX,
                                                coY
                                            )
                                        } else if (stoneColorArray[coX][coY] == 2) {
                                            changetoOriginalStone(
                                                targetImageButton,
                                                "white",
                                                coX,
                                                coY
                                            )
                                        }
                                    }
                                    0, 1 -> {
                                        checkStoneArray[coX][coY] = 0 // 체크 된 돌을 일반 돌로 바꾼다
                                        if (stoneColorArray[coX][coY] == 1) {
                                            changetoOriginalStone(
                                                targetImageButton,
                                                "black",
                                                coX,
                                                coY
                                            )
                                        } else if (stoneColorArray[coX][coY] == 2) {
                                            changetoOriginalStone(
                                                targetImageButton,
                                                "white",
                                                coX,
                                                coY
                                            )
                                        }
                                    }
                                }
                            }

                            //println("@@@ turnNum : $turnNum")

                            clickStoneChanged = false
                            when (turnNum % 4) {
                                1, 0 -> { // turnNum(시도 횟수)가 4로 나눴을 때 1 또는 0이면 player 1 차례다.
                                    if (playerColor == "black") {
                                        setStoneDependOnSize(imageButton, "black", i, j)
                                    } else {
                                        setStoneDependOnSize(imageButton, "white", i, j)
                                    }
                                    checkStoneArray[i][j] = 1 // 지금 체크된 돌임을 명시
                                }
                                2, 3 -> { // 2,3이면 player 2 차례다
                                    if (playerColor == "black") {
                                        setStoneDependOnSize(imageButton, "black", i, j)
                                    } else {
                                        setStoneDependOnSize(imageButton, "white", i, j)
                                    }
                                    checkStoneArray[i][j] = 1 // 지금 체크된 돌임을 명시
                                }
                            }
                            stoneCheckArray[i][j] = true // 좌표를 확인해서 배열에 착수가 되었다고 true 값 대입
                            imageButton.isClickable = false // 한 번 돌이 착수된 곳은 다시 클릭하지 못하도록 설정

                            stoneClickCnt = 0
                            checkDiamondStone[i][j] = 0
                            turnDataList.add(
                                Turn(
                                    "player",
                                    turnNum.toString(),
                                    i,
                                    j
                                )
                            ) // TurnDataList에 사용자가 어디에 착수했는지 기록합니다.
                            judgeVictory()

                            /**AI가 착수하는 부분입니다.*/
                            //printBoard()
                            if (playerColor == "white") { // 사용자가 흰 색일 때, 클릭을 두번 했는지 감지합니다.
                                if (playerClicked == 2) {
                                    checkDanger()
                                    printBoard()
                                    calcMinMax()
                                    //aiPlay()
                                    min1AiPlay()
                                    judgeVictory()
                                    //aiPlay()
                                    min2AiPlay()
                                    judgeVictory()
                                    playerClicked = 0
                                }
                            } else if (playerColor == "black") {
                                checkDanger()
                                printBoard()
                                //aiPlay()
                                calcMinMax()
                                min1AiPlay()
                                judgeVictory()
                                //aiPlay()
                                min2AiPlay()
                                judgeVictory()
                            }

                        } else { // 두 번째 클릭한 위치가 다른 위치라면(다이아몬드를 옮겨야 함)
                            Log.e("들어옴", "들어옴")
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

    // 최근에 둔 돌이어서 체크되었던 돌들을 체크되지 않은 일반적인 돌로 바꿔줍니다.
    private fun changetoOriginalStone(imageButton: ImageButton, color: String, i: Int, j: Int) {
        //println("%%% ${imageButton}, ${color}, ${i}, ${j}, $clickCnt")
        when (zoomLevel) {
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

    /*지금 화면에 보여지는 한 타일(바둑판 한 칸의 사이즈)의 크기(16,32,64)와 플레이어의 차례에 따라 색을 구분해
    * 바둑판에 돌을 두는 함수입니다.*/
    private fun setStoneDependOnSize(imageButton: ImageButton, color: String, i: Int, j: Int) {
        when (zoomLevel) {
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
                    Log.d("white", color)
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
                    Log.d("black", color)
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

    private fun changeStoneDependSize(i: Int, j: Int) {
        val boardStr = "sBoard".plus(if (i < 10) "0".plus(i.toString()) else i.toString())
            .plus(if (j < 10) "0".plus(j.toString()) else j.toString())
        val target: Int = resources.getIdentifier(boardStr, "id", packageName)
        val imageButton: ImageButton = findViewById(target)

        when (zoomLevel) {
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

    private fun setCheckStone(imageButton: ImageButton, i: Int, j: Int) {
        when (zoomLevel) {
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
                    imageButton.setImageResource(R.drawable.standard_confirm64)
                }
            }
        }
    }

    private fun showDialogAndChooseColor() {
        val d = Dialog(this) // 처음에 들어가면 dialog 보여줘야 함
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        d.setContentView(R.layout.dialog_choose_black_white_single_player)
        val whiteButton = d.findViewById<Button>(R.id.chooseWhite) // Dialog에서 White button
        val blackButton = d.findViewById<Button>(R.id.chooseBlack) // Dialog에서 Black button
        val chooseTextView = d.findViewById<TextView>(R.id.chooseTextView)
        val divLine1 = d.findViewById<LinearLayout>(R.id.divisionLine1)
        val divLine2 = d.findViewById<LinearLayout>(R.id.divisionLine2)

        when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_NO -> { // Dark Mode 아닐 때
                chooseTextView.setBackgroundColor(
                    ContextCompat.getColor(
                        applicationContext,
                        R.color.dimGray
                    )
                )
                chooseTextView.setTextColor(
                    ContextCompat.getColor(
                        applicationContext,
                        R.color.white
                    )
                )
                whiteButton.setBackgroundColor(
                    ContextCompat.getColor(
                        applicationContext,
                        R.color.white
                    )
                )
                whiteButton.setTextColor(ContextCompat.getColor(applicationContext, R.color.black))
                blackButton.setBackgroundColor(
                    ContextCompat.getColor(
                        applicationContext,
                        R.color.white
                    )
                )
                blackButton.setTextColor(ContextCompat.getColor(applicationContext, R.color.black))
                divLine1.setBackgroundColor(
                    ContextCompat.getColor(
                        applicationContext,
                        R.color.black
                    )
                )
                divLine2.setBackgroundColor(
                    ContextCompat.getColor(
                        applicationContext,
                        R.color.black
                    )
                )
            }
            Configuration.UI_MODE_NIGHT_YES -> { // Dark Mode일 때
                chooseTextView.setBackgroundColor(
                    ContextCompat.getColor(
                        applicationContext,
                        R.color.darkGray
                    )
                )
                chooseTextView.setTextColor(
                    ContextCompat.getColor(
                        applicationContext,
                        R.color.white
                    )
                )
                whiteButton.setBackgroundColor(
                    ContextCompat.getColor(
                        applicationContext,
                        R.color.dimGray
                    )
                )
                whiteButton.setTextColor(ContextCompat.getColor(applicationContext, R.color.white))
                blackButton.setBackgroundColor(
                    ContextCompat.getColor(
                        applicationContext,
                        R.color.dimGray
                    )
                )
                blackButton.setTextColor(ContextCompat.getColor(applicationContext, R.color.white))
                divLine1.setBackgroundColor(
                    ContextCompat.getColor(
                        applicationContext,
                        R.color.white
                    )
                )
                divLine2.setBackgroundColor(
                    ContextCompat.getColor(
                        applicationContext,
                        R.color.white
                    )
                )
            }
        }

        d.show()
        whiteButton.setOnClickListener {
            playerColor = "white"
            Log.d("playerColor", playerColor)
            setImageClickable()
            d.dismiss()

            /**사용자가 흰색을 선택했으므로 ai가 먼저 착수해야 한다.
             * 아래 부분은 AI가 첫 수를 (8,8)에 먼저 착수하는 것이다.*/
            val target = resources.getIdentifier("sBoard0909", "id", packageName)
            val imageButton = findViewById<ImageButton>(target)
            setStoneDependOnSize(imageButton, "black", 9, 9)
            stoneCheckArray[9][9] = true
            stoneColorArray[9][9] = 1
            turnNum++
            setImageClickable()
            turnDataList.add(Turn("ai", turnNum.toString(), 9, 9))
        }

        blackButton.setOnClickListener {
            playerColor = "black"
            Log.d("playerColor", playerColor)
            println(playerColor)
            setImageClickable()
            d.dismiss()
        }
    }

    private fun zoomBoard32() {
        for (i in 0..18) { // 행
            val str = "0"
            var boardStr = "sBoard"

            val firstNum =
                if (i < 10) str.plus(i.toString()) else i.toString() // board 뒤에 바로 붙는 첫 번째 수

            boardStr = boardStr.plus(firstNum) // board00까지 완성

            for (j in 0..18) { // 열
                val boardAndFirstNum = boardStr // boardStr값은 변경되지 않도록 함
                val zero = "0"
                val boardFinal =
                    boardAndFirstNum.plus(if (j < 10) zero.plus(j) else j.toString()) // 만약 j가 10보다 작으면 j 앞에 0을 붙이고, j가 10이상이면 그대로 j를 저장
                //최종적으로 boardFinal에는 board0000꼴로 id가 저장되게 된다.


                /*좌표 위치를 통해 ImageButton의 크기를 줄이는 부분*/
                val target: Int =
                    resources.getIdentifier(boardFinal, "id", packageName) // id 값을 target에 저장
                val imageButton: ImageButton =
                    findViewById(target) // id값(target)과 findViewById를 통해 imageButton 변수에 좌표에 해당하는 값 할당


                if (checkDiamondStone[i][j] == 1) {
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
            var boardStr = "sBoard"

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


                /*좌표 위치를 통해 ImageButton의 크기를 줄이는 부분*/
                val target: Int =
                    resources.getIdentifier(boardFinal, "id", packageName) // id 값을 target에 저장
                val imageButton: ImageButton =
                    findViewById(target) // id값(target)과 findViewById를 통해 imageButton 변수에 좌표에 해당하는 값 할당

                if (checkDiamondStone[i][j] == 1) {
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
            var boardStr = "sBoard"

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


                /*좌표 위치를 통해 ImageButton의 크기를 줄이는 부분*/
                val target: Int =
                    resources.getIdentifier(boardFinal, "id", packageName) // id 값을 target에 저장
                val imageButton: ImageButton =
                    findViewById(target) // id값(target)과 findViewById를 통해 imageButton 변수에 좌표에 해당하는 값 할당



                if (checkDiamondStone[i][j] == 1) {
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
        for (i in calcScoreArray.indices) {
            for (j in calcScoreArray[i].indices) {
                calcScoreArray[i][j] = 0 // 주위에 착수된 값이 없어서 0이다.
            }
        }
        for (i in calcPlayerScoreArray.indices) {
            for (j in calcPlayerScoreArray[i].indices) {
                calcPlayerScoreArray[i][j] = 0 // 주위에 착수된 값이 없어서 0이다.
            }
        }
    }


    /**인공지능이 위험을 감지하는 함수입니다. TODO 아직 이 함수는 완료된 함수가 아닙니다.
     * stoneColorArray에서 1은 black, 2는 white입니다.
     * 인공지능 입장에서 유리한 것은 +1, 불리한 것은 -1합니다.
     * 즉 인공지능 돌 주변은 유리하다고 판단하여 +1을 하고, 사용자 돌 주변은 위험하다고 판단하여(유리하지 않다고 판단하여) -1을 합니다.*/
    private fun checkDanger() {
        println("AI is checking Danger")
        if (playerColor == "black") { // 만약 player가 검은돌이라면, 인공지능은 1이 연속된 것이 있는지 확인해야 합니다.
            for (i in 0..18) {
                for (j in 0..18) {
                    for (k in 0 until 8) {

                        // 끝 면에서 바둑판 바깥 방향으로 나가려고 하면 continue해서 다음 값 계산으로 넘깁니다.
                        if (i == 0 && j == 0 && (k == 2 || k == 3 || k == 4 || k == 5 || k == 6)) continue // 왼쪽 위 코너
                        else if (i == 18 && j == 0 && (k == 0 || k == 1 || k == 2 || k == 3 || k == 4)) continue // 왼쪽 아래 코너
                        else if (i == 18 && j == 18 && (k == 0 || k == 1 || k == 2 || k == 6 || k == 7)) continue // 오른쪽 아래 코너
                        else if (i == 0 && j == 18 && (k == 0 || k == 4 || k == 5 || k == 6 || k == 7)) continue // 오른쪽 위 코너
                        else if (i == 0 && j in 1..17 && (k == 4 || k == 5 || k == 6)) continue // 윗면
                        else if (i == 18 && j in 1..17 && (k == 0 || k == 1 || k == 2)) continue // 아랫면
                        else if (i in 1..17 && j == 0 && (k == 2 || k == 3 || k == 4)) continue // 왼쪽 면
                        else if (i in 1..17 && j == 18 && (k == 0 || k == 7 || k == 6)) continue // 오른쪽 면

                        /**
                         * AI 입장에서 유리한 값을 -로 나타내며, 사용자에게 유리한 값을 +로 나타낸다.
                         * 이 부분은 AI가 착수해야 할 곳을 판단하는 부분이다.
                         * calcScore에서는 calcScoreArray(AI가 유리한 곳 판단하는 배열, -)와
                         * calcPlayerScoreArray(사용자가 유리한 곳을 판단하는 배열, +)에 가중치를 입력합니다.
                         * */
                        if (stoneColorArray[i][j] == 1 && countStone(i, j, k, 1) == 1) {
                            when (k) {
                                0 -> calcScore(i + 1, j + 1, -1)
                                1 -> calcScore(i + 1, j, -1)
                                2 -> calcScore(i + 1, j - 1, -1)
                                3 -> calcScore(i, j - 1, -1)
                                4 -> calcScore(i - 1, j - 1, -1)
                                5 -> calcScore(i - 1, j, -1)
                                6 -> calcScore(i - 1, j + 1, -1)
                                7 -> calcScore(i, j + 1, -1)
                            }
                        } else if (stoneColorArray[i][j] == 1 && countStone(i, j, k, 1) == 2) {
                            when (k) {
                                0 -> calcScore(i + 1, j + 1, -5)
                                1 -> calcScore(i + 1, j, -5)
                                2 -> calcScore(i + 1, j - 1, -5)
                                3 -> calcScore(i, j - 1, -5)
                                4 -> calcScore(i - 1, j - 1, -5)
                                5 -> calcScore(i - 1, j, -5)
                                6 -> calcScore(i - 1, j + 1, -5)
                                7 -> calcScore(i, j + 1, -5)
                            }
                        } else if (stoneColorArray[i][j] == 1 && countStone(i, j, k, 1) == 3) {
                            when (k) {
                                0 -> calcScore(i + 1, j + 1, -10)
                                1 -> calcScore(i + 1, j, -10)
                                2 -> calcScore(i + 1, j - 1, -10)
                                3 -> calcScore(i, j - 1, -10)
                                4 -> calcScore(i - 1, j - 1, -10)
                                5 -> calcScore(i - 1, j, -10)
                                6 -> calcScore(i - 1, j + 1, -10)
                                7 -> calcScore(i, j + 1, -10)
                            }
                        } else if (stoneColorArray[i][j] == 1 && countStone(i, j, k, 1) == 4) {
                            when (k) {
                                0 -> calcScore(i + 1, j + 1, -50)
                                1 -> calcScore(i + 1, j, -50)
                                2 -> calcScore(i + 1, j - 1, -50)
                                3 -> calcScore(i, j - 1, -50)
                                4 -> calcScore(i - 1, j - 1, -50)
                                5 -> calcScore(i - 1, j, -50)
                                6 -> calcScore(i - 1, j + 1, -50)
                                7 -> calcScore(i, j + 1, -50)
                            }
                            when (k) {
                                0 -> calcScore(i + 1, j + 1, -50)
                                1 -> calcScore(i + 1, j, -50)
                                2 -> calcScore(i + 1, j - 1, -50)
                                3 -> calcScore(i, j - 1, -50)
                                4 -> calcScore(i - 1, j - 1, -50)
                                5 -> calcScore(i - 1, j, -50)
                                6 -> calcScore(i - 1, j + 1, -50)
                                7 -> calcScore(i, j + 1, -50)
                            }
                        } else if (stoneColorArray[i][j] == 1 && countStone(i, j, k, 1) == 5) {
                            when (k) {
                                0 -> calcScore(i + 1, j + 1, -100)
                                1 -> calcScore(i + 1, j, -100)
                                2 -> calcScore(i + 1, j - 1, -100)
                                3 -> calcScore(i, j - 1, -100)
                                4 -> calcScore(i - 1, j - 1, -100)
                                5 -> calcScore(i - 1, j, -100)
                                6 -> calcScore(i - 1, j + 1, -100)
                                7 -> calcScore(i, j + 1, -100)
                            }
                        }

                        // ai색(여기서는 흰 색)이 연속적으로 다섯 개가 연결되어 있고 위 또는 아래가 비어 있을 때 바로 착수할 수 있도록 가중치를 매우 낮게 잡습니다.
                        if (stoneColorArray[i][j] == 2 && countStone(i, j, k, 2) == 5) {
                            when (k) {
                                0 -> calcScore(i + 1, j + 1, -150)
                                1 -> calcScore(i + 1, j, -150)
                                2 -> calcScore(i + 1, j - 1, -150)
                                3 -> calcScore(i, j - 1, -150)
                                4 -> calcScore(i - 1, j - 1, -150)
                                5 -> calcScore(i - 1, j, -150)
                                6 -> calcScore(i - 1, j + 1, -150)
                                7 -> calcScore(i, j + 1, -150)
                            }
                        } else if (stoneColorArray[i][j] == 2 && countStone(i, j, k, 2) == 4) {
                            when (k) {
                                0 -> {
                                    if (i + 2 < 19 && j + 2 < 19 && stoneColorArray[i + 2][j + 2] == 0 && stoneColorArray[i + 1][j + 1] == 0) {
                                        // 네 개가 연속적으로 이어져 있고 방향 k로 두 칸이 더 비어 있을 때 그 쪽으로 두 돌을 착수하여 게임을 끝낼 수 있다.
                                        // 그러므로 해당 위치에 -150을 주어 바로 게임을 끝낼 수 있게 한다.
                                        calcScore(i + 1, j + 1, -150)
                                        calcScore(i + 2, j + 2, -150)
                                    } else if (i + 2 < 19 && j + 2 < 19 && stoneColorArray[i + 2][j + 2] == 0 && (stoneColorArray[i + 1][j + 1] == 1 || stoneColorArray[i + 1][j + 1] == 2)) {
                                        // 만약 네 개가 연결되어 있었는데 양 쪽 모두 막혀 있다면, 이전에 -150으로 설정해주었던 부분을 다시 0으로 되돌려 놓는다.
                                        // 해당 위치에 돌이 있으므로 0이되어도 상관 없다.
                                        // stoneColorArray[i+1][j+1]이 1 또는 2여도 상관 없다. 이미 위에서 5개짜리는 처리가 되므로 무조건 상대돌이 오기 때문이다.
                                        calcScore(i + 2, j + 2, 0)
                                    } else {
                                        calcScore(i + 1, j + 1, -40)
                                    }
                                }
                                1 -> {
                                    if (i + 2 < 19 && stoneColorArray[i + 2][j] == 0 && stoneColorArray[i + 1][j] == 0) {
                                        calcScore(i + 1, j, -150)
                                        calcScore(i + 2, j, -150)
                                    } else if (i + 2 < 19 && stoneColorArray[i + 2][j] == 0 && (stoneColorArray[i + 1][j] == 1 || stoneColorArray[i + 1][j] == 2)) {
                                        calcScore(i + 2, j, 0)
                                    } else {
                                        calcScore(i + 1, j, -40)
                                    }
                                }
                                2 -> {
                                    if (i + 2 < 19 && j - 2 >= 0 && stoneColorArray[i + 2][j - 2] == 0 && stoneColorArray[i + 1][j - 1] == 0) {
                                        calcScore(i + 1, j - 1, -150)
                                        calcScore(i + 2, j - 2, -150)
                                    } else if (i + 2 < 19 && j - 2 >= 0 && stoneColorArray[i + 2][j - 2] == 0 && (stoneColorArray[i + 1][j - 1] == 1 || stoneColorArray[i + 1][j - 1] == 2)) {
                                        calcScore(i + 2, j - 2, 0)
                                    } else {
                                        calcScore(i + 1, j - 1, -40)
                                    }
                                }
                                3 -> {
                                    if (j - 2 >= 0 && stoneColorArray[i][j - 2] == 0 && stoneColorArray[i][j - 1] == 0) {
                                        calcScore(i, j - 1, -150)
                                        calcScore(i, j - 2, -150)
                                    } else if (j - 2 >= 0 && stoneColorArray[i][j - 2] == 0 && (stoneColorArray[i][j - 1] == 1 || stoneColorArray[i][j - 1] == 2)) {
                                        calcScore(i, j - 2, 0)
                                    } else {
                                        calcScore(i, j - 1, -40)
                                    }
                                }
                                4 -> {
                                    if (i - 2 >= 0 && j - 2 >= 0 && stoneColorArray[i - 2][j - 2] == 0 && stoneColorArray[i - 1][j - 1] == 0) {
                                        calcScore(i - 1, j - 1, -150)
                                        calcScore(i - 2, j - 2, -150)
                                    } else if (i - 2 >= 0 && j - 2 >= 0 && stoneColorArray[i - 2][j - 2] == 0 && (stoneColorArray[i - 1][j - 1] == 1 || stoneColorArray[i - 1][j - 1] == 2)) {
                                        calcScore(i - 2, j - 2, 0)
                                    } else {
                                        calcScore(i - 1, j - 1, -40)
                                    }
                                }
                                5 -> {
                                    if (i - 2 >= 0 && stoneColorArray[i - 2][j] == 0 && stoneColorArray[i - 1][j] == 0) {
                                        calcScore(i - 1, j, -150)
                                        calcScore(i - 2, j, -150)
                                    } else if (i - 2 >= 0 && stoneColorArray[i - 2][j] == 0 && (stoneColorArray[i - 1][j] == 1 || stoneColorArray[i - 1][j] == 2)) {
                                        calcScore(i - 2, j, 0)
                                    } else {
                                        calcScore(i - 1, j, -40)
                                    }
                                }
                                6 -> {
                                    if (i - 2 >= 0 && j + 2 < 19 && stoneColorArray[i - 2][j + 2] == 0 && stoneColorArray[i - 1][j + 1] == 0) {
                                        calcScore(i - 1, j + 1, -150)
                                        calcScore(i - 2, j + 2, -150)
                                    } else if (i - 2 >= 0 && j + 2 < 19 && stoneColorArray[i - 2][j + 2] == 0 && (stoneColorArray[i - 1][j + 1] == 1 || stoneColorArray[i - 1][j + 1] == 2)) {
                                        calcScore(i - 2, j + 2, 0)
                                    } else {
                                        calcScore(i - 1, j + 1, -40)
                                    }
                                }
                                7 -> {
                                    if (j + 2 < 19 && stoneColorArray[i][j + 2] == 0 && stoneColorArray[i][j + 1] == 0) {
                                        calcScore(i, j + 1, -150)
                                        calcScore(i, j + 2, -150)
                                    } else if (j + 2 < 19 && stoneColorArray[i][j + 2] == 0 && (stoneColorArray[i][j + 1] == 1 || stoneColorArray[i][j + 1] == 2)) {
                                        calcScore(i, j + 2, 0)
                                    } else {
                                        calcScore(i, j + 1, -40)
                                    }
                                }
                            }
                        } else if (stoneColorArray[i][j] == 2 && countStone(i, j, k, 2) == 3) {
                            when (k) {
                                0 -> calcScore(i + 1, j + 1, -20)
                                1 -> calcScore(i + 1, j, -20)
                                2 -> calcScore(i + 1, j - 1, -20)
                                3 -> calcScore(i, j - 1, -20)
                                4 -> calcScore(i - 1, j - 1, -20)
                                5 -> calcScore(i - 1, j, -20)
                                6 -> calcScore(i - 1, j + 1, -20)
                                7 -> calcScore(i, j + 1, -20)
                            }
                        }

                    }
                }
            }
        } else if (playerColor == "white") {
            for (i in 0..18) {
                for (j in 0..18) {
                    for (k in 0 until 8) {

                        // 끝 면에서 바둑판 바깥 방향으로 나가려고 하면 continue해서 다음 값 계산으로 넘깁니다.
                        if (i == 0 && j == 0 && (k == 2 || k == 3 || k == 4 || k == 5 || k == 6)) continue // 왼쪽 위 코너
                        else if (i == 18 && j == 0 && (k == 0 || k == 1 || k == 2 || k == 3 || k == 4)) continue // 왼쪽 아래 코너
                        else if (i == 18 && j == 18 && (k == 0 || k == 1 || k == 2 || k == 6 || k == 7)) continue // 오른쪽 아래 코너
                        else if (i == 0 && j == 18 && (k == 0 || k == 4 || k == 5 || k == 6 || k == 7)) continue // 오른쪽 위 코너
                        else if (i == 0 && j in 1..17 && (k == 4 || k == 5 || k == 6)) continue // 윗면
                        else if (i == 18 && j in 1..17 && (k == 0 || k == 1 || k == 2)) continue // 아랫면
                        else if (i in 1..17 && j == 0 && (k == 2 || k == 3 || k == 4)) continue // 왼쪽 면
                        else if (i in 1..17 && j == 18 && (k == 0 || k == 7 || k == 6)) continue // 오른쪽 면

                        if (stoneColorArray[i][j] == 2 && countStone(i, j, k, 2) == 1) {
                            when (k) {
                                0 -> calcScore(i + 1, j + 1, -1)
                                1 -> calcScore(i + 1, j, -1)
                                2 -> calcScore(i + 1, j - 1, -1)
                                3 -> calcScore(i, j - 1, -1)
                                4 -> calcScore(i - 1, j - 1, -1)
                                5 -> calcScore(i - 1, j, -1)
                                6 -> calcScore(i - 1, j + 1, -1)
                                7 -> calcScore(i, j + 1, -1)
                            }
                        } else if (stoneColorArray[i][j] == 2 && countStone(i, j, k, 2) == 2) {
                            when (k) {
                                0 -> calcScore(i + 1, j + 1, -5)
                                1 -> calcScore(i + 1, j, -5)
                                2 -> calcScore(i + 1, j - 1, -5)
                                3 -> calcScore(i, j - 1, -5)
                                4 -> calcScore(i - 1, j - 1, -5)
                                5 -> calcScore(i - 1, j, -5)
                                6 -> calcScore(i - 1, j + 1, -5)
                                7 -> calcScore(i, j + 1, -5)
                            }
                        } else if (stoneColorArray[i][j] == 2 && countStone(i, j, k, 2) == 3) {
                            when (k) {
                                0 -> calcScore(i + 1, j + 1, -10)
                                1 -> calcScore(i + 1, j, -10)
                                2 -> calcScore(i + 1, j - 1, -10)
                                3 -> calcScore(i, j - 1, -10)
                                4 -> calcScore(i - 1, j - 1, -10)
                                5 -> calcScore(i - 1, j, -10)
                                6 -> calcScore(i - 1, j + 1, -10)
                                7 -> calcScore(i, j + 1, -10)
                            }
                        } else if (stoneColorArray[i][j] == 2 && countStone(i, j, k, 2) == 4) {
                            when (k) {
                                0 -> calcScore(i + 1, j + 1, -50)
                                1 -> calcScore(i + 1, j, -50)
                                2 -> calcScore(i + 1, j - 1, -50)
                                3 -> calcScore(i, j - 1, -50)
                                4 -> calcScore(i - 1, j - 1, -50)
                                5 -> calcScore(i - 1, j, -50)
                                6 -> calcScore(i - 1, j + 1, -50)
                                7 -> calcScore(i, j + 1, -50)
                            }
                        } else if (stoneColorArray[i][j] == 2 && countStone(i, j, k, 2) == 5) {
                            when (k) {
                                0 -> calcScore(i + 1, j + 1, -100)
                                1 -> calcScore(i + 1, j, -100)
                                2 -> calcScore(i + 1, j - 1, -100)
                                3 -> calcScore(i, j - 1, -100)
                                4 -> calcScore(i - 1, j - 1, -100)
                                5 -> calcScore(i - 1, j, -100)
                                6 -> calcScore(i - 1, j + 1, -100)
                                7 -> calcScore(i, j + 1, -100)
                            }
                        }

                        // ai색(여기서는 흰 색)이 연속적으로 다섯 개가 연결되어 있고 위 또는 아래가 비어 있을 때 바로 착수할 수 있도록 가중치를 매우 낮게 잡습니다.
                        if (stoneColorArray[i][j] == 1 && countStone(i, j, k, 1) == 5) {
                            when (k) {
                                0 -> calcScore(i + 1, j + 1, -150)
                                1 -> calcScore(i + 1, j, -150)
                                2 -> calcScore(i + 1, j - 1, -150)
                                3 -> calcScore(i, j - 1, -150)
                                4 -> calcScore(i - 1, j - 1, -150)
                                5 -> calcScore(i - 1, j, -150)
                                6 -> calcScore(i - 1, j + 1, -150)
                                7 -> calcScore(i, j + 1, -150)
                            }
                        } else if (stoneColorArray[i][j] == 1 && countStone(i, j, k, 1) == 4) {
                            when (k) {
                                0 -> {
                                    if (i + 2 < 19 && j + 2 < 19 && stoneColorArray[i + 2][j + 2] == 0 && stoneColorArray[i + 1][j + 1] == 0) {
                                        // 네 개가 연속적으로 이어져 있고 방향 k로 두 칸이 더 비어 있을 때 그 쪽으로 두 돌을 착수하여 게임을 끝낼 수 있다.
                                        // 그러므로 해당 위치에 -150을 주어 바로 게임을 끝낼 수 있게 한다.
                                        calcScore(i + 1, j + 1, -150)
                                        calcScore(i + 2, j + 2, -150)
                                    } else if (i + 2 < 19 && j + 2 < 19 && stoneColorArray[i + 2][j + 2] == 0 && (stoneColorArray[i + 1][j + 1] == 1 || stoneColorArray[i + 1][j + 1] == 2)) {
                                        // 만약 네 개가 연결되어 있었는데 양 쪽 모두 막혀 있다면, 이전에 -150으로 설정해주었던 부분을 다시 0으로 되돌려 놓는다.
                                        // 해당 위치에 돌이 있으므로 0이되어도 상관 없다.
                                        // stoneColorArray[i+1][j+1]이 1 또는 2여도 상관 없다. 이미 위에서 5개짜리는 처리가 되므로 무조건 상대돌이 오기 때문이다.
                                        calcScore(i + 2, j + 2, 0)
                                    } else {
                                        calcScore(i + 1, j + 1, -40)
                                    }
                                }
                                1 -> {
                                    if (i + 2 < 19 && stoneColorArray[i + 2][j] == 0 && stoneColorArray[i + 1][j] == 0) {
                                        calcScore(i + 1, j, -150)
                                        calcScore(i + 2, j, -150)
                                    } else if (i + 2 < 19 && stoneColorArray[i + 2][j] == 0 && (stoneColorArray[i + 1][j] == 1 || stoneColorArray[i + 1][j] == 2)) {
                                        calcScore(i + 2, j, 0)
                                    } else {
                                        calcScore(i + 1, j, -40)
                                    }
                                }
                                2 -> {
                                    if (i + 2 < 19 && j - 2 >= 0 && stoneColorArray[i + 2][j - 2] == 0 && stoneColorArray[i + 1][j - 1] == 0) {
                                        calcScore(i + 1, j - 1, -150)
                                        calcScore(i + 2, j - 2, -150)
                                    } else if (i + 2 < 19 && j - 2 >= 0 && stoneColorArray[i + 2][j - 2] == 0 && (stoneColorArray[i + 1][j - 1] == 1 || stoneColorArray[i + 1][j - 1] == 2)) {
                                        calcScore(i + 2, j - 2, 0)
                                    } else {
                                        calcScore(i + 1, j - 1, -40)
                                    }
                                }
                                3 -> {
                                    if (j - 2 >= 0 && stoneColorArray[i][j - 2] == 0 && stoneColorArray[i][j - 1] == 0) {
                                        calcScore(i, j - 1, -150)
                                        calcScore(i, j - 2, -150)
                                    } else if (j - 2 >= 0 && stoneColorArray[i][j - 2] == 0 && (stoneColorArray[i][j - 1] == 1 || stoneColorArray[i][j - 1] == 2)) {
                                        calcScore(i, j - 2, 0)
                                    } else {
                                        calcScore(i, j - 1, -40)
                                    }
                                }
                                4 -> {
                                    if (i - 2 >= 0 && j - 2 >= 0 && stoneColorArray[i - 2][j - 2] == 0 && stoneColorArray[i - 1][j - 1] == 0) {
                                        calcScore(i - 1, j - 1, -150)
                                        calcScore(i - 2, j - 2, -150)
                                    } else if (i - 2 >= 0 && j - 2 >= 0 && stoneColorArray[i - 2][j - 2] == 0 && (stoneColorArray[i - 1][j - 1] == 1 || stoneColorArray[i - 1][j - 1] == 2)) {
                                        calcScore(i - 2, j - 2, 0)
                                    } else {
                                        calcScore(i - 1, j - 1, -40)
                                    }
                                }
                                5 -> {
                                    if (i - 2 >= 0 && stoneColorArray[i - 2][j] == 0 && stoneColorArray[i - 1][j] == 0) {
                                        calcScore(i - 1, j, -150)
                                        calcScore(i - 2, j, -150)
                                    } else if (i - 2 >= 0 && stoneColorArray[i - 2][j] == 0 && (stoneColorArray[i - 1][j] == 1 || stoneColorArray[i - 1][j] == 2)) {
                                        calcScore(i - 2, j, 0)
                                    } else {
                                        calcScore(i - 1, j, -40)
                                    }
                                }
                                6 -> {
                                    if (i - 2 >= 0 && j + 2 < 19 && stoneColorArray[i - 2][j + 2] == 0 && stoneColorArray[i - 1][j + 1] == 0) {
                                        calcScore(i - 1, j + 1, -150)
                                        calcScore(i - 2, j + 2, -150)
                                    } else if (i - 2 >= 0 && j + 2 < 19 && stoneColorArray[i - 2][j + 2] == 0 && (stoneColorArray[i - 1][j + 1] == 1 || stoneColorArray[i - 1][j + 1] == 2)) {
                                        calcScore(i - 2, j + 2, 0)
                                    } else {
                                        calcScore(i - 1, j + 1, -40)
                                    }
                                }
                                7 -> {
                                    if (j + 2 < 19 && stoneColorArray[i][j + 2] == 0 && stoneColorArray[i][j + 1] == 0) {
                                        calcScore(i, j + 1, -150)
                                        calcScore(i, j + 2, -150)
                                    } else if (j + 2 < 19 && stoneColorArray[i][j + 2] == 0 && (stoneColorArray[i][j + 1] == 1 || stoneColorArray[i][j + 1] == 2)) {
                                        calcScore(i, j + 2, 0)
                                    } else {
                                        calcScore(i, j + 1, -40)
                                    }
                                }
                            }
                        } else if (stoneColorArray[i][j] == 1 && countStone(i, j, k, 1) == 3) {
                            when (k) {
                                0 -> calcScore(i + 1, j + 1, -20)
                                1 -> calcScore(i + 1, j, -20)
                                2 -> calcScore(i + 1, j - 1, -20)
                                3 -> calcScore(i, j - 1, -20)
                                4 -> calcScore(i - 1, j - 1, -20)
                                5 -> calcScore(i - 1, j, -20)
                                6 -> calcScore(i - 1, j + 1, -20)
                                7 -> calcScore(i, j + 1, -20)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 보드를 분석하는 부분 중 보드에 값들을 입력하는 함수입니다.
     * */
    private fun calcScore(i: Int, j: Int, score: Int) {
        //val playerBoardScore = -score
        calcScoreArray[i][j] = min(score, calcScoreArray[i][j])
        calcPlayerScoreArray[i][j] = kotlin.math.max(-score, calcPlayerScoreArray[i][j])
    }

    /**
     * ai가 착수하기 위해서 제일 작은 값과 그 다음으로 작은 값,
     * 제일 큰 값과, 그 다음으로 큰 값 총 네 개를 구하는 부분입니다.
     * 이는 MinMax Algorithm에 사용될 수 있습니다.
     * */
    private fun calcMinMax() {
        var min1Val = 999999999
        var min2Val = 999999999

        var max1Val = -999999999
        var max2Val = -999999999

        //제일 작은 값 min1Val구하는 부분
        for (i in calcScoreArray.indices) {
            for (j in calcScoreArray[i].indices) {
                if (stoneColorArray[i][j] != 1 && stoneColorArray[i][j] != 2 && calcScoreArray[i][j] < min1Val) { // 만약 기존 최솟값보다 값이 작다면 최솟값 위치를 갱신해줍니다.
                    min1.i = i
                    min1.j = j
                    min1Val = calcScoreArray[i][j]
                }
            }
        }

        //두번 째로 작은 값 min2Val구하는 부분
        for (i in calcScoreArray.indices) {
            for (j in calcScoreArray[i].indices) {
                if (stoneColorArray[i][j] != 1 && stoneColorArray[i][j] != 2 && calcScoreArray[i][j] < min2Val) { // 만약 기존 최솟값보다 값이 작다면 최솟값 위치를 갱신해줍니다.
                    if (i == min1.i && j == min1.j) continue // min1보다 작은 것을 구해야 하므로 continue한다.
                    min2.i = i
                    min2.j = j
                    min2Val = calcScoreArray[i][j]
                }
            }
        }

        //제일 큰 값 max1Val 구하는 부분
        for (i in calcScoreArray.indices) {
            for (j in calcScoreArray[i].indices) {
                if (stoneColorArray[i][j] != 1 && stoneColorArray[i][j] != 2 && calcPlayerScoreArray[i][j] > max1Val) { // 만약 기존 최솟값보다 값이 작다면 최솟값 위치를 갱신해줍니다.
                    //if (((max1.i == min1.i) && (max1.j == min1.j)) || ((max1.i == min2.i) && (max1.j == min2.j))) continue
                    // 최솟값으로 두려고 예정 된 곳이면 continue한다.
                    max1.i = i
                    max1.j = j
                    max1Val = calcPlayerScoreArray[i][j]
                }
            }
        }

        for (i in calcScoreArray.indices) {
            for (j in calcScoreArray[i].indices) {
                if (stoneColorArray[i][j] != 1 && stoneColorArray[i][j] != 2 && calcPlayerScoreArray[i][j] > max2Val) { // 만약 기존 최솟값보다 값이 작다면 최솟값 위치를 갱신해줍니다.
                    if (((max1.i == min1.i) && (max1.j == min1.j)) || ((max1.i == min2.i) && (max1.j == min2.j)) || (i == max1.i && j == max1.j)) continue
                    // 이미 max1에 둔 값이거나 최솟값으로 두려고 예정 된 곳이면 continue한다.
                    if (i == max1.i && j == max1.j) continue // min1보다 작은 것을 구해야 하므로 continue한다.
                    max2.i = i
                    max2.j = j
                    max2Val = calcPlayerScoreArray[i][j]
                }
            }
        }
    }

    /**AI가 착수하는 부분입니다.
     * AI는 유저가 유리한 곳에 착수해야 하므로 calcArray의 최솟값에 착수합니다. */
    private fun min1AiPlay() {
        if (playerColor == "black" && (turnNum % 4 == 1 || turnNum % 4 == 0)) { // 사용자가 검은돌일 때, 인공지능은 흰 색이므로 2,3이 인공지능 차례다.
            //인공지능 차례가 아닐 때는 아래를  하지 않고 그냥 넘긴다.
            return
        }

        Log.e(
            "MIN",
            "minx1 : ${min1.i}, miny1 : ${min1.j}, minx2 : ${min2.i}, miny2 : ${min2.j}, min1Val : ${calcScoreArray[min1.i][min1.j]}, min2Val : ${calcScoreArray[min2.i][min2.j]}"
        )
        Log.e(
            "MAX",
            "maxx1 : ${max1.i}, maxy1 : ${max1.j}, maxx2 : ${max2.i}, maxy2 : ${max2.j}, max1Val : ${calcPlayerScoreArray[max1.i][max1.j]}, max2Val : ${calcScoreArray[max2.i][max2.j]}"
        )

        val minX = min1.i
        val minY = min1.j
        val boardStr = "sBoard".plus(if (minX < 10) "0".plus(minX) else minX)
            .plus(if (minY < 10) "0".plus(minY) else minY)
        val target = resources.getIdentifier(boardStr, "id", packageName)
        val imageButton = findViewById<ImageButton>(target)
        var soundPool = SoundPool(5, AudioManager.STREAM_MUSIC, 0)
        var soundID = soundPool.load(this, R.raw.sound_stone, 1)
        soundPool.play(soundID, 1f, 1f, 0, 0, 1f);    // 돌 두는 소리가 나는 부분입니다
        if (playerColor == "white") setStoneDependOnSize(imageButton, "black", minX, minY)
        else setStoneDependOnSize(imageButton, "white", minX, minY)
        turnNum++
        stoneCheckArray[minX][minY] = true // 좌표를 확인해서 배열에 착수가 되었다고 true 값 대입
        if (playerColor == "black") stoneColorArray[minX][minY] =
            2 // 만약 플레이어가 검은돌이면, 인공지능이 둔 위치에 흰색을 착수했다고 표시
        else if (playerColor == "white") stoneColorArray[minX][minY] = 1
        setImageClickable()

        //TODO 이 부분을 수정해야 할듯, 그리고 색바꿔주는 부분이 하나 더 있는데, 그 부분을 수정해야 할 것 같음.
        if (turnNum >= 4) { // 세번째 전에 둔 돌들로부터 최근 돌 마크를 빼고 일반 돌로 바꿔줍니다.

            println()
            val coX: Int = turnDataList[turnNum - 4].coX
            val coY: Int = turnDataList[turnNum - 4].coY
            val boardFinalStone =
                "sBoard".plus(if (coX < 10) "0".plus(coX) else coX.toString())
                    .plus(if (coY < 10) "0".plus(coY) else coY.toString())
            //boardFinalStone에는 원래 돌로 바꿔야할 위치의 좌표값이 들어있습니다.
            val targetStone: Int =
                resources.getIdentifier(boardFinalStone, "id", packageName)

            val targetImageButton: ImageButton = findViewById(targetStone)
            when (turnNum % 4) {
                3, 2 -> {
                    checkStoneArray[coX][coY] = 0 // 체크 된 돌을 일반 돌로 바꾼다
                    if (stoneColorArray[coX][coY] == 1) {
                        changetoOriginalStone(targetImageButton, "black", coX, coY)
                    } else if (stoneColorArray[coX][coY] == 2) {
                        changetoOriginalStone(targetImageButton, "white", coX, coY)
                    }
                }
                0, 1 -> {
                    checkStoneArray[coX][coY] = 0 // 체크 된 돌을 일반 돌로 바꾼다
                    if (stoneColorArray[coX][coY] == 1) {
                        changetoOriginalStone(targetImageButton, "black", coX, coY)
                    } else if (stoneColorArray[coX][coY] == 2) {
                        changetoOriginalStone(targetImageButton, "white", coX, coY)
                    }
                }
            }
        }

        turnDataList.add(
            Turn(
                "ai",
                turnNum.toString(),
                minX,
                minY
            )
        ) // TurnDataList에 인공지능이 어디에 착수했는지 기록합니다.
    }

    private fun min2AiPlay() {
        if (playerColor == "black" && (turnNum % 4 == 1 || turnNum % 4 == 0)) { // 사용자가 검은돌일 때, 인공지능은 흰 색이므로 2,3이 인공지능 차례다.
            //인공지능 차례가 아닐 때는 아래를  하지 않고 그냥 넘긴다.
            return
        }
        val minX = min2.i
        val minY = min2.j

        val boardStr = "sBoard".plus(if (minX < 10) "0".plus(minX) else minX)
            .plus(if (minY < 10) "0".plus(minY) else minY)
        val target = resources.getIdentifier(boardStr, "id", packageName)
        val imageButton = findViewById<ImageButton>(target)
        var soundPool = SoundPool(5, AudioManager.STREAM_MUSIC, 0)
        var soundID = soundPool.load(this, R.raw.sound_stone, 1)
        soundPool.play(soundID, 1f, 1f, 0, 0, 1f);    // 돌 두는 소리가 나는 부분입니다
        if (playerColor == "white") setStoneDependOnSize(imageButton, "black", minX, minY)
        else setStoneDependOnSize(imageButton, "white", minX, minY)
        turnNum++
        stoneCheckArray[minX][minY] = true // 좌표를 확인해서 배열에 착수가 되었다고 true 값 대입
        if (playerColor == "black") stoneColorArray[minX][minY] =
            2 // 만약 플레이어가 검은돌이면, 인공지능이 둔 위치에 흰색을 착수했다고 표시
        else if (playerColor == "white") stoneColorArray[minX][minY] = 1
        setImageClickable()

        //TODO 이 부분을 수정해야 할듯, 그리고 색바꿔주는 부분이 하나 더 있는데, 그 부분을 수정해야 할 것 같음.
        if (turnNum >= 4) { // 세번째 전에 둔 돌들로부터 최근 돌 마크를 빼고 일반 돌로 바꿔줍니다.

            println()
            val coX: Int = turnDataList[turnNum - 4].coX
            val coY: Int = turnDataList[turnNum - 4].coY
            val boardFinalStone =
                "sBoard".plus(if (coX < 10) "0".plus(coX) else coX.toString())
                    .plus(if (coY < 10) "0".plus(coY) else coY.toString())
            //boardFinalStone에는 원래 돌로 바꿔야할 위치의 좌표값이 들어있습니다.
            val targetStone: Int =
                resources.getIdentifier(boardFinalStone, "id", packageName)

            val targetImageButton: ImageButton = findViewById(targetStone)
            when (turnNum % 4) {
                3, 2 -> {
                    checkStoneArray[coX][coY] = 0 // 체크 된 돌을 일반 돌로 바꾼다
                    if (stoneColorArray[coX][coY] == 1) {
                        changetoOriginalStone(targetImageButton, "black", coX, coY)
                    } else if (stoneColorArray[coX][coY] == 2) {
                        changetoOriginalStone(targetImageButton, "white", coX, coY)
                    }
                }
                0, 1 -> {
                    checkStoneArray[coX][coY] = 0 // 체크 된 돌을 일반 돌로 바꾼다
                    if (stoneColorArray[coX][coY] == 1) {
                        changetoOriginalStone(targetImageButton, "black", coX, coY)
                    } else if (stoneColorArray[coX][coY] == 2) {
                        changetoOriginalStone(targetImageButton, "white", coX, coY)
                    }
                }
            }
        }

        turnDataList.add(
            Turn(
                "ai",
                turnNum.toString(),
                minX,
                minY
            )
        ) // TurnDataList에 인공지능이 어디에 착수했는지 기록합니다.
    }

    /**AI가 착수하는 부분입니다.
     * AI는 유저가 유리한 곳에 착수해야 하므로 calcArray의 최솟값에 착수합니다. */
    private fun aiPlay() {
        if (playerColor == "black" && (turnNum % 4 == 1 || turnNum % 4 == 0)) { // 사용자가 검은돌일 때, 인공지능은 흰 색이므로 2,3이 인공지능 차례다.
            //인공지능 차례가 아닐 때는 아래를  하지 않고 그냥 넘긴다.
            return
        }
        var minVal = 999999999 // 최솟값을 확인하는 변수입니다.
        var maxVal = -999999999
        var minX = 0 // 최솟값의 x좌표입니다.
        var minY = 0 // 최솟값의 y좌표입니다.
        var maxX = 0 // 최댓값의 x좌표입니다.
        var maxY = 0 // 최댓값의 y좌표입니다.
        for (i in calcScoreArray.indices) {
            for (j in calcScoreArray[i].indices) {
                if (stoneColorArray[i][j] != 1 && stoneColorArray[i][j] != 2 && calcScoreArray[i][j] < minVal) { // 만약 기존 최솟값보다 값이 작다면 최솟값 위치를 갱신해줍니다.
                    minX = i
                    minY = j
                    minVal = calcScoreArray[i][j]
                }
//                if (stoneColorArray[i][j] != 1 && stoneColorArray[i][j] != 2 && calcPlayerScoreArray[i][j] > maxVal) { // 만약 기존 최솟값보다 값이 작다면 최솟값 위치를 갱신해줍니다.
//                    maxX = i
//                    maxY = j
//                    maxVal = calcPlayerScoreArray[i][j]
//                }
            }
        }
//        Log.e("MAXVAL", "x : $maxX, y : $maxY, maxVal : $maxVal")
        val boardStr = "sBoard".plus(if (minX < 10) "0".plus(minX) else minX)
            .plus(if (minY < 10) "0".plus(minY) else minY)
        val target = resources.getIdentifier(boardStr, "id", packageName)
        val imageButton = findViewById<ImageButton>(target)
        var soundPool = SoundPool(5, AudioManager.STREAM_MUSIC, 0)
        var soundID = soundPool.load(this, R.raw.sound_stone, 1)
        soundPool.play(soundID, 1f, 1f, 0, 0, 1f);    // 돌 두는 소리가 나는 부분입니다
        if (playerColor == "white") setStoneDependOnSize(imageButton, "black", minX, minY)
        else setStoneDependOnSize(imageButton, "white", minX, minY)
        turnNum++
        stoneCheckArray[minX][minY] = true // 좌표를 확인해서 배열에 착수가 되었다고 true 값 대입
        if (playerColor == "black") stoneColorArray[minX][minY] =
            2 // 만약 플레이어가 검은돌이면, 인공지능이 둔 위치에 흰색을 착수했다고 표시
        else if (playerColor == "white") stoneColorArray[minX][minY] = 1
        setImageClickable()

        //TODO 이 부분을 수정해야 할듯, 그리고 색바꿔주는 부분이 하나 더 있는데, 그 부분을 수정해야 할 것 같음.
        if (turnNum >= 4) { // 세번째 전에 둔 돌들로부터 최근 돌 마크를 빼고 일반 돌로 바꿔줍니다.

            println()
            val coX: Int = turnDataList[turnNum - 4].coX
            val coY: Int = turnDataList[turnNum - 4].coY
            val boardFinalStone =
                "sBoard".plus(if (coX < 10) "0".plus(coX) else coX.toString())
                    .plus(if (coY < 10) "0".plus(coY) else coY.toString())
            //boardFinalStone에는 원래 돌로 바꿔야할 위치의 좌표값이 들어있습니다.
            val targetStone: Int =
                resources.getIdentifier(boardFinalStone, "id", packageName)

            val targetImageButton: ImageButton = findViewById(targetStone)
            when (turnNum % 4) {
                3, 2 -> {
                    checkStoneArray[coX][coY] = 0 // 체크 된 돌을 일반 돌로 바꾼다
                    if (stoneColorArray[coX][coY] == 1) {
                        changetoOriginalStone(targetImageButton, "black", coX, coY)
                    } else if (stoneColorArray[coX][coY] == 2) {
                        changetoOriginalStone(targetImageButton, "white", coX, coY)
                    }
                }
                0, 1 -> {
                    checkStoneArray[coX][coY] = 0 // 체크 된 돌을 일반 돌로 바꾼다
                    if (stoneColorArray[coX][coY] == 1) {
                        changetoOriginalStone(targetImageButton, "black", coX, coY)
                    } else if (stoneColorArray[coX][coY] == 2) {
                        changetoOriginalStone(targetImageButton, "white", coX, coY)
                    }
                }
            }
        }

        turnDataList.add(
            Turn(
                "ai",
                turnNum.toString(),
                minX,
                minY
            )
        ) // TurnDataList에 인공지능이 어디에 착수했는지 기록합니다.

        for (i in calcScoreArray.indices) {
            for (j in calcScoreArray[i].indices) {
                if (stoneColorArray[i][j] != 1 && stoneColorArray[i][j] != 2 && calcPlayerScoreArray[i][j] > maxVal) { // 만약 기존 최솟값보다 값이 작다면 최솟값 위치를 갱신해줍니다.
                    maxX = i
                    maxY = j
                    maxVal = calcPlayerScoreArray[i][j]
                }
            }
        }
    }


    //어떤 돌이 착수되었는지 확인하기 위해 로그를 띄워주는 함수입니다.
    private fun printBoard() {
        println("---Print Board---")
        for (i in calcScoreArray.indices) {
            print("$i ")
            for (j in calcScoreArray[i].indices) {
                print("${String.format("% 5d", calcScoreArray[i][j])} ")
            }
            println()
        }
    }

    //DFS를 이용해서 돌이 몇 개 연속으로 연결되어 있는지 확인한다.
    private fun countStone(
        x: Int, // 좌표
        y: Int, // 좌표
        dir: Int, // 뻗어나가는 방향
        color: Int // 돌 색깔
    ): Int {

        var cnt = 1
        val nextX = x + dx[dir]
        val nextY = y + dy[dir]

        // 끝 면에서 바둑판 바깥 방향으로 나가려고 하면 그냥 cnt를 반환해준다.
        if (x == 0 && y == 0 && (dir == 0 || dir == 1 || dir == 2 || dir == 6 || dir == 7)) return cnt // 왼쪽 위 코너
        else if (x == 18 && y == 0 && (dir == 0 || dir == 4 || dir == 5 || dir == 6 || dir == 7)) return cnt // 왼쪽 아래 코너
        else if (x == 18 && y == 18 && (dir == 2 || dir == 3 || dir == 4 || dir == 5 || dir == 6)) return cnt // 오른쪽 아래 코너
        else if (x == 0 && y == 18 && (dir == 0 || dir == 1 || dir == 2 || dir == 3 || dir == 4)) return cnt // 오른쪽 위 코너
        else if (x == 0 && y in 1..17 && (dir == 0 || dir == 1 || dir == 2)) return cnt // 윗면
        else if (x == 18 && y in 1..17 && (dir == 4 || dir == 5 || dir == 6)) return cnt // 아랫면
        else if (x in 1..17 && y == 0 && (dir == 0 || dir == 7 || dir == 6)) return cnt // 왼쪽 면
        else if (x in 1..17 && y == 18 && (dir == 2 || dir == 3 || dir == 4)) return cnt // 오른쪽 면

        //println("x : $x, y : $y, dir : $dir, nextX : $nextX, nextY : $nextY") // TODO 왼쪽 면 클릭했을 때 이상하게 나옴

        if (stoneColorArray[nextX][nextY] == color) {
            //println("@@@@ x : $x, y : $y, d : $dir")
            cnt += countStone(nextX, nextY, dir, color)
        }
        return cnt
    }

    private fun judgeVictory() {

        for (i in 0..18) {
            for (j in 0..18) {

                when (stoneColorArray[i][j]) {
                    1 -> {
                        for (k in 0 until 8) {
                            if (countStone(i, j, k, 1) >= 6) { //i,j는 돌 위치, k는 방향, 1은 색깔
                                Log.e("GAME OVER", "SIX CONNECTED AND BLACK WIN")

                                winnerStr = if (playerColor == "black") "player" else "AI"
                                println("### 1")
                                initBoards() // 게임 승리 판정이 나면 초기화
                                turnNum = 1
                                showDialog(winnerStr)
                                return
                            }
                        }
                    }
                    2 -> {
                        for (k in 0 until 8) {
                            if (countStone(i, j, k, 2) >= 6) { //i,j는 돌 위치, k는 방향, 1은 색깔
                                Log.e("GAME OVER", "SIX CONNECTED AND WHITE WIN")
                                winnerStr = if (playerColor == "white") "player" else "AI"
                                println("### 2")
                                //Toast.makeText(this, "2$winnerStr win!", Toast.LENGTH_SHORT).show()
                                initBoards()
                                turnNum = 1
                                showDialog(winnerStr)
                            }
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("InflateParams")
    private fun showDialog(winnerStr: String) {

        val builder = AlertDialog.Builder(this).create()
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


        println("### winnerStr : $winnerStr, myNickName : $myNickName")

        if (winnerStr == "player" && (!this.isFinishing)) { // 만약 승리했다면,
            when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
                Configuration.UI_MODE_NIGHT_NO -> { // Dark Mode 아닐 때
                    builder.setView(winDialogView)
                    builder.setButton(
                        AlertDialog.BUTTON_POSITIVE,
                        getString(R.string.Back)
                    ) { disBuilder, _ ->
                        disBuilder.dismiss()
                    }
                    builder.setButton(
                        AlertDialog.BUTTON_NEGATIVE,
                        getString(R.string.upper_exit)
                    ) { _, _ ->
                        finish()
                    }
                    builder.show()
                    builder.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.BLACK)
                    builder.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.BLACK)
                    builder.setCancelable(false)
                }
                Configuration.UI_MODE_NIGHT_YES -> { // Dark Mode 일 때

                    builder.setView(winDialogView)
                    builder.setButton(
                        AlertDialog.BUTTON_POSITIVE,
                        getString(R.string.Back)
                    ) { disBuilder, _ ->
                        disBuilder.dismiss()
                    }
                    builder.setButton(
                        AlertDialog.BUTTON_NEGATIVE,
                        getString(R.string.upper_exit)
                    ) { _, _ ->
                        finish()
                    }
                    builder.show()
                    builder.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.WHITE)
                    builder.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.WHITE)
                    builder.setCancelable(false)
                }
            }

        } else if (winnerStr != "player" && (!this.isFinishing)) { // 만약 패배했다면,
            when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
                Configuration.UI_MODE_NIGHT_NO -> { // Dark Mode 아닐 때
                    builder.setView(loseDialogView)
                    builder.setButton(
                        AlertDialog.BUTTON_POSITIVE,
                        getString(R.string.Back)
                    ) { disBuilder, _ ->
                        disBuilder.dismiss()
                    }
                    builder.setButton(
                        AlertDialog.BUTTON_NEGATIVE,
                        getString(R.string.upper_exit)
                    ) { _, _ ->
                        finish()
                    }
                    builder.show()
                    builder.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.BLACK)
                    builder.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.BLACK)
                    builder.setCancelable(false)
                }
                Configuration.UI_MODE_NIGHT_YES -> { // Dark Mode 일 때
                    builder.setView(loseDialogView)
                    builder.setButton(
                        AlertDialog.BUTTON_POSITIVE,
                        getString(R.string.Back)
                    ) { disBuilder, _ ->
                        disBuilder.dismiss()
                    }
                    builder.setButton(
                        AlertDialog.BUTTON_NEGATIVE,
                        getString(R.string.upper_exit)
                    ) { _, _ ->
                        finish()
                    }
                    builder.show()
                    builder.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.WHITE)
                    builder.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.WHITE)
                    builder.setCancelable(false)
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
                    imageButton.setImageResource(R.drawable.standard_confirm64)
                }
            }
        }
    }

    private fun setImageClickable() {
        println("##@@## IMAGECLICKABLE 들어옴 ##@@## ")
        Log.e("turnNUm ", turnNum.toString())
        if (playerColor == "black") { // 사용자가 먼저 착수 : 0,1일 때 사용자 차례
            when (turnNum % 4) {
                0, 1 -> { // 플레이어 차례
                    for (i in 0..18) {
                        for (j in 0..18) { // 모든 imageButton을 클릭 안되게 한다.
                            val boardStr = "sBoard".plus(if (i < 10) "0".plus(i) else i)
                                .plus(if (j < 10) "0".plus(j) else j)
                            val imageButton: ImageButton =
                                findViewById(resources.getIdentifier(boardStr, "id", packageName))
                            // 이미 착수가 된 곳이면 계속 착수가 불가능하도록 둡니다.
                            imageButton.isClickable = !stoneCheckArray[i][j]
                        }
                    }
                }
                2, 3 -> { // 컴퓨터 차례
                    for (i in 0..18) {
                        for (j in 0..18) { // 이미 돌이 착수 된 위치를 제외하고 clickbalse을 true로 줍니다.
                            val boardStr = "sBoard".plus(if (i < 10) "0".plus(i) else i)
                                .plus(if (j < 10) "0".plus(j) else j)
                            val imageButton: ImageButton =
                                findViewById(resources.getIdentifier(boardStr, "id", packageName))
                            imageButton.isClickable = false
                        }
                    }
                }
            }
        } else if (playerColor == "white") { // 컴퓨터가 먼저 착수 : 2,3일 때 사용자 차례
            when (turnNum % 4) {
                0, 1 -> { // 컴퓨터 차례
                    for (i in 0..18) {
                        for (j in 0..18) { // 모든 imageButton을 클릭 안되게 한다.
                            val boardStr = "sBoard".plus(if (i < 10) "0".plus(i) else i)
                                .plus(if (j < 10) "0".plus(j) else j)
                            val imageButton: ImageButton =
                                findViewById(resources.getIdentifier(boardStr, "id", packageName))
                            imageButton.isClickable = false
                        }
                    }
                }
                2, 3 -> { // 플레이어 차례
                    for (i in 0..18) {
                        for (j in 0..18) { // 이미 돌이 착수 된 위치를 제외하고 clickbalse을 true로 줍니다.
                            val boardStr = "sBoard".plus(if (i < 10) "0".plus(i) else i)
                                .plus(if (j < 10) "0".plus(j) else j)
                            val imageButton: ImageButton =
                                findViewById(resources.getIdentifier(boardStr, "id", packageName))
                            (!stoneCheckArray[i][j]).also {
                                imageButton.isClickable = it
                            } // 이미 착수가 된 곳이면 계속 착수가 불가능하도록 둡니다.
                        }
                    }
                }
            }
        }
    }

}