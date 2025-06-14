package com.example.assistantapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Base64
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class FaceRecognitionManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("face_recognition_prefs", Context.MODE_PRIVATE)
    private val FACES_KEY = "saved_faces"

    private val detectorOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
        .build()
    private val detector = FaceDetection.getClient(detectorOptions)

    // Save a face with a name (stores cropped face as Base64 string)
    fun saveFace(name: String, faceBitmap: Bitmap) {
        val facesJson = JSONObject(prefs.getString(FACES_KEY, "{}"))
        facesJson.put(name, bitmapToBase64(faceBitmap))
        prefs.edit().putString(FACES_KEY, facesJson.toString()).apply()
    }

    // Get all saved faces (name to Bitmap)
    fun getSavedFaces(): Map<String, Bitmap> {
        val facesJson = JSONObject(prefs.getString(FACES_KEY, "{}"))
        val map = mutableMapOf<String, Bitmap>()
        for (key in facesJson.keys()) {
            val bmp = base64ToBitmap(facesJson.getString(key))
            if (bmp != null) map[key] = bmp
        }
        return map
    }

    // Detect faces in a bitmap
    suspend fun detectFaces(bitmap: Bitmap): List<Face> {
        val image = InputImage.fromBitmap(bitmap, 0)
        return detector.process(image).await()
    }

    // Crop face from bitmap using bounding box
    fun cropFace(bitmap: Bitmap, boundingBox: Rect): Bitmap {
        val left = boundingBox.left.coerceAtLeast(0)
        val top = boundingBox.top.coerceAtLeast(0)
        val right = boundingBox.right.coerceAtMost(bitmap.width)
        val bottom = boundingBox.bottom.coerceAtMost(bitmap.height)
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    // Compare a detected face to saved faces (simple pixel comparison, can be improved)
    fun matchFace(faceBitmap: Bitmap): String? {
        val saved = getSavedFaces()
        for ((name, savedBmp) in saved) {
            if (areBitmapsSimilar(faceBitmap, savedBmp)) {
                return name
            }
        }
        return null
    }

    // Simple bitmap similarity (resize and compare pixels)
    private fun areBitmapsSimilar(bmp1: Bitmap, bmp2: Bitmap): Boolean {
        val size = 64 // Downscale for comparison
        val b1 = Bitmap.createScaledBitmap(bmp1, size, size, false)
        val b2 = Bitmap.createScaledBitmap(bmp2, size, size, false)
        val buffer1 = ByteBuffer.allocate(size * size * 4)
        val buffer2 = ByteBuffer.allocate(size * size * 4)
        b1.copyPixelsToBuffer(buffer1)
        b2.copyPixelsToBuffer(buffer2)
        return buffer1.array().contentEquals(buffer2.array())
    }

    // Bitmap to Base64
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.DEFAULT)
    }

    // Base64 to Bitmap
    private fun base64ToBitmap(base64: String): Bitmap? {
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }
} 