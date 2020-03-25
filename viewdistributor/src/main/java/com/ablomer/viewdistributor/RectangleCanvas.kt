package com.ablomer.viewdistributor

import android.graphics.Point
import android.graphics.Rect
import java.util.*

class RectangleCanvas(left: Int, top: Int, right: Int, bottom: Int) {

    private val mRandom = Random()

    private val mDrawRegion = Rect(left, top, right, bottom)
    private val mAvoidRegions = mutableListOf<Rect>()

    fun randomRect(width: Int, height: Int): Rect? {

        val rect = Rect(0, 0, width, height)

        for (retry in 0 until PLACE_RETRIES) {
            val xOffset = random(mDrawRegion.left, mDrawRegion.right - width)
            val yOffset = random(mDrawRegion.top, mDrawRegion.bottom - height)

            rect.offsetTo(xOffset, yOffset)

            if (!intersects(rect)) {
                return rect
            }
        }

        return null // No room
    }

    fun findClosest(rect: Rect): Rect? {

        var closest: Rect? = null
        var closestDistance = Int.MAX_VALUE

        for (otherRect in mAvoidRegions) {

            val distance = distance(rect, otherRect)

            if (distance < closestDistance) {
                closest = otherRect
                closestDistance = distance
            }
        }

        return closest
    }

    fun addRect(rect: Rect) {
        mAvoidRegions.add(rect)
    }

    fun addRects(rects: List<Rect>) {
        mAvoidRegions.addAll(rects)
    }

    fun isClear(): Boolean {
        return mAvoidRegions.isEmpty()
    }

    fun clear() {
        mAvoidRegions.clear()
    }

    fun intersects(rect: Rect): Boolean {
        for (avoid in mAvoidRegions) {
            if (rect.intersects(avoid)) {
                return true
            }
        }

        return false
    }

    private fun distance(a: Point, b: Point): Int {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return dx * dx + dy * dy // Sqrt omitted, monotonic function
    }

    fun distance(a: Rect, b: Rect): Int {

        val left = b.right < a.left
        val right = a.right < b.left
        val bottom = b.bottom < a.top
        val top = a.bottom < b.top

        if (top && left) {
            val aLeftBottom = Point(a.left, a.bottom)
            val bRightTop = Point(b.right, b.top)
            return distance(aLeftBottom, bRightTop)

        } else if (left && bottom) {
            val aLeftTop = Point(a.left, a.top)
            val bRightBottom = Point(b.right, b.bottom)
            return distance(aLeftTop, bRightBottom)

        } else if (bottom && right) {
            val aRightTop = Point(a.right, a.top)
            val bLeftBottom = Point(b.left, b.bottom)
            return distance(aRightTop, bLeftBottom)

        } else if (right && top) {
            val aRightBottom = Point(a.right, a.bottom)
            val bLeftTop = Point(b.left, b.top)
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
            return 0
        }
    }

    private fun random(min: Int, max: Int): Int {
        return mRandom.nextInt(max - min) + min
    }

    private fun Rect.intersects(other: Rect): Boolean {
        return intersects(other.left, other.top, other.right, other.bottom)
    }

    companion object {
        private const val PLACE_RETRIES = 2000
    }

}