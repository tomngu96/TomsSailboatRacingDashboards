package com.sailboatracing.offline

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.view.MotionEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class SelectionBoxOverlay(
    initialNorth: Double,
    initialSouth: Double,
    initialWest: Double,
    initialEast: Double,
    private val onChange: (north: Double, south: Double, west: Double, east: Double) -> Unit
) : Overlay() {

    var north = initialNorth
    var south = initialSouth
    var west  = initialWest
    var east  = initialEast

    private val HANDLE_RADIUS = 44f
    private var dragHandle = -1

    private val fillPaint = Paint().apply {
        color = 0x220088FF.toInt()
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint().apply {
        color = 0xFF2288FF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val handleFill = Paint().apply {
        color = 0xFF2288FF.toInt()
        style = Paint.Style.FILL
    }
    private val handleStroke = Paint().apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }

    // Returns screen Points for [NW, NE, SW, SE]
    private fun handles(mapView: MapView): List<Point> {
        val proj = mapView.projection
        val nw = Point(); proj.toPixels(GeoPoint(north, west), nw)
        val se = Point(); proj.toPixels(GeoPoint(south, east), se)
        return listOf(
            Point(nw.x, nw.y),  // NW
            Point(se.x, nw.y),  // NE
            Point(nw.x, se.y),  // SW
            Point(se.x, se.y)   // SE
        )
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val proj = mapView.projection
        val nw = Point(); proj.toPixels(GeoPoint(north, west), nw)
        val se = Point(); proj.toPixels(GeoPoint(south, east), se)

        val l = min(nw.x, se.x).toFloat()
        val t = min(nw.y, se.y).toFloat()
        val r = max(nw.x, se.x).toFloat()
        val b = max(nw.y, se.y).toFloat()

        canvas.drawRect(l, t, r, b, fillPaint)
        canvas.drawRect(l, t, r, b, strokePaint)

        for (h in handles(mapView)) {
            canvas.drawCircle(h.x.toFloat(), h.y.toFloat(), HANDLE_RADIUS, handleFill)
            canvas.drawCircle(h.x.toFloat(), h.y.toFloat(), HANDLE_RADIUS, handleStroke)
        }
    }

    override fun onTouchEvent(event: MotionEvent, mapView: MapView): Boolean {
        val proj = mapView.projection
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dragHandle = handles(mapView).indexOfFirst { h ->
                    val dx = event.x - h.x
                    val dy = event.y - h.y
                    sqrt(dx * dx + dy * dy) < HANDLE_RADIUS * 2.5f
                }
                return dragHandle >= 0
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragHandle < 0) return false
                val geo = proj.fromPixels(event.x.toInt(), event.y.toInt())
                when (dragHandle) {
                    0 -> { north = geo.latitude; west = geo.longitude }  // NW
                    1 -> { north = geo.latitude; east = geo.longitude }  // NE
                    2 -> { south = geo.latitude; west = geo.longitude }  // SW
                    3 -> { south = geo.latitude; east = geo.longitude }  // SE
                }
                onChange(north, south, west, east)
                mapView.invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragHandle = -1
            }
        }
        return false
    }
}
