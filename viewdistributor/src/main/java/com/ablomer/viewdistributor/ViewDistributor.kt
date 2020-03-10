package com.ablomer.viewdistributor

import android.content.Context
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
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

    private val mDrawRegions = mutableListOf<RectF>()
    private var mAvoidRegions = mutableListOf<RectF>()

    private val mPlacedPoints = mutableListOf<PointF>()

    var iterations = 200
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
        updateDrawRegions(width, height, scale)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) { // TODO: Animate by redrawing?
        mLayoutLeft = l
        mLayoutRight = r
        shuffle()
    }

    fun shuffle() {
        mPlacedPoints.clear()

        for (i in 0 until childCount) {
            val view = getChildAt(i)

            bestCandidate()?.let {
                val bestCandidate = centerToLeftTop(it, view.measuredWidth, view.measuredHeight)
                mPlacedPoints.add(bestCandidate)

                val rotation = if (mRotationStyle == "position") {
                    scale(bestCandidate.x, mLayoutLeft.toFloat(), mLayoutRight.toFloat(), minAngle, maxAngle)
                } else {
                    randomFloat(minAngle, maxAngle)
                }

                var height = view.layoutParams.height
                var width = view.layoutParams.width

                view.measure(width, height)

                if (height < 0) {
                    height = view.measuredHeight
                }

                if (width < 0) {
                    width = view.measuredWidth
                }

                setChildFrame(
                    view,
                    bestCandidate.x.toInt(),
                    bestCandidate.y.toInt(),
                    width,
                    height,
                    rotation
                )
            }
        }
    }

    private fun updateDrawRegions(width: Int, height: Int, padding: Float) {

        val drawArea = Rect(0, 0, width, height)
        val paddedDrawArea = scale(drawArea, padding)

        mDrawRegions.clear()

        val xs = mutableListOf<Float>()
        val ys = mutableListOf<Float>()

        xs.add(paddedDrawArea.left)
        xs.add(paddedDrawArea.right)
        ys.add(paddedDrawArea.top)
        ys.add(paddedDrawArea.bottom)

        for (avoid in mAvoidRegions) {
            xs.add(avoid.left)
            xs.add(avoid.right)
            ys.add(avoid.top)
            ys.add(avoid.bottom)
        }

        xs.sort()
        ys.sort()

        for (ix in 0 until xs.size - 1) {
            for (iy in 0 until ys.size - 1) {
                mDrawRegions.add(
                    RectF(
                        xs[ix], ys[iy],
                        xs[ix + 1], ys[iy + 1]
                    )
                )
            }
        }

        val iterator = mDrawRegions.iterator()
        while (iterator.hasNext()) {
            val next = iterator.next()
            for (avoid in mAvoidRegions) {
                if (RectF.intersects(next, avoid)) iterator.remove()
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

    private fun bestCandidate(): PointF? {

        if (mPlacedPoints.isEmpty())
            return randomPoint()

        var bestCandidate: PointF? = null
        var bestDistance = 0f

        for (i in 0 until iterations) {

            val candidate = randomPoint()
            val closest = findClosest(candidate)
            val distance = distance(candidate, closest!!)

            if (distance > bestDistance) {
                bestCandidate = candidate
                bestDistance = distance
            }
        }

        return bestCandidate
    }

    private fun randomPoint(): PointF {
        val region = mDrawRegions[randomInt(0, mDrawRegions.size)]
        val x = randomFloat(region.left, region.right)
        val y = randomFloat(region.top, region.bottom)
        return PointF(x, y)
    }

    private fun findClosest(point: PointF): PointF? {

        var closest: PointF? = null
        var closestDistance = Float.MAX_VALUE

        for (rectangle in mPlacedPoints) {

            val distance = distance(point, rectangle)

            if (distance < closestDistance) {
                closest = rectangle
                closestDistance = distance
            }
        }

        return closest
    }

    private fun distance(a: PointF, b: PointF): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return dx * dx + dy * dy // Sqrt omitted, monotonic function
    }

    private fun centerToLeftTop(point: PointF, width: Int, height: Int): PointF {
        val left = point.x - width / 2f
        val top = point.y - height / 2f
        return PointF(left, top)
    }

    /**
     * Generates a random float within [min, max)
     */
    private fun randomFloat(min: Float, max: Float): Float {
        return mRandom.nextFloat() * (max - min) + min
    }

    /**
     * Generates a random integer within [min, max)
     */
    private fun randomInt(min: Int, max: Int): Int {
        return mRandom.nextInt(max - min) + min
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

    fun addAvoidRegion(rectF: RectF) {
        mAvoidRegions.add(rectF)
    }

    fun removeAvoidRegion(rectF: RectF) {
        mAvoidRegions.remove(rectF)
    }

    fun setRandomRotationStyle() {
        mRotationStyle = "random"
    }

    fun setPositionRotationStyle() {
        mRotationStyle = "position"
    }

    companion object {

        fun viewToRegion(view: View): RectF {
            val location = FloatArray(2)
            location[0] = view.x
            location[1] = view.y
            return RectF(location[0], location[1], location[0] + view.width, location[1] + view.height)
        }

        fun scale(rect: RectF, factor: Float): RectF {
            val diffHorizontal = rect.width() * (factor - 1)
            val diffVertical = rect.height() * (factor - 1)

            return RectF(
                rect.left - diffHorizontal / 2f, rect.top - diffVertical / 2f,
                rect.right + diffHorizontal / 2f, rect.bottom + diffVertical / 2f
            )
        }

        fun scale(rect: Rect, factor: Float): RectF {
            return scale(RectF(rect), factor)
        }
    }
}
