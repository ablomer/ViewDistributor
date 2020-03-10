package com.ablomer.vddemo

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import com.ablomer.viewdistributor.ViewDistributor

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val viewDistributor = findViewById<ViewDistributor>(R.id.viewDistributor)
        val shuffleButton = findViewById<Button>(R.id.shuffleButton)

        val shuffleButtonRegion = ViewDistributor.viewToRegion(shuffleButton)
        viewDistributor.addAvoidRegion(shuffleButtonRegion)

        shuffleButton.setOnClickListener {
            viewDistributor.shuffle()
        }
    }
}
