package com.ablomer.viewdistributor

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import java.util.*


class ViewDistributor @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0

) : ViewGroup(context, attrs, defStyleAttr) {

    private val mRandom = Random()

    private lateinit var mCanvas: RectangleCanvas

    var iterations = 2000
    var scale = 1f
    var minAngle = 0f
    var maxAngle = 0f
    private var mRotationStyle = "random" // position or random

    private var mLayoutLeft = 0
    private var mLayoutRight = 0

    init {
        context.obtainStyledAttributes(attrs, R.styleable.ViewDistributor, 0, 0).apply {
            try {
                getFloat(R.styleable.ViewDistributor_drawAreaScale, 1f).let { scale = it }
                getFloat(R.styleable.ViewDistributor_minAngle, 0f).let { minAngle = it }
                getFloat(R.styleable.ViewDistributor_maxAngle, 0f).let { maxAngle = it }
                getInt(R.styleable.ViewDistributor_iterations, 200).let { iterations = it }
                getString(R.styleable.ViewDistributor_rotationStyle)?.let { mRotationStyle = it }

            } finally {
                recycle()
            }
        }
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)

        val xScale = width * (scale - 1f)
        val yScale = height * (scale - 1f)

        mCanvas = RectangleCanvas(
            -xScale.toInt(),
            -yScale.toInt(),
            width + xScale.toInt(),
            height + yScale.toInt()
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        mLayoutLeft = l
        mLayoutRight = r
        shuffle()
    }

    fun shuffle() {
        mCanvas.clear()

        for (i in 0 until childCount) {
            val view = getChildAt(i)

            var width = view.layoutParams.width
            var height = view.layoutParams.height

            view.measure(width, height)

            if (width < 0) {
                width = view.measuredWidth
            }

            if (height < 0) {
                height = view.measuredHeight
            }

            bestCandidate(width, height)?.let {

                mCanvas.addRect(it)

                val rotation = if (mRotationStyle == "position") {
                    scale(it.left.toFloat(), mLayoutLeft.toFloat(), mLayoutRight.toFloat(), minAngle, maxAngle)
                } else {
                    randomFloat(minAngle, maxAngle)
                }

                setChildFrame(
                    view,
                    it.left,
                    it.top,
                    width,
                    height,
                    rotation
                )
            }
        }
    }

    private fun setChildFrame(
        child: View,
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        rotation: Float
    ) {
        child.layout(left, top, left + width, top + height)
        child.rotation = rotation
    }

    private fun bestCandidate(width: Int, height: Int): Rect? {

        if (mCanvas.isClear()) {
            return mCanvas.randomRect(width, height)
        }

        var bestCandidate: Rect? = null
        var bestDistance = 0

        for (i in 0 until iterations) {

            mCanvas.randomRect(width, height)?.let {
                val closest = mCanvas.findClosest(it)
                val distance = mCanvas.distance(it, closest!!)

                if (distance > bestDistance) {
                    bestCandidate = it
                    bestDistance = distance
                }
            }
        }

        return bestCandidate
    }

    /**
     * Generates a random float within [min, max)
     */
    private fun randomFloat(min: Float, max: Float): Float {
        return mRandom.nextFloat() * (max - min) + min
    }

    /**
     * Takes a value from one range and returns the associated value in a new range. This function
     * assumes that all ranges are linear.
     *
     * @param value Value to convert from the old scale to the new scale
     * @param oldMin The minimum value of the range that value is in
     * @param oldMax The maximum value of the range that value is in
     * @param newMin The minimum value of the new range for value
     * @param newMax The maximum value of the new range for value
     * @return A new value in the new range representing the given value in the old range
     */
    private fun scale(
        value: Float,
        oldMin: Float,
        oldMax: Float,
        newMin: Float,
        newMax: Float
    ): Float {
        return (newMax - newMin) * (value - oldMin) / (oldMax - oldMin) + newMin
    }

    fun setRandomRotationStyle() {
        mRotationStyle = "random"
    }

    fun setPositionRotationStyle() {
        mRotationStyle = "position"
    }

    companion object {

        fun viewToRegion(view: View): Rect {
            val location = IntArray(2)
            location[0] = view.x.toInt()
            location[1] = view.y.toInt()
            return Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
        }
    }
}
