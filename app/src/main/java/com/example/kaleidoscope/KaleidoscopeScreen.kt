package com.example.kaleidoscope

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.io.OutputStream
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KaleidoscopeScreen(viewModel: KaleidoscopeViewModel = viewModel()) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val pairs by viewModel.symmetry
    val currentStrokeWeight by viewModel.currentStrokeWeight
    val linesToDraw by remember { derivedStateOf { viewModel.linesToDraw } }
    val backgroundColor by viewModel.backgroundColor
    
    // Animate background color changes
    val animatedBackgroundColor by animateColorAsState(
        targetValue = backgroundColor,
        animationSpec = tween(durationMillis = 500),
        label = "backgroundColor"
    )

    var lastPosition by remember { mutableStateOf<Offset?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Kaleidoscope", 
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    ) 
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                actions = {
                    // Save Button - Text only approach to avoid icon reference issues
                    Button(
                        onClick = {
                            saveBitmapToGallery(context, viewModel.saveCanvasAsBitmap(), "KaleidoscopeArt")
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Image Saved to Gallery")
                            }
                        },
                        modifier = Modifier.padding(horizontal = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                    
                    // Clear/Delete Button - Using Clear icon which is definitely available
                    IconButton(
                        onClick = { viewModel.clearCanvas() },
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(48.dp)
                    ) {
                        Icon(
                            Icons.Filled.Clear, 
                            contentDescription = "Clear Canvas",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        },
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Improved Drawing Canvas with proper padding
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    // Add a background container with shadow and rounded corners
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .shadow(4.dp, RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp))
                            .background(animatedBackgroundColor)
                    )
                    
                    // The actual canvas for drawing, without clipping to ensure no cut-off
                    Canvas(
                        modifier = Modifier
                            .matchParentSize()
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        lastPosition = offset
                                        viewModel.addLine(offset, offset)
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val currentPosition = change.position
                                        lastPosition?.let {
                                            viewModel.addLine(it, currentPosition)
                                        }
                                        lastPosition = currentPosition
                                    },
                                    onDragEnd = {
                                        lastPosition = null
                                    }
                                )
                            }
                    ) {
                        viewModel.updateCanvasSize(size.width, size.height)
                        
                        // We don't need to draw the background here anymore
                        // as we've set it on the Box background
                        
                        linesToDraw.forEach { line ->
                            val angle = 360f / line.symmetry
                            val drawColor = line.color
                            val strokeWidth = line.strokeWidth

                            val centerX = size.width / 2
                            val centerY = size.height / 2

                            val mx = line.start.x
                            val my = line.start.y
                            val pmx = line.end.x
                            val pmy = line.end.y

                            for (i in 0 until line.symmetry) {
                                rotate(degrees = angle * i, pivot = Offset(centerX, centerY)) {
                                    translate(left = centerX, top = centerY) {
                                        val relativeMx = mx - centerX
                                        val relativeMy = my - centerY
                                        val relativePmx = pmx - centerX
                                        val relativePmy = pmy - centerY

                                        drawLine(
                                            color = drawColor,
                                            start = Offset(relativeMx, relativeMy),
                                            end = Offset(relativePmx, relativePmy),
                                            strokeWidth = strokeWidth,
                                            cap = StrokeCap.Round
                                        )

                                        scale(scaleX = 1f, scaleY = -1f, pivot = Offset.Zero) {
                                            drawLine(
                                                color = drawColor,
                                                start = Offset(relativeMx, relativeMy),
                                                end = Offset(relativePmx, relativePmy),
                                                strokeWidth = strokeWidth,
                                                cap = StrokeCap.Round
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Color change floating button - Using text instead of icon
                    FloatingActionButton(
                        onClick = { viewModel.changeColor() },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                            .size(56.dp),
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    Color.hsv(
                                        viewModel.currentHue.value,
                                        0.8f,
                                        0.9f
                                    )
                                )
                        )
                    }
                }

                // Controls Panel
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Brush Size Control
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Brush: ${currentStrokeWeight.toInt()}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(90.dp)
                            )
                            
                            Slider(
                                value = currentStrokeWeight,
                                onValueChange = { newValue -> viewModel.setBrushSize(newValue) },
                                valueRange = 1f..30f,
                                steps = 29,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Pairs Controls (formerly Symmetry)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Pair: $pairs",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(90.dp)
                            )
                            
                            Slider(
                                value = pairs.toFloat(),
                                onValueChange = { newValue -> viewModel.setSymmetry(newValue.roundToInt()) },
                                valueRange = 2f..9f,
                                steps = 6,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    )
}

fun saveBitmapToGallery(context: Context, bitmap: Bitmap, displayName: String) {
    val imageCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }

    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }

    val resolver = context.contentResolver
    var uri = resolver.insert(imageCollection, contentValues)

    uri?.let {
        try {
            val outputStream: OutputStream? = resolver.openOutputStream(it)
            outputStream?.use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(it, contentValues, null, null)
            }
            Toast.makeText(context, "Image saved to Gallery", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Error saving image: ${e.message}", Toast.LENGTH_LONG).show()
            // Clean up entry if save failed
            resolver.delete(it, null, null)
        }
    } ?: run {
        Toast.makeText(context, "Error creating MediaStore entry.", Toast.LENGTH_LONG).show()
    }
} 