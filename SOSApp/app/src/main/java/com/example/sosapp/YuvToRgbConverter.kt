package com.example.sosapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.media.Image
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

class YuvToRgbConverter(context: Context) {

    fun yuvToRgb(image: ImageProxy, output: Bitmap) {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = android.graphics.YuvImage(
            nv21,
            ImageFormat.NV21,
            image.width,
            image.height,
            null
        )

        val out = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(
            android.graphics.Rect(0, 0, image.width, image.height),
            100,
            out
        )
        val jpegBytes = out.toByteArray()

        val tempBitmap = android.graphics.BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
        val scaled = Bitmap.createScaledBitmap(tempBitmap, output.width, output.height, false)
        val canvas = android.graphics.Canvas(output)
        canvas.drawBitmap(scaled, 0f, 0f, null)
    }
}
