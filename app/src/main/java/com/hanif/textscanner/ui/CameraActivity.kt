package com.hanif.textscanner.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.hanif.textscanner.databinding.ActivityCameraBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraBinding
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private var flashEnabled = false
    private var camera: Camera? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()
        startCamera()
        setupButtons()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch (exc: Exception) {
                Log.e("CameraActivity", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun setupButtons() {
        binding.btnCapture.setOnClickListener { takePhoto() }

        binding.btnFlash.setOnClickListener {
            flashEnabled = !flashEnabled
            camera?.cameraControl?.enableTorch(flashEnabled)
            binding.btnFlash.setImageResource(
                if (flashEnabled) android.R.drawable.ic_menu_gallery
                else android.R.drawable.ic_menu_gallery
            )
            binding.btnFlash.alpha = if (flashEnabled) 1.0f else 0.6f
        }

        binding.btnClose.setOnClickListener { finish() }

        // Tap to focus
        binding.viewFinder.setOnTouchListener { _, event ->
            val factory = binding.viewFinder.meteringPointFactory
            val point = factory.createPoint(event.x, event.y)
            val action = FocusMeteringAction.Builder(point).build()
            camera?.cameraControl?.startFocusAndMetering(action)
            true
        }
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        binding.btnCapture.isEnabled = false
        binding.captureFlash.visibility = View.VISIBLE

        val photoFile = File(
            externalMediaDirs.firstOrNull() ?: filesDir,
            "IMG_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    binding.captureFlash.visibility = View.GONE
                    binding.btnCapture.isEnabled = true

                    val uri = FileProvider.getUriForFile(
                        this@CameraActivity,
                        "${packageName}.fileprovider",
                        photoFile
                    )

                    val resultIntent = Intent().apply {
                        putExtra("captured_image_uri", uri)
                    }
                    setResult(RESULT_OK, resultIntent)
                    finish()
                }

                override fun onError(exc: ImageCaptureException) {
                    binding.captureFlash.visibility = View.GONE
                    binding.btnCapture.isEnabled = true
                    Toast.makeText(this@CameraActivity, "Capture failed: ${exc.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
