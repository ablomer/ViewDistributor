package com.ablomer.viewdistributor

import android.content.Context
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.ViewGroup
import java.util.*


class ViewDistributor @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0

) : ViewGroup(context, attrs, defStyleAttr) {

    private var mAvoids = mutableListOf<RectF>()

    private val mPlacedPoints = mutableListOf<PointF>()
    private val mDrawRegions = mutableListOf<RectF>()

    private val mRandom = Random()

    private val mMaxRetries = 200 // TODO: What does this do?
    private var mPadding = 1f
    private var mMinAngle = 0
    private var mMaxAngle = 0

    // https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/widget/LinearLayout.java

    /*
    ViewDistributor(rlLoginBackground)
        .minAngle(-15)
        .maxAngle(15)
        .avoid(rlLoginLogo) // TODO: Do each individual element individually
        .boundPadding(0.9f) // <1 shrinks
        .avoidPadding(1.2f)
        .onRandomizeComplete(object : OnRandomizeComplete() {
            override fun onRandomizeComplete(
                view: View?,
                x: Float,
                y: Float,
                r: Float
            ) {
                view?.x = x
                view?.y = y
                view?.rotation = r
            }
        }).randomize()
     */

    init {
        context.obtainStyledAttributes(attrs, R.styleable.ViewDistributor, 0, 0).apply {
            try {
//                getString(R.styleable.SuffixEditText_suffix)?.let { mSuffix = it }

            } finally {
                recycle()
            }
        }
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        updateDrawRegions(width, height, mPadding)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) { // TODO: Animate by redrawing?

        mPlacedPoints.clear()

        for (i in 0 until childCount) {
            val view = getChildAt(i)

            bestCandidate()?.let {
                val bestCandidate = centerToLeftTop(it, view.measuredWidth, view.measuredHeight)
                mPlacedPoints.add(bestCandidate)

                mMinAngle = -15 // TODO: Attributes
                mMaxAngle = 15

                val rotation = scale(bestCandidate.x, l.toFloat(), r.toFloat(), mMinAngle.toFloat(), mMaxAngle.toFloat()) // TODO: This is angling by x position

                view.measure(width, height)

                setChildFrame(
                    view,
                    bestCandidate.x.toInt(),
                    bestCandidate.y.toInt(),
                    view.measuredWidth,
                    view.measuredHeight,
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

        for (avoid in mAvoids) {
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
            for (avoid in mAvoids) {
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

        for (i in 0 until mMaxRetries) {

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
        val region = mDrawRegions[randomInt(0, mDrawRegions.size - 1)]
        val x = randomFloat(region.left, region.right)
        val y = randomFloat(region.top, region.bottom)
        return PointF(x, y)
    }

    // https://bost.ocks.org/mike/algorithms/
    private fun findClosest(point: PointF): PointF? { // TODO: Use quad-tree (see link above)

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

    private fun randomFloat(min: Float, max: Float): Float {
        return mRandom.nextFloat() * (max - min) + min
    }

    /**
     * Generates a random integer between min and max, inclusive
     */
    private fun randomInt(min: Int, max: Int): Int {
        return mRandom.nextInt(max - min + 1) + min
    }

    private fun scale(rect: RectF, factor: Float): RectF {

        var factor = factor
        factor -= 1f

        val diffHorizontal = rect.width() * factor
        val diffVertical = rect.height() * factor

        return RectF(
            rect.left - diffHorizontal / 2f, rect.top - diffVertical / 2f,
            rect.right + diffHorizontal / 2f, rect.bottom + diffVertical / 2f
        )
    }

    private fun scale(rect: Rect, factor: Float): RectF {
        return scale(RectF(rect), factor)
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

    private fun calculateRectangle(view: View): RectF {
        val location = FloatArray(2)
        location[0] = view.x
        location[1] = view.y
        return RectF(location[0], location[1], location[0] + view.width, location[1] + view.height)
    }

    /*
    var suffix: String
        get() = mSuffix
        set(suffix) {
            mSuffix = suffix
            invalidate()
        }
     */

    fun setPadding() {
        // TODO
    }

    fun avoidPadding(avoidPadding: Float) {
        val paddedAvoids: MutableList<RectF> = ArrayList()
        for (avoid in mAvoids) {
            paddedAvoids.add(scale(avoid, avoidPadding))
        }
        mAvoids = paddedAvoids
    }

    fun minAngle(minAngle: Int) {
        mMinAngle = minAngle
    }

    fun maxAngle(maxAngle: Int) {
        mMaxAngle = maxAngle
    }

    fun avoid(view: View?) {
        if (view != null) mAvoids.add(calculateRectangle(view))
    }

    fun avoid(rectF: RectF?) {
        if (rectF != null) mAvoids.add(rectF)
    }

}