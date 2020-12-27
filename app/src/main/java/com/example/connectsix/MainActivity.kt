package com.example.connectsix

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.Toast
import com.example.connectsix.sharedRef.SharedData
import com.google.firebase.database.FirebaseDatabase
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    @SuppressLint("ShowToast", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val intent = Intent(this, TwoplayerGameboard::class.java)

        var newNickname : String = ""



        if(SharedData.prefs.getName("nickName", "").isEmpty()){ // 만약 첫 로그인이라면(sharedPreference에 값이 없다면)
            enterButton.setOnClickListener {
                newNickname = setNickName.text.toString()
                if(newNickname.length < 3){ // 아이디가 두 글자 이하이면 다시 입력하라고 나옵니다.
                    Toast.makeText(this, "Please enter at least three characters!", Toast.LENGTH_SHORT).show()
                }else{
                    var updateUserName = mutableMapOf<String, Any>()
                    updateUserName["userName"] = newNickname // Firebase에 update할 값 준비
                    FirebaseDatabase.getInstance().reference.updateChildren(updateUserName)//Firebase에 설정한 이름 탑재
                    Toast.makeText(this, "Login Success", Toast.LENGTH_SHORT).show()

                    val user : User = User(newNickname, "0", "0", "0%")

                    FirebaseDatabase.getInstance().reference.child("Users").push().setValue(user)

                    setNickName.visibility = GONE
                    enterButton.visibility = GONE

                    SharedData.prefs.setName("nickName", newNickname)

                    welcomeSign.text = "Welcome, $newNickname :)"
                    welcomeSign.visibility = VISIBLE
                }
            }
        }else{ // 만약 첫 로그인이 아니고 이전에 로그인한 기록이 있으면,
            newNickname = SharedData.prefs.getName("nickName", "")
            setNickName.visibility = GONE
            enterButton.visibility = GONE

            SharedData.prefs.setName("nickName", newNickname)

            welcomeSign.text = "Welcome back, $newNickname :)\nHave a great day."
            welcomeSign.visibility = VISIBLE

        }


        gameStartButton.setOnClickListener {

            //val room : Room = Room("temp", "", "", "1")
            //FirebaseDatabase.getInstance().reference.child("roomId").push().setValue(room)

            intent.putExtra("player1NickName", newNickname)
            startActivity(intent)
        }
    }

}