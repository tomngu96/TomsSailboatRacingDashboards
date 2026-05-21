package com.sailboatracing.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

/**
 * Shared map utilities used by DashboardScreen, CourseScreen, and SessionsScreen.
 */

/**
 * Returns true when the device has an active internet-capable network connection.
 *
 * OsmDroid's default behaviour with [MapView.setUseDataConnection] = true can short-circuit
 * its tile-loading pipeline when ConnectivityManager reports no network, preventing even
 * pre-downloaded SQL-cache tiles from rendering.  Callers should pass this result to
 * [MapView.setUseDataConnection] so that offline tiles are served from the SQL cache.
 */
fun isNetworkAvailable(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val network = cm.activeNetwork ?: return false
        val caps    = cm.getNetworkCapabilities(network) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } else {
        @Suppress("DEPRECATION")
        cm.activeNetworkInfo?.isConnected == true
    }
}

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