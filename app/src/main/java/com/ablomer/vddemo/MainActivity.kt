package com.ablomer.vddemo

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.ablomer.viewdistributor.ViewDistributor

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val viewDistributor = findViewById<ViewDistributor>(R.id.viewDistributor)
        val shuffleButton = findViewById<Button>(R.id.shuffleButton)
        val animateButton = findViewById<Button>(R.id.animateButton)

        val shuffleButtonRegion = ViewDistributor.viewToRegion(shuffleButton)
        viewDistributor.addAvoidRegion(shuffleButtonRegion)

        shuffleButton.setOnClickListener {
            viewDistributor.shuffle()
        }

        animateButton.setOnClickListener {
            val fadeOut = ObjectAnimator.ofFloat(viewDistributor, "alpha", 0f)
            val fadeIn = ObjectAnimator.ofFloat(viewDistributor, "alpha", 1f)

            fadeOut.addListener(object : Animator.AnimatorListener {

                override fun onAnimationEnd(animation: Animator?) {
                    viewDistributor.shuffle()
                }

                override fun onAnimationRepeat(animation: Animator?) {}
                override fun onAnimationCancel(animation: Animator?) {}
                override fun onAnimationStart(animation: Animator?) {}
            })

            AnimatorSet().apply {
                playSequentially(fadeOut, fadeIn)
                start()
            }
        }

    }
}
