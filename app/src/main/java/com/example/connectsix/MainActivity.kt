package com.example.connectsix

import android.annotation.SuppressLint
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.Toast
import com.example.connectsix.sharedRef.SharedData
import com.google.firebase.database.*
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.twoplayer_gameboard.*
import kotlinx.coroutines.*
import java.lang.Runnable
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

//TODO 당장 해야 할 것
//TODO 누가 먼저 시작하는 것인지 알려주는 UI가 필요해보임
//TODO user_left toast message가 1. 상대방이 나갔을 때, 2. 내가 나갔을 때 두 번 뜨는 것에 대한 오류 해결
// => TwoPlayer~.kt에서 user_left ctrl+f로 검색해서 찾은 후 그 부분 조건문 고치면 됨. 1079번 째 줄 조건 고치기

//TODO 추후에 해야 할 것
//TODO 게임 끝나면 판정화면 만들고 바둑판 초기화시키기
//TODO 바둑판 초기화버튼 만들기
//TODO random game start button 클릭시 랜덤하게 비어있는 방 입장 가능하도록 구현

// DONE 이겨서 다시 메인 액티비티로 돌아온 후 방을 들어가면 winLoseRatio가 바뀌지 않는 에러 해결해야 함
// DONE 플레이어(본인)가 나갈 때 Dialog가 두 번 띄워지는 에러 해결(isUserExit변수를 통해서 해결)
// DONE 유저 나갔을 때 나갔다고 Toast message 띄워줄 것 + 승리 화면(dialog) 띄워주는 것
// DONE 상대방이 들어오면 전적 반영해서 nickname 옆에 보여주는 것
// DONE 빈 방에 들어갔을 때 바로 판정 처리 나는 것 -> TwoplayerGameboard.kt 1093,1099번 줄 확인하면 된다.
// DONE join game할 때 toast message 뜨는 것 조건 확인해서 없애기
// DONE 게임 판정 후 win/lose/ratio 업데이트
// DONE 소리 늦게 나는 것 해결
// DONE 바둑판에 돌 둘 때 소리 effect 넣기
// DONE Join Game을 통해 방 번호를 눌러 들어가는데, 이 때 없는 방 번호를 입력하면 방 번호가 없다고 Toast message 출력
// DONE 바둑판 나오는 곳에 몇 번 방인지 알 수 있도록 설정
// DONE Button크기가 작아 잘못 둘 수도 있으니 확인 마크 추가하기 -> 다이아몬드 버튼을 추가해서 잘못 두는 경우가 없도록 설정함
// DONE 닉네임 너무 길면 안들어가서 닉네임 제한 두기
// DONE turnNum으로 지금 몇 번 째 돌인지 알려줄 수 있도록 layout에 추가하고 turnNum설정하기
// DONE intent로 방 번호 넘겨주는 것
// DONE 버튼 두 번 클릭해야 바둑판에 착수할 수 있도록, 근데 한 번 클릭했을 때 확대나 축소한 경우에도 빨간 다이아몬드가 안 없어지도록 설정
// DONE 지금 있는 오류는 한 번 클릭하고 다른 위치에 클릭하면 다이아몬드가 없어짐, 그냥 클릭만 카운트하기 때문
// Done 게임 종료 후 판정 시 이름(Nickname) 오류 수정
// DONE 다른 사람 차례일 때 바둑판(ImageButton) clickable 비활성화하는 코드 - 201230에 할 것
// DONE 닉네임 중복체크 기능
// DONE 둘 다 방 나가면 서버에서도 삭제하는 코드

class MainActivity : AppCompatActivity() {

    var winLoseRatio = ""
    var roomNum = ""

    @SuppressLint("ShowToast", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val intent = Intent(this, TwoplayerGameboard::class.java)

        var newNickname: String = ""
        var pastNickName = ""

        if (SharedData.prefs.getName("nickName", "")
                .isEmpty()
        ) { // 만약 첫 로그인이라면(sharedPreference에 값이 없다면)
            enterButton.setOnClickListener {
                newNickname = setNickName.text.toString()
                if (newNickname.length !in 3..12) { // 아이디가 조건에 맞지 않는다면 다시 입력하라고 나옵니다.
                    Toast.makeText(
                        this,
                        getString(R.string.id_at_least_three_characters),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    //var updateUserName = mutableMapOf<String, Any>()
                    //updateUserName["userName"] = newNickname // Firebase에 update할 값 준비
                    //FirebaseDatabase.getInstance().reference.updateChildren(updateUserName)//Firebase에 설정한 이름 탑재
                    Toast.makeText(this, getString(R.string.login_success), Toast.LENGTH_SHORT).show()

                    FirebaseDatabase.getInstance().reference.child("Users").push()
                        .setValue(User(newNickname, "0", "0", "0%"))
//                    FirebaseDatabase.getInstance().reference.child("Users").addValueEventListener(object:ValueEventListener{
//                        //새로 등록한 유저의 key값을 sharedPreference로 저장합니다.
//                        override fun onDataChange(snapshot: DataSnapshot) {
//                            for(i in snapshot.children){
//                                if(i.child("nickName").value.toString() == newNickname){
//                                    SharedData.prefs.setNicknameKey("nicknameKey", i.key.toString())
//                                }
//                            }
//                        }
//
//                        override fun onCancelled(error: DatabaseError) {
//                        }
//
//                    })

                    setNickName.visibility = GONE
                    enterButton.visibility = GONE

                    SharedData.prefs.setName("nickName", newNickname)
                    //TODO SharedData.prefs.setNicknameKey()

                    welcomeSign.text = "Welcome, $newNickname :)"
                    welcomeSign.visibility = VISIBLE
                    changeNickname.visibility = VISIBLE
                }
            }
        } else { // 만약 첫 로그인이 아니고 이전에 로그인한 기록이 있으면,
            newNickname = SharedData.prefs.getName("nickName", "")
            updateUserRatio(newNickname) // 사용자 닉네임을 기반으로 디비에서 승률 검사 후 함수에서 winLoseRatio 변수에 저장합니다.

            setNickName.visibility = GONE
            enterButton.visibility = GONE

            //SharedData.prefs.setName("nickName", newNickname) 이 부분은 없어도 될 것 같음. 왜 새로 닉네임을 설정해야 하는지?

            if(Locale.getDefault().language.toString() == "ko"){ // 기본 언어가 한국어인 device에 대한 처리
                welcomeSign.text = "${newNickname}님, ${getString(R.string.welcome_back)}:)\n${getString(R.string.have_a_great_day)}"
            }else{
                welcomeSign.text = "${getString(R.string.welcome_back)}$newNickname :)\n${getString(R.string.have_a_great_day)}"
            }

            //welcomeSign.text = "${getString(R.string.welcome_back)}$newNickname :)\n${getString(R.string.have_a_great_day)}"
            welcomeSign.visibility = VISIBLE
            changeNickname.visibility = VISIBLE

        }


        var changeNickNameButtonClickTime: Long = 0
        changeNickname.setOnClickListener {
            if (System.currentTimeMillis() > changeNickNameButtonClickTime + 2500) {
                changeNickNameButtonClickTime = System.currentTimeMillis()
                Toast.makeText(
                    this,
                    getString(R.string.change_name_lose_data),
                    Toast.LENGTH_LONG
                ).show()

            } else if (System.currentTimeMillis() <= changeNickNameButtonClickTime + 2500) {
                /*DB에서 유저의 정보를 삭제하는 부분*/
                pastNickName = newNickname
                FirebaseDatabase.getInstance().reference.child("Users")
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            for (index in snapshot.children) {
                                val nickname: String = index.child("nickName").value.toString()
                                println("@@@ nickname : $nickname")
                                if (nickname == pastNickName) {
                                    var deleteUserKey: String = index.key.toString()
                                    println("@@@ ${index.key}")
                                    FirebaseDatabase.getInstance().reference.child("Users")
                                        .child(deleteUserKey).removeValue()
                                }
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Log.e("Firebase DB error", "$error")
                        }
                    })

//                FirebaseDatabase.getInstance().reference.child("Users").addListenerForSingleValueEvent(object:ValueEventListener{
//                    //새로 바꾼 유저의 key를 sharedPreferences로 저장합니다.
//                    override fun onDataChange(snapshot: DataSnapshot) {
//                        for(i in snapshot.children){
//                            if(i.child("nickName").value.toString() == newNickname){
//                                SharedData.prefs.setNicknameKey("nicknameKey", i.key.toString())
//                            }
//                        }
//                    }
//
//                    override fun onCancelled(error: DatabaseError) {
//                    }
//                })

                setNickName.visibility = VISIBLE
                enterButton.visibility = VISIBLE //다시 이름 설정을 할 수 있는 창을 보이게 한다
                welcomeSign.visibility = GONE // welcome sign을 다시 들어가게 한다.
                changeNickname.visibility = GONE

                enterButton.setOnClickListener {
                    newNickname = setNickName.text.toString()

                    if (newNickname.length in 3..12) {

                        val user: User = User(newNickname, "0", "0", "0%")
                        var isIdTaken = false

                        FirebaseDatabase.getInstance().reference.child("Users")
                            .addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(snapshot: DataSnapshot) {
                                    for (i in snapshot.children) {
                                        if (i.child("NickName").getValue()
                                                .toString() == newNickname
                                        ) { // 이미 이 Nickname이 존재한다면 isIdTaken 변수를 true로 설정합니다.
                                            isIdTaken = true
                                            break
                                        } else {
                                            isIdTaken = false // 만약 계속 없을 시 false로 변경시켜줍니다.
                                            // 이 작업을 하지 않으면 처음에 틀렸는데 이후에도 계속 true로 남아 있을 수 있기 때문에 필요합니다.
                                        }
                                    }
                                }

                                override fun onCancelled(error: DatabaseError) {}

                            })
                        if (!isIdTaken) { //새로운 nickname이라면 파이어베이스에 Nickname을 추가합니다.
                            FirebaseDatabase.getInstance().reference.child("Users").push()
                                .setValue(user)

                            setNickName.visibility = GONE
                            enterButton.visibility = GONE

                            SharedData.prefs.setName("nickName", newNickname)
                            // 새로운 이름 추가시 sharedData에 이름과 초기화된 전적을 추가합니다.


                            welcomeSign.text = "Welcome, $newNickname :)\nNew Life isn't it great?"
                            welcomeSign.visibility = VISIBLE
                            changeNickname.visibility = VISIBLE
                        } else { // 이미 사용되고 있는 닉네임임을 알려줍니다.
                            Toast.makeText(
                                baseContext,
                                "$newNickname ${getString(R.string.nickname_already_taken)}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                    } else {
                        Toast.makeText(
                            baseContext,
                            getString(R.string.nickname_between_three_twelve),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

        //TODO 이 부분은 추후에 구현할 것
        randomGameStartButton.setOnClickListener {
            Toast.makeText(this, "Coming soon...", Toast.LENGTH_SHORT).show()
            //intent.putExtra("player1NickName", newNickname)
            //startActivity(intent)
        }

        //Create Game 버튼 눌렀을 때 방 생성 및 입장
        createGameButton.setOnClickListener {
            val database = FirebaseDatabase.getInstance().reference.child("roomId")
            roomNum = roomNumberEdittext.text.toString()

            GlobalScope.launch {
                if(roomNum.isEmpty()){
                    runOnUiThread {
                        Toast.makeText(
                            applicationContext,
                            getString(R.string.please_enter_room_num),
                            Toast.LENGTH_SHORT
                        )
                            .show()
                    }
                }
                else if (isRoomAvailable(roomNum)) { // 만약 방이 없으면, 즉 isRoomAvailable이 true이므로 방 생성 가능하다.
                    if (roomNum.toInt() <= "9999".toInt() && roomNum.toInt() >= "1000".toInt()) { // roomNum은 1000부터 9999 사이여야 한다.
                        database.push().setValue(Room(roomNum, newNickname, "", "1"))
                        database.addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(snapshot: DataSnapshot) {
                                for (index in snapshot.children) {
                                    if (index.child("roomId").value.toString() == roomNum) {
                                        //println("@@@@ ${index.key}")
                                        intent.putExtra("roomKey", index.key)
                                            .toString()//roomNum을 intent에 추가해서 게임 화면으로 넘깁니다.
                                        intent.putExtra("player1NickName", newNickname)
                                        intent.putExtra("roomNum", roomNum)
                                        intent.putExtra("winLoseRatio", winLoseRatio)
                                        //println("@@@@ $winLoseRatio")
                                        startActivity(intent)
                                    }
                                }
                            }

                            override fun onCancelled(error: DatabaseError) {
                                Log.e("Firebase DB error", "$error")
                            }
                        })
                    } else {
                        runOnUiThread {
                            Toast.makeText(
                                applicationContext,
                                getString(R.string.check_room_num_again),
                                Toast.LENGTH_SHORT
                            )
                                .show()
                        }
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(
                            applicationContext,
                            getString(R.string.room_num_already_taken),
                            Toast.LENGTH_LONG
                        )
                            .show()
                    }

                }
            }

//            if (roomNum.toInt() <= "9999".toInt() && roomNum.toInt() >= "0000".toInt()) { // roomNum은 0000부터 9999 사이여야 한다.
//                database.push().setValue(Room(roomNum, newNickname, "", "1"))
//                database.addListenerForSingleValueEvent(object : ValueEventListener {
//                    override fun onDataChange(snapshot: DataSnapshot) {
//                        for (index in snapshot.children) {
//                            if (index.child("roomId").value.toString() == roomNum) {
//                                //println("@@@@ ${index.key}")
//                                intent.putExtra("roomKey", index.key)
//                                    .toString()//roomNum을 intent에 추가해서 게임 화면으로 넘깁니다.
//                                intent.putExtra("player1NickName", newNickname)
//                                intent.putExtra("roomNum", roomNum)
//                                intent.putExtra("winLoseRatio", winLoseRatio)
//                                //println("@@@@ $winLoseRatio")
//                                startActivity(intent)
//                            }
//                        }
//                    }
//
//                    override fun onCancelled(error: DatabaseError) {
//                        Log.e("Firebase DB error", "$error")
//                    }
//                })
//            } else {
//                Toast.makeText(this, "Please check the room number again.", Toast.LENGTH_SHORT)
//                    .show()
//            }


        }

        //방을 찾아서 들어갑니다.
        //방으로 넘어갈 때 player1Nickname과 roomKey를 넘깁니다.
        joinGameButton.setOnClickListener {
            roomNum = roomNumberEdittext.text.toString()
            var isExistRoom = false



            if (roomNum <= "9999" && roomNum >= "0000") { // roomNum은 0000부터 9999 사이여야 한다.
                FirebaseDatabase.getInstance().reference.child("roomId")
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            for (index in snapshot.children) {
                                val serverRoomNum = index.child("roomId").value.toString()
                                if (serverRoomNum == roomNum) { // 같은 roomNum을 찾으면
                                    isExistRoom = true
                                    intent.putExtra(
                                        "player1NickName",
                                        newNickname
                                    ) // player1Nickname과
                                    intent.putExtra(
                                        "roomKey",
                                        index.key
                                    )//roomNum을 intent에 추가해서 게임 화면으로 넘깁니다.
                                    intent.putExtra("roomNum", roomNum)
                                    intent.putExtra("winLoseRatio", winLoseRatio)
                                    startActivity(intent)
                                }

                            }
                            if (!isExistRoom) {
                                println("gotinhere")
                                Toast.makeText(
                                    applicationContext,
                                    getString(R.string.check_room_num_again),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Log.e("Firebase DB error", "$error")
                        }
                    })
            } else {
                Toast.makeText(this, getString(R.string.check_room_num_again), Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    //이 함수는 1초를 잘 기다리고 값을 넘겨줌
    private suspend fun temp0126(roomNum: String): Boolean {
        var deffered = CoroutineScope(Dispatchers.IO).async {
            delay(1000L)
            println("world")
            false
        }
        println("### ${deffered.await()}")
        return deffered.await()
    }

    private suspend fun temp10(roomNum: String): Boolean {
        var deffered = CoroutineScope(Dispatchers.IO).async {
            val database = FirebaseDatabase.getInstance().reference.child("roomId")
            var rValue = true
            database.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (i in snapshot.children) {
                        println("$roomNum <= roomNum, ${i.child("roomId").toString()} <= DB data")
                        if (roomNum == i.child("roomId").value.toString()) {
                            rValue = false
                        }
                    }
                    Log.e("e", "1")
                }

                override fun onCancelled(error: DatabaseError) {}
            })
            Log.e("e", "2")
            rValue
        }
        return deffered.await()
    }

    private suspend fun temp13(roomNum: String): Boolean {
        var deffered = CoroutineScope(Dispatchers.IO).async {
            val database = FirebaseDatabase.getInstance().reference.child("roomId")
            var rValue = true
            database.orderByChild("roomId").equalTo(roomNum)
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        rValue = false
//                    for(i in snapshot.children){
//                        println("$roomNum <= roomNum, ${i.child("roomId").toString() } <= DB data")
////                        if (roomNum == i.child("roomId").value.toString()) {
////                            rValue = false
////                        }
//                        rValue = false
//                    }
                        Log.e("e", "1")
                        println("@@@ for문 끝난 후 rValue = $rValue")
                    }

                    override fun onCancelled(error: DatabaseError) {}
                })
            Log.e("e", "2")
            rValue
        }
        return deffered.await()
    }

    private suspend fun isRoomAvailable(roomNum: String) =
        suspendCoroutine<Boolean> {
            Handler(Looper.getMainLooper()).postDelayed({
                val database = FirebaseDatabase.getInstance().reference.child("roomId")
                var rValue = true
                database.orderByChild("roomId").equalTo(roomNum)
                    .addListenerForSingleValueEvent(object :
                        ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            println("@@@ 찾은 값 : ${snapshot.value.toString()}, 원래 값 : $roomNum")
                            if (snapshot.value != null) {
                                rValue = false
                            }
                            println("@@@ for문 끝난 후 rValue = $rValue")
                            it.resume(rValue)
                        }

                        override fun onCancelled(error: DatabaseError) {}
                    })
            }, 500)
        }

    //아래는 잘 true, false를 바꿔주면 잘 알아서 바꿔짐
    private suspend fun temp11(roomNum: String) = suspendCoroutine<Boolean> {
        Handler(Looper.getMainLooper()).postDelayed(object : Runnable {
            override fun run() {
                it.resume(false)
            }
        }, 1000)
    }

    suspend fun checkIsRoom(roomNum: String): Boolean {
        //return@withContext rValue
        return CoroutineScope(Dispatchers.IO).async {
            Log.e("1", "1")
            val database = FirebaseDatabase.getInstance().reference.child("roomId")
            var rValue = true
            database.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (i in snapshot.children) {
                        //println("$roomNum <= roomNum, ${i.child("roomId").toString() } <= DB data")
                        if (roomNum == i.child("roomId").value.toString()) {
                            rValue = false
                        }
                    }
                    Log.e("2", "2")
                    //println("for문끝나고 출력하는 rValue = $rValue")
                }

                override fun onCancelled(error: DatabaseError) {}
            })
            Log.e("3", "3")
            rValue
        }.await()
    }

    private suspend fun checkIsRoom7(tempRoomNum: String): Boolean {
        return suspendCoroutine { continuation ->
            FirebaseDatabase.getInstance().reference.child("roomId")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        Log.e("2", "2")
                        for (i in snapshot.children) {
                            //println("$tempRoomNum <= roomNum, ${i.child("roomId").toString() } <= DB data")
                            if (tempRoomNum == i.child("roomId").value.toString()) {
                                continuation.resume(false)
                                break
                            }
                        }
                        Log.e("3", "3")
                        continuation.resume(true)
                        //println("for문끝나고 출력하는 rValue = $rValue")
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e("error", error.toString())
                        continuation.resumeWithException(error.toException())
                    }
                })
        }
    }

    private suspend fun checkIsRoom6(tempRoomNum: String) =
        suspendCoroutine<Boolean> { continuation ->
            Log.e("1", "1")
            val roomDatabase = FirebaseDatabase.getInstance().reference.child("roomId")
            var rValue = true
            FirebaseDatabase.getInstance().reference.child("roomId")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        Log.e("2", "2")
                        for (i in snapshot.children) {
                            //println("$tempRoomNum <= roomNum, ${i.child("roomId").toString() } <= DB data")
                            if (tempRoomNum == i.child("roomId").value.toString()) {
                                rValue = false
                                continuation.resume(rValue)
                                break
                            }
                        }
                        Log.e("3", "3")
                        continuation.resume(rValue)
                        //println("for문끝나고 출력하는 rValue = $rValue")
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e("error", error.toString())
                        continuation.resumeWithException(error.toException())
                    }
                })
            Log.e("4", "4")
            //continuation.resume(rValue)
        }

    private suspend fun checkIsRoom4() = suspendCoroutine<Boolean> {
        Handler(Looper.getMainLooper()).postDelayed(object : Runnable {
            override fun run() {
                Log.e("1", "1")
                val database = FirebaseDatabase.getInstance().reference.child("roomId")
                var rValue = true
                database.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        for (i in snapshot.children) {
                            println(
                                "$roomNum <= roomNum, ${
                                    i.child("roomId").toString()
                                } <= DB data"
                            )
                            if (roomNum == i.child("roomId").value.toString()) {
                                rValue = false
                            }
                        }
                        Log.e("2", "2")
                        //println("for문끝나고 출력하는 rValue = $rValue")
                    }

                    override fun onCancelled(error: DatabaseError) {}
                })
                Log.e("3", "3")
                it.resume(rValue)
            }
        }, 2000)
    }

    suspend fun temp3(database: DatabaseReference, roomNum: String): Boolean =
        withContext(Dispatchers.Default) {
            var bool = true
            database.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    Log.e("3", "3")

                    for (i in snapshot.children) {
                        println("$roomNum <= roomNum, ${i.child("roomId").toString()} <= DB data")
                        if (roomNum == i.child("roomId").value.toString()) {
                            bool = false
                        }
                    }
                    println("for문끝나고 출력하는 rValue = $bool")
                }

                override fun onCancelled(error: DatabaseError) {}
            })
            return@withContext bool
        }


    suspend fun checkIsRoom2(roomNum: String): Boolean {
        val database = FirebaseDatabase.getInstance().reference.child("roomId")
        var rValue = true
        Log.e("1", "1")

        CoroutineScope(Dispatchers.Main).launch {
            Log.e("2", "2")

            val check = CoroutineScope(Dispatchers.IO).async {
                database.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        println("@@@@ 3")
                        Log.e("3", "3")

                        for (i in snapshot.children) {
                            println(
                                "$roomNum <= roomNum, ${
                                    i.child("roomId").toString()
                                } <= DB data"
                            )
                            if (roomNum == i.child("roomId").value.toString()) {
                                rValue = false
                            }
                        }
                        println("for문끝나고 출력하는 rValue = $rValue")
                    }

                    override fun onCancelled(error: DatabaseError) {
                        TODO("Not yet implemented")
                    }
                })

            }.await()

            withContext(Dispatchers.Main) {
                //rValue = check
            }
        }

        Log.e("5", "5")
        return rValue
    }

    fun checkIsRoom3(roomNum: String): Boolean {
        val database = FirebaseDatabase.getInstance().reference.child("roomId")
        var rValue = true
        Log.e("1", "1")

        CoroutineScope(Dispatchers.Main).launch {
            Log.e("2", "2")
            val check = CoroutineScope(Dispatchers.IO).async {
                //rValue = temp(database, roomNum)
                rValue
            }.await()

            withContext(Dispatchers.Main) {
                rValue = check
            }
        }

        Log.e("5", "5")
        return rValue
    }

    //방이 있는지 확인하는 코드입니다. false를 반환하면 이미 room Number가 있어서 해당 방 번호로 방을 생성하지 못하는 경우입니다.
    //true를 return하면 방 번호가 아직 생성되지 않아 방을 생성할 수 있다는 것입니다.
    fun checkIsRoom5(roomNum: String): Boolean {
        val database = FirebaseDatabase.getInstance().reference.child("roomId")
        var rValue = true
        println("@@@@ 1")
        runBlocking {
            val job: Job = launch {
                //rValue = temp(database, roomNum)
            }
            job.join()
            println("@@@@ Rvalue is ${rValue}")
        }
        println("@@@@@ 6")
        return rValue
    }

    fun temp(database: DatabaseReference, roomNum: String): Boolean {
        var rValue = true
        database.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (i in snapshot.children) {
                    if (roomNum == i.child("roomId").value.toString()) {
                        rValue = false
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
        return rValue
    }

    suspend fun temp2(database: DatabaseReference, roomNum: String): Boolean {
        var rValue = true
        println("@@@@@ 2")

        coroutineScope {
            println("@@@@@ 3")
            val lnch = launch {

                database.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        println("@@@@ 4")
                        for (i in snapshot.children) {
                            println(
                                "$roomNum <= roomNum, ${
                                    i.child("roomId").toString()
                                } <= DB data"
                            )
                            if (roomNum == i.child("roomId").value.toString()) {
                                rValue = false
                            }
                        }
                        println("for문끝나고 출력하는 rValue = $rValue")
                    }

                    override fun onCancelled(error: DatabaseError) {}

                })
            }
            lnch.join()//완료까지 대기
        }
        //delay(500)
        println("@@@@ 5")
        return rValue
    }


    fun updateUserRatio(userNickName: String) {
        val database = FirebaseDatabase.getInstance().reference.child("Users")
        var ratio = ""


        println("@@@@@@@ $ratio")//확인하는부분입니다

        database.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (i in snapshot.children) {
                    if (i.child("nickName").value.toString() == userNickName) {
                        ratio = i.child("win").value.toString().substringBefore(".").plus("/")
                            .plus(i.child("lose").value.toString()).substringBefore(".").plus("/")
                            .plus(i.child("ratio").value.toString())
                    }
                }
                winLoseRatio = ratio
            }

            override fun onCancelled(error: DatabaseError) {}

        })

        database.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                for (i in snapshot.children) {
                    if (i.child("nickName").value.toString() == userNickName) {
                        ratio = i.child("win").value.toString().substringBefore(".").plus("/")
                            .plus(i.child("lose").value.toString()).substringBefore(".").plus("/")
                            .plus(i.child("ratio").value.toString())
                    }
                }
                winLoseRatio = ratio
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
    }
}