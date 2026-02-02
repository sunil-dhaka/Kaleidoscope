package com.example.kaleidoscope

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

data class LineSegment(
    val start: Offset,
    val end: Offset,
    val color: Color,
    val strokeWidth: Float,
    val symmetry: Int  // Number of paired reflections
)

class KaleidoscopeViewModel : ViewModel() {
    // Renamed to 'symmetry' but represents pairs of reflections
    var symmetry = mutableStateOf(6)
        private set
    var currentStrokeWeight = mutableStateOf(4f)
        private set
    var currentHue = mutableStateOf(0f) // 0-360
        private set
    private var currentSaturation = mutableStateOf(0.8f) // 0-1
    private var currentBrightness = mutableStateOf(0.9f) // 0-1
    private var currentAlpha = mutableStateOf(0.8f) // 0-1

    private val _drawnPaths = mutableStateListOf<Path>()
    val drawnPaths: List<Path> get() = _drawnPaths

    private val _linesToDraw = mutableStateListOf<LineSegment>()
    val linesToDraw: List<LineSegment> get() = _linesToDraw

    var canvasSize = mutableStateOf(Offset(0f, 0f))
        private set

    // More modern, darker background color
    var backgroundColor = mutableStateOf(Color(0xFF121212))
        private set
        
    // Predefined vibrant colors
    private val vibrantColors = listOf(
        Color(0xFF00BCD4), // Cyan
        Color(0xFFE91E63), // Pink
        Color(0xFF4CAF50), // Green
        Color(0xFFFF9800), // Orange
        Color(0xFF673AB7), // Deep Purple
        Color(0xFF2196F3), // Blue
        Color(0xFFFFEB3B), // Yellow
        Color(0xFFFF5722)  // Deep Orange
    )

    init {
        // Start with a random vibrant color
        changeColor()
    }

    fun updateCanvasSize(width: Float, height: Float) {
        canvasSize.value = Offset(width, height)
    }

    fun addLine(start: Offset, end: Offset) {
        val hsv = floatArrayOf(
            currentHue.value,
            currentSaturation.value,
            currentBrightness.value
        )
        val color = Color.hsv(hsv[0], hsv[1], hsv[2]).copy(alpha = currentAlpha.value)

        _linesToDraw.add(LineSegment(start, end, color, currentStrokeWeight.value, symmetry.value))
        
        // Shift hue slightly for a nice gradient effect in continuous drawing
        currentHue.value = (currentHue.value + 0.3f) % 360f
    }

    fun clearCanvas() {
        _linesToDraw.clear()
        // Change background color when clearing for a fresh experience
        backgroundColor.value = generateBackgroundColor()
    }

    fun changeColor() {
        // Either use a predefined vibrant color or generate a random one
        if (Random.nextBoolean()) {
            // Select a vibrant color from our palette
            val randomColor = vibrantColors[Random.nextInt(vibrantColors.size)]
            // Extract HSV values from the color
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(randomColor.toArgb(), hsv)
            currentHue.value = hsv[0]
            currentSaturation.value = hsv[1]
            currentBrightness.value = hsv[2]
        } else {
            // Generate a random color with high saturation and brightness for vibrancy
            currentHue.value = Random.nextFloat() * 360f
            currentSaturation.value = 0.7f + Random.nextFloat() * 0.3f
            currentBrightness.value = 0.8f + Random.nextFloat() * 0.2f
        }
    }
    
    private fun generateBackgroundColor(): Color {
        // Generate dark, muted background colors that complement the vibrant drawing colors
        return Color(
            red = Random.nextInt(5, 30) / 255f,
            green = Random.nextInt(5, 30) / 255f,
            blue = Random.nextInt(20, 45) / 255f,
            alpha = 1f
        )
    }

    // Direct brush size setter for slider control
    fun setBrushSize(size: Float) {
        currentStrokeWeight.value = size.coerceIn(1f, 30f)
    }

    fun increaseBrushSize() {
        currentStrokeWeight.value = min(currentStrokeWeight.value + 1f, 30f)
    }

    fun decreaseBrushSize() {
        currentStrokeWeight.value = (currentStrokeWeight.value - 1f).coerceAtLeast(1f)
    }

    // Set the number of pairs (formerly symmetry)
    fun setSymmetry(newPairs: Int) {
        if (newPairs >= 2) {
            symmetry.value = newPairs
        }
    }

    fun saveCanvasAsBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(
            canvasSize.value.x.toInt(),
            canvasSize.value.y.toInt(),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        canvas.drawColor(backgroundColor.value.toArgb()) // Set background color for the saved image
        
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
        }

        _linesToDraw.forEach { segment ->
            paint.color = segment.color.toArgb()
            paint.strokeWidth = segment.strokeWidth
            paint.strokeCap = android.graphics.Paint.Cap.ROUND

            val angleIncrement = 360f / segment.symmetry
            val centerX = canvasSize.value.x / 2f
            val centerY = canvasSize.value.y / 2f

            for (i in 0 until segment.symmetry) {
                canvas.save()
                canvas.translate(centerX, centerY)
                canvas.rotate(angleIncrement * i)

                // Adjust coordinates relative to center for drawing
                val mx = segment.start.x - centerX
                val my = segment.start.y - centerY
                val pmx = segment.end.x - centerX
                val pmy = segment.end.y - centerY

                canvas.drawLine(mx, my, pmx, pmy, paint)

                // Reflection
                canvas.save()
                canvas.scale(1f, -1f)
                canvas.drawLine(mx, my, pmx, pmy, paint)
                canvas.restore() // Restore from scale

                canvas.restore() // Restore from rotate and translate
            }
        }
        return bitmap
    }
} 