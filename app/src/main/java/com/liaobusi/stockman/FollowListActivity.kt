package com.liaobusi.stockman

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle




class FollowListActivity : AppCompatActivity() {

    companion object{
        fun startFollowListActivity(context:Context){

            val i= Intent(context,FollowListActivity::class.java)
            context.startActivity(i)


        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_follow_list)
        supportActionBar?.title="关注列表"
    }
}