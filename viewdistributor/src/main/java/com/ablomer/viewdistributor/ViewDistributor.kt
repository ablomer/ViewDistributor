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
    private val mOccupiedRegions = mutableListOf<RectF>()

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

                // TODO: Bleed attribute (just use draw area scaling)
                // TODO: Right now the position is determined by the top-left corner, so the bottom-right corner is bleeding out of ViewGroup

            } finally {
                recycle()
            }
        }
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        updateDrawRegions(width, height, scale)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        mLayoutLeft = l
        mLayoutRight = r
        shuffle()
    }

    fun shuffle() {
        mOccupiedRegions.clear()

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
                val bestRect = RectF(it.x, it.y, it.x + width, it.y + height)
                mOccupiedRegions.add(bestRect)

                val rotation = if (mRotationStyle == "position") {
                    scale(it.x, mLayoutLeft.toFloat(), mLayoutRight.toFloat(), minAngle, maxAngle)
                } else {
                    randomFloat(minAngle, maxAngle)
                }

                setChildFrame(
                    view,
                    it.x.toInt(),
                    it.y.toInt(),
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

    private fun bestCandidate(width: Int, height: Int): PointF? {

        if (mOccupiedRegions.isEmpty()) {
            val region = mDrawRegions[randomInt(0, mDrawRegions.size)]
            return randomRectPoint(region)
        }

        var bestCandidate: PointF? = null
        var bestDistance = 0f

        for (i in 0 until iterations) {

            val region = mDrawRegions[randomInt(0, mDrawRegions.size)]
            val candidate = randomRectPoint(region)
            val candidateRect = pointToRect(candidate, width.toFloat(), height.toFloat())
            val closest = findClosest(candidateRect)
            val distance = distance(candidateRect, closest!!)

            if (distance > bestDistance) {
                bestCandidate = candidate
                bestDistance = distance
            }
        }

        return bestCandidate
    }

    private fun randomRectPoint(rect: RectF): PointF {
        val x = randomFloat(rect.left, rect.right)
        val y = randomFloat(rect.top, rect.bottom)
        return PointF(x, y)
    }

    private fun findClosest(rect: RectF): RectF? {

        var closest: RectF? = null
        var closestDistance = Float.MAX_VALUE

        for (otherRect in mOccupiedRegions) {

            val distance = distance(rect, otherRect)

            if (distance < closestDistance) {
                closest = otherRect
                closestDistance = distance
            }
        }

        return closest
    }

    private fun pointToRect(a: PointF, width: Float, height: Float): RectF {
        return RectF(a.x, a.y, a.x + width, a.y + height)
    }

    private fun distance(a: PointF, b: PointF): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return dx * dx + dy * dy // Sqrt omitted, monotonic function
    }

    private fun distance(a: RectF, b: RectF): Float {

        val left = b.right < a.left
        val right = a.right < b.left
        val bottom = b.bottom < a.top
        val top = a.bottom < b.top

        if (top && left) {
            val aLeftBottom = PointF(a.left, a.bottom)
            val bRightTop = PointF(b.right, b.top)
            return distance(aLeftBottom, bRightTop)

        } else if (left && bottom) {
            val aLeftTop = PointF(a.left, a.top)
            val bRightBottom = PointF(b.right, b.bottom)
            return distance(aLeftTop, bRightBottom)

        } else if (bottom && right) {
            val aRightTop = PointF(a.right, a.top)
            val bLeftBottom = PointF(b.left, b.bottom)
            return distance(aRightTop, bLeftBottom)

        } else if (right && top) {
            val aRightBottom = PointF(a.right, a.bottom)
            val bLeftTop = PointF(b.left, b.top)
            return distance(aRightBottom, bLeftTop)

        } else if (left) {
            return a.left - b.right

        } else if (right) {
            return b.left - a.right

        } else if (bottom) {
            return a.top - b.bottom

        } else if (top) {
            return b.top - a.bottom

        } else {
            return 0f
        }
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
