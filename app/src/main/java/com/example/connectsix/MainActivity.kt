package com.example.connectsix

import android.annotation.SuppressLint
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.Toast
import com.example.connectsix.sharedRef.SharedData
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.android.synthetic.main.activity_main.*



//TODO 게임 끝나면 판정화면 만들고 바둑판 초기화시키기
//TODO turnNum으로 지금 몇 번 째 돌인지 알려줄 수 있도록 layout에 추가하고 turnNum설정하기
//TODO 닉네임 너무 길면 안들어가서 닉네임 제한 두기
//TODO Button크기가 작아 잘못 둘 수도 있으니 확인 마크 추가하기
//TODO 바둑판 나오는 곳에 몇 번 방인지 알 수 있도록 설정
//TODO 바둑판에 돌 둘 때 소리 effect 넣기

// DONE 다른 사람 차례일 때 바둑판(ImageButton) clickable 비활성화하는 코드 - 201230에 할 것
// DONE 닉네임 중복체크 기능
// DONE 둘 다 방 나가면 서버에서도 삭제하는 코드

class MainActivity : AppCompatActivity() {

    @SuppressLint("ShowToast", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val intent = Intent(this, TwoplayerGameboard::class.java)

        var newNickname : String = ""
        var pastNickName = ""
        var roomNum = ""

        if(SharedData.prefs.getName("nickName", "").isEmpty()){ // 만약 첫 로그인이라면(sharedPreference에 값이 없다면)
            enterButton.setOnClickListener {
                newNickname = setNickName.text.toString()
                if(newNickname.length < 3){ // 아이디가 두 글자 이하이면 다시 입력하라고 나옵니다.
                    Toast.makeText(this, "Please enter at least three characters!", Toast.LENGTH_SHORT).show()
                }else{
                    //var updateUserName = mutableMapOf<String, Any>()
                    //updateUserName["userName"] = newNickname // Firebase에 update할 값 준비
                    //FirebaseDatabase.getInstance().reference.updateChildren(updateUserName)//Firebase에 설정한 이름 탑재
                    Toast.makeText(this, "Login Success", Toast.LENGTH_SHORT).show()

                    FirebaseDatabase.getInstance().reference.child("Users").push().setValue(User(newNickname, "0", "0", "0%"))

                    setNickName.visibility = GONE
                    enterButton.visibility = GONE

                    SharedData.prefs.setName("nickName", newNickname)

                    welcomeSign.text = "Welcome, $newNickname :)"
                    welcomeSign.visibility = VISIBLE
                    changeNickname.visibility = VISIBLE
                }
            }
        }else{ // 만약 첫 로그인이 아니고 이전에 로그인한 기록이 있으면,
            newNickname = SharedData.prefs.getName("nickName", "")
            setNickName.visibility = GONE
            enterButton.visibility = GONE

            SharedData.prefs.setName("nickName", newNickname)

            welcomeSign.text = "Welcome back, $newNickname :)\nHave a great day."
            welcomeSign.visibility = VISIBLE
            changeNickname.visibility = VISIBLE

        }

        var changeNickNameButtonClickTime : Long = 0
        changeNickname.setOnClickListener {
            if(System.currentTimeMillis() > changeNickNameButtonClickTime + 2500){
                changeNickNameButtonClickTime = System.currentTimeMillis()
                Toast.makeText(this, "If you change name, you will lose data\nIf you still want to change, please click the button one more time", Toast.LENGTH_LONG).show()

            }
            else if(System.currentTimeMillis() <= changeNickNameButtonClickTime + 2500) {
                Toast.makeText(this, "New Life, New Start", Toast.LENGTH_SHORT).show()

                /*DB에서 유저의 정보를 삭제하는 부분*/
                pastNickName = newNickname
                FirebaseDatabase.getInstance().reference.child("Users").addListenerForSingleValueEvent(object:ValueEventListener{
                    override fun onDataChange(snapshot: DataSnapshot) {
                        for(index in snapshot.children){
                            val nickname : String = index.child("nickName").value.toString()
                            println("@@@ nickname : $nickname")
                            if(nickname == pastNickName){
                                var deleteUserKey : String = index.key.toString()
                                println("@@@ ${index.key}")
                                FirebaseDatabase.getInstance().reference.child("Users").child(deleteUserKey).removeValue()
                            }
                        }
                    }
                    override fun onCancelled(error: DatabaseError) { Log.e("Firebase DB error", "$error") }
                })

                setNickName.visibility = VISIBLE
                enterButton.visibility = VISIBLE //다시 이름 설정을 할 수 있는 창을 보이게 한다
                welcomeSign.visibility = GONE // welcome sign을 다시 들어가게 한다.
                changeNickname.visibility = GONE

                enterButton.setOnClickListener{
                    newNickname = setNickName.text.toString()

                    val user : User = User(newNickname, "0", "0", "0%")
                    var isIdTaken = false

                    FirebaseDatabase.getInstance().reference.child("Users").addListenerForSingleValueEvent(object: ValueEventListener{
                        override fun onDataChange(snapshot: DataSnapshot) {
                            for(i in snapshot.children){
                                if(i.child("NickName").getValue().toString() == newNickname){ // 이미 이 Nickname이 존재한다면 isIdTaken 변수를 true로 설정합니다.
                                    isIdTaken = true
                                    break
                                }else{
                                    isIdTaken = false // 만약 계속 없을 시 false로 변경시켜줍니다.
                                                      // 이 작업을 하지 않으면 처음에 틀렸는데 이후에도 계속 true로 남아 있을 수 있기 때문에 필요합니다.
                                }
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            TODO("Not yet implemented")
                        }

                    })
                    if(!isIdTaken){ //새로운 nickname이라면 파이어베이스에 Nickname을 추가합니다.
                        FirebaseDatabase.getInstance().reference.child("Users").push().setValue(user)

                        setNickName.visibility = GONE
                        enterButton.visibility = GONE

                        SharedData.prefs.setName("nickName", newNickname)

                        welcomeSign.text = "Welcome, $newNickname :)\nNew Life isn't it great?"
                        welcomeSign.visibility = VISIBLE
                        changeNickname.visibility = VISIBLE
                    }else{ // 이미 사용되고 있는 닉네임임을 알려줍니다.
                        Toast.makeText(baseContext, "$newNickname is already taken. Please try it again.", Toast.LENGTH_SHORT).show()
                    }

                }
            }
        }

        randomGameStartButton.setOnClickListener {
            intent.putExtra("player1NickName", newNickname)
            startActivity(intent)
        }

        //Create Game 버튼 눌렀을 때 방 생성 및 입장
        createGameButton.setOnClickListener {
            roomNum = roomNumberEdittext.text.toString()

            if(roomNum<="9999" && roomNum >= "0000") { // roomNum은 0000부터 9999 사이여야 한다.
                FirebaseDatabase.getInstance().reference.child("roomId").push()
                    .setValue(Room(roomNum, newNickname, "", "1"))
                FirebaseDatabase.getInstance().reference.child("roomId")
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            for (index in snapshot.children) {
                                if (index.child("roomId").value.toString() == roomNum) {
                                    println("@@@@ ${index.key}")
                                    intent.putExtra("roomKey", index.key)
                                        .toString()//roomNum을 intent에 추가해서 게임 화면으로 넘깁니다.
                                    intent.putExtra("player1NickName", newNickname)
                                    startActivity(intent)
                                }
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            TODO("Not yet implemented")
                        }
                    })
            }else{
                Toast.makeText(this, "Please check the room number again.", Toast.LENGTH_SHORT).show()
            }

        }

        //방을 찾아서 들어갑니다.
        //방으로 넘어갈 때 player1Nickname과 roomKey를 넘깁니다.
        joinGameButton.setOnClickListener {
            roomNum = roomNumberEdittext.text.toString()
            if(roomNum<="9999" && roomNum >= "0000") { // roomNum은 0000부터 9999 사이여야 한다.
                println("@@@ 조건 성립")
                FirebaseDatabase.getInstance().reference.child("roomId").addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            for (index in snapshot.children) {
                                val serverRoomNum = index.child("roomId").value.toString()
                                if (serverRoomNum == roomNum) { // 같은 roomNum을 찾으면
                                    print("@@@ ${index.key}")
                                    println("@@@@ $roomNum found!")
                                    intent.putExtra("player1NickName", newNickname) // player1Nickname과
                                    intent.putExtra("roomKey", index.key)//roomNum을 intent에 추가해서 게임 화면으로 넘깁니다.
                                    startActivity(intent)
                                    //var deleteUserKey : String = index.key.toString()
                                    //println("@@@ ${index.key}")
                                    //FirebaseDatabase.getInstance().reference.child("Users").child(deleteUserKey).removeValue()
                                }
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Log.e("Firebase DB error", "$error")
                        }
                    })
            }else{
                Toast.makeText(this, "Please check the room number again.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}