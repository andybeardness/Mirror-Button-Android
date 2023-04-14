package com.beardness.mirrorbutton

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Matrix
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.beardness.mirrorbutton.ui.theme.MirrorButtonTheme
import jp.co.cyberagent.android.gpuimage.GPUImageView
import jp.co.cyberagent.android.gpuimage.filter.GPUImageGaussianBlurFilter
import java.util.concurrent.Executors


/**
 * Main activity of mirror button app
 */
@ExperimentalGetImage
class MainActivity : ComponentActivity() {

    private lateinit var preview: GPUImageView
    private var bitmap: Bitmap? = null
    private lateinit var converter: YuvToRgbConverter
    private var cameraProvider: ProcessCameraProvider? = null
    private val executor = Executors.newSingleThreadExecutor()

    companion object {
        private const val CAMERA_PERMISSION_ID = 1488
        private const val BLUR_SIZE = 2f
        private const val BUTTON_ALPHA = .65f
    }

    private fun allocateBitmap(width: Int, height: Int): Bitmap {
        val currentBitmap = bitmap ?: createBitmap(width = width, height = height)
        bitmap = currentBitmap
        return currentBitmap
    }

    private fun createBitmap(width: Int, height: Int): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setup()

        permission()
        compose()
        start()
    }

    private fun setup() {
        setupPreview()
        setupConverter()
    }

    private fun setupPreview() {
        preview = GPUImageView(this)
        preview.filter = GPUImageGaussianBlurFilter(BLUR_SIZE)
    }

    private fun setupConverter() {
        converter = YuvToRgbConverter(this)
    }

    private fun permission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_DENIED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_ID
            )
        }
    }

    private fun compose() {
        setContent {
            MirrorButtonTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(color = Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    MirrorButtonScreen()
                }
            }
        }
    }

    @Composable
    private fun MirrorButtonScreen() {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height = 80.dp)
                .padding(horizontal = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            MirrorButton()
        }
    }

    @Composable
    private fun MirrorButton() {
        val shape = RoundedCornerShape(percent = 50)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height = 96.dp)
                .shadow(shape = shape, elevation = 5.dp)
                .clip(shape = shape)
                .clickable { },
            contentAlignment = Alignment.Center,
        ) {
            CameraPreview()

            Spacer(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = Color
                            .White
                            .copy(
                                alpha = BUTTON_ALPHA
                            )
                    )
            )

            Text(
                text = "CLICK",
                fontSize = 56.sp,
                fontWeight = FontWeight.Thin,
                color = Color.White,
            )
        }
    }

    @Composable
    private fun CameraPreview() {
        AndroidView(
            modifier = Modifier
                .fillMaxSize(),
            factory = { _ ->
                preview
            }
        )
    }

    private fun start() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(
            Runnable {
                cameraProvider =
                    cameraProviderFuture
                        .get()
                        .apply {
                            bindToLifecycle(
                                this@MainActivity,
                                CameraSelector.DEFAULT_FRONT_CAMERA,
                            )
                        }

                startCamera()
            },
            ContextCompat.getMainExecutor(this),
        )
    }

    private fun startCamera() {
        if (!isPermissionsGranted() || cameraProvider == null) {
            return;
        }

        val imageAnalysis =
            ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

        imageAnalysis.setAnalyzer(
            executor,
            ImageAnalysis.Analyzer {
                val bitmap = allocateBitmap(width = it.width, height = it.height)

                val matrix =
                    Matrix()
                        .apply {
                            postRotate(-90f)
                            postScale(-1f, 1f)
                        }

                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, it.width, it.height, true)
                val rotatedBitmap = Bitmap.createBitmap(
                    scaledBitmap,
                    0,
                    0,
                    scaledBitmap.width,
                    scaledBitmap.height,
                    matrix,
                    true
                )

                converter.yuvToRgb(it.image!!, bitmap)

                it.close()

                preview.post {
                    preview.setImage(rotatedBitmap)
                }
            }
        )
        cameraProvider!!.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, imageAnalysis)
    }

    private fun isPermissionsGranted(): Boolean {
        return ContextCompat
            .checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
    }
}