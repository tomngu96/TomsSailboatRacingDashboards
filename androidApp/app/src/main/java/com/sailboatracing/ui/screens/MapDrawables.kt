package com.sailboatracing.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable

/**
 * Shared map marker drawables used by DashboardScreen and SessionsScreen.
 */

fun boatBitmapDrawable(context: Context, headingDeg: Float): BitmapDrawable {
    val dp   = context.resources.displayMetrics.density
    val size = (20 * dp).toInt()
    val bmp  = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val cv   = Canvas(bmp)
    cv.rotate(headingDeg, size / 2f, size / 2f)
    val w = size.toFloat(); val h = size.toFloat()
    // Black outline
    cv.drawPath(Path().apply {
        moveTo(w * 0.50f, h * 0.02f); lineTo(w * 0.86f, h * 0.92f)
        lineTo(w * 0.50f, h * 0.65f); lineTo(w * 0.14f, h * 0.92f); close()
    }, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; style = Paint.Style.FILL })
    // Amber fill
    cv.drawPath(Path().apply {
        moveTo(w * 0.50f, h * 0.10f); lineTo(w * 0.79f, h * 0.88f)
        lineTo(w * 0.50f, h * 0.65f); lineTo(w * 0.21f, h * 0.88f); close()
    }, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#FF8C00"); style = Paint.Style.FILL
    })
    return BitmapDrawable(context.resources, bmp)
}