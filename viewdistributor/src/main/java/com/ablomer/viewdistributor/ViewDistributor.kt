package com.ablomer.viewdistributor

import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
import android.view.View
import android.view.ViewGroup
import java.util.*

class ViewDistributor {

    private val MAX_RETRIES = 200

    private val rectangles: MutableList<ViewRectangle> = ArrayList()
    private var avoids: MutableList<RectF> = ArrayList()

    private val placedPoints: MutableList<PointF?> = ArrayList()
    private val regions: MutableList<RectF> = ArrayList()

    private val random = Random()

    private var onRandomizeComplete: OnRandomizeComplete? = null

    /**
     * Area in which views will be distributed
     */
    private var area: RectF? = null
    private var paddedArea: RectF? = null

    private var minAngle = 0
    private var maxAngle = 0

    fun ViewDistributor(viewGroup: ViewGroup) {
        area = RectF(0f, 0f, viewGroup.width.toFloat(), viewGroup.height.toFloat())
        paddedArea = RectF(area)

        Log.d(TAG, "Distributing views in $area")

        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            rectangles.add(ViewRectangle(child))
        }
    }

    fun randomize() { // TODO: Run this in background
        placedPoints.clear()
        updateRegions()
        for (rectangle in rectangles) {
            var bestCandidate = bestCandidate()
            bestCandidate = centerToLeftTop(bestCandidate, rectangle.width(), rectangle.height())
            placedPoints.add(bestCandidate)
            val r = scale(
                bestCandidate!!.x,
                paddedArea!!.left,
                paddedArea!!.right,
                minAngle.toFloat(),
                maxAngle.toFloat()
            )
            if (onRandomizeComplete != null) onRandomizeComplete!!.onRandomizeComplete(
                rectangle.view,  // TODO: Run this in main thread
                bestCandidate.x, bestCandidate.y, r
            )
        }
    }

    private fun bestCandidate(): PointF? {

        if (placedPoints.isEmpty()) return randomPoint()

        var bestCandidate: PointF? = null
        var bestDistance = 0f

        for (i in 0 until MAX_RETRIES) {
            val candidate = randomPoint()
            val closest = findClosest(candidate)
            val distance = distance(candidate, closest)
            if (distance > bestDistance) {
                bestCandidate = candidate
                bestDistance = distance
            }
        }

        return bestCandidate
    }

    private fun randomPoint(): PointF {
        val region = regions[randomInt(0, regions.size - 1)]
        val x = randomFloat(region.left, region.right)
        val y = randomFloat(region.top, region.bottom)
        return PointF(x, y)
    }

    private fun findClosest(point: PointF): PointF? { // TODO: Use quad-tree (see link above)

        var closest: PointF? = null
        var closestDistance = Float.MAX_VALUE

        for (rectangle in placedPoints) {

            val distance = distance(point, rectangle)

            if (distance < closestDistance) {
                closest = rectangle
                closestDistance = distance
            }
        }

        return closest
    }

    private fun distance(a: RectF, b: RectF): Float {
        val dx = a.centerX() - b.centerX()
        val dy = a.centerY() - b.centerY()
        return dx * dx + dy * dy // Sqrt omitted, monotonic function
    }

    private fun distance(a: PointF, b: RectF): Float {
        val dx = a.x - b.centerX()
        val dy = a.y - b.centerY()
        return dx * dx + dy * dy // Sqrt omitted, monotonic function
    }

    private fun distance(a: PointF, b: PointF?): Float {
        val dx = a.x - b!!.x
        val dy = a.y - b.y
        return dx * dx + dy * dy // Sqrt omitted, monotonic function
    }

    private fun centerToLeftTop(
        point: PointF?,
        width: Float,
        height: Float
    ): PointF? {
        val left = point!!.x - width / 2f
        val top = point.y - height / 2f
        return PointF(left, top)
    }

    private fun randomFloat(min: Float, max: Float): Float {
        return random.nextFloat() * (max - min) + min
    }

    /**
     * Generates a random integer between min and max, inclusive
     */
    private fun randomInt(min: Int, max: Int): Int {
        return random.nextInt(max - min + 1) + min
    }

    private fun scale(rect: RectF?, factor: Float): RectF {

        var factor = factor
        factor -= 1f

        val diffHorizontal = rect!!.width() * factor
        val diffVertical = rect.height() * factor

        return RectF(
            rect.left - diffHorizontal / 2f, rect.top - diffVertical / 2f,
            rect.right + diffHorizontal / 2f, rect.bottom + diffVertical / 2f
        )
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
    fun scale(value: Float, oldMin: Float, oldMax: Float, newMin: Float, newMax: Float): Float {
        return (newMax - newMin) * (value - oldMin) / (oldMax - oldMin) + newMin
    }

    private fun updateRegions() {

        regions.clear()

        val xs: MutableList<Float> = ArrayList()
        val ys: MutableList<Float> = ArrayList()

        xs.add(paddedArea!!.left)
        xs.add(paddedArea!!.right)
        ys.add(paddedArea!!.top)
        ys.add(paddedArea!!.bottom)

        for (avoid in avoids) {
            xs.add(avoid.left)
            xs.add(avoid.right)
            ys.add(avoid.top)
            ys.add(avoid.bottom)
        }

        Collections.sort(xs)
        Collections.sort(ys)

        for (ix in 0 until xs.size - 1) {
            for (iy in 0 until ys.size - 1) {
                regions.add(
                    RectF(
                        xs[ix], ys[iy],
                        xs[ix + 1], ys[iy + 1]
                    )
                )
            }
        }

        val iterator = regions.iterator()
        while (iterator.hasNext()) {
            val next = iterator.next()
            for (avoid in avoids) {
                if (RectF.intersects(next, avoid)) iterator.remove()
            }
        }

        Log.v(TAG, "Regions " + regions.toTypedArray().contentToString())
    }

    fun boundPadding(areaPadding: Float): ViewDistributor? {
        paddedArea = scale(area, areaPadding)
        return this
    }

    fun avoidPadding(avoidPadding: Float): ViewDistributor? {
        val paddedAvoids: MutableList<RectF> = ArrayList()
        for (avoid in avoids) {
            paddedAvoids.add(scale(avoid, avoidPadding))
        }
        avoids = paddedAvoids
        return this
    }

    fun minAngle(minAngle: Int): ViewDistributor? {
        this.minAngle = minAngle
        return this
    }

    fun maxAngle(maxAngle: Int): ViewDistributor? {
        this.maxAngle = maxAngle
        return this
    }

    fun avoid(view: View?): ViewDistributor? {
        if (view != null) avoids.add(calculateRectangle(view))
        return this
    }

    fun onRandomizeComplete(onRandomizeComplete: OnRandomizeComplete?): ViewDistributor? {
        this.onRandomizeComplete = onRandomizeComplete
        return this
    }

    companion object {

        private val TAG = ViewDistributor::class.java.simpleName

        private fun calculateRectangle(view: View): RectF {

            val location = FloatArray(2)

            // view.getLocationOnScreen(location)

            location[0] = view.x
            location[1] = view.y

            val result = RectF(location[0], location[1], location[0] + view.width, location[1] + view.height)

            Log.v(TAG, "$view rectangle $result")

            return RectF(location[0], location[1], location[0] + view.width, location[1] + view.height)
        }
    }

    class ViewRectangle internal constructor(var view: View) : RectF(calculateRectangle(view)) {
        val width: Int
            get() = view.width

        val height: Int
            get() = view.height
    }

    abstract class OnRandomizeComplete {
        abstract fun onRandomizeComplete(
            view: View?,
            x: Float,
            y: Float,
            r: Float
        )
    }

}