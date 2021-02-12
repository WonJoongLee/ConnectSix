package com.connectsix.connectsix

import android.app.Dialog
import android.content.res.Configuration
import android.os.Bundle
import android.view.Window
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.connectsix.connectsix.sharedRef.SharedData
import kotlinx.android.synthetic.main.singleplayer_gameboard.*
import kotlinx.android.synthetic.main.twoplayer_gameboard.zoomInButton
import kotlinx.android.synthetic.main.twoplayer_gameboard.zoomOutButton

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
    private val myNickName: String =
        SharedData.prefs.getName("nickName", "") // Shared Pref.에서 내 닉네임을 가져온다.
    private var turnNum = 1 // 지금이 몇 번째 Turn인지 알기 위한 변수입니다.
    var stoneClickCnt = 0 // 빨간 다이아모든 위에 두 번 째 클릭인지 확인하는 변수입니다.
    var confirmX = -1 // user가 두기로 했던 빨간 diamond와 같은 위치에 돌을 두는지 확인하는 변수들입니다.
    var confirmY = -1 // 결국 좌표를 확인하기 위한 변수입니다.
    var clickStoneChanged: Boolean = false
    var turnDataList = mutableListOf<Turn>() // 지금까지 착수된 돌들을 list형태로 저장하기 위한 변수입니다.

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.singleplayer_gameboard)

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
        setImageClickable()

        /*zoom out button이 눌려지면 각 button의 크기를 줄임
        * default 값은 64*/
        zoomOutButton.setOnClickListener { //축소
            println("@@@ $zoomLevel!!")
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
            println("@@@확대clicked!!")
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

                imageButton.setOnClickListener { // 사용자가 보드 클릭 시
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
                            turnNum++ // 사용자가 클릭해서 돌을 착수했으므로 turnNum을 증가시킨다.
                            setImageClickable()
                            if (turnNum >= 4) { // 세번째 전에 둔 돌들로부터 최근 돌 마크를 빼고 일반 돌로 바꿔줍니다.
                                println("@@@ $playerColor")
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
                                        changetoOriginalStone(targetImageButton, "black", coX, coY)
                                    }
                                    0, 1 -> {
                                        checkStoneArray[coX][coY] = 0 // 체크 된 돌을 일반 돌로 바꾼다
                                        changetoOriginalStone(targetImageButton, "white", coX, coY)
                                    }
                                }
                            }

                            println("@@@ $turnNum")

                            clickStoneChanged = false
                            when (turnNum % 4) {
                                1, 0 -> { // turnNum(시도 횟수)가 4로 나눴을 때 1 또는 0이면 player 1 차례다.
                                    if(playerColor == "black"){
                                        setStoneDependOnSize(imageButton, "black", i, j)
                                    }else{
                                        setStoneDependOnSize(imageButton, "white", i, j)
                                    }
                                    checkStoneArray[i][j] = 1 // 지금 체크된 돌임을 명시

                                }
                                2, 3 -> { // 2,3이면 player 2 차례다
                                    if(playerColor == "black"){
                                        setStoneDependOnSize(imageButton, "black", i, j)
                                    }else{
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
        //println("i : $i, j : $j")
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
            println(playerColor)
            d.dismiss()
        }
        blackButton.setOnClickListener {
            playerColor = "black"
            println(playerColor)
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
        if (playerColor == "black") { // 사용자가 먼저 착수 : 0,1일 때 사용자 차례
            println("@@@ GOTIN")
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
//                            if (stoneCheckArray[i][j]) { // 이 부분이 위 한 줄과 같음
//                                imageButton.isClickable = false
//                            } else {
//                                imageButton.isClickable = true
//                            }
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
//                            if(stoneCheckArray[i][j]) { // 이 부분이 위 한 줄과 같음
//                                imageButton.isClickable = false
//                            }else{
//                                imageButton.isClickable = true
//                            }
                        }
                    }
                }
            }
        }
    }

}