package com.r4sventures.motosaas

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.util.Rational

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var overlay: OverlayView
    private lateinit var startBackground: LinearLayout
    private lateinit var appIcon: ImageView

    private lateinit var btnOpenCamera: Button
    private lateinit var btnCloseCamera: Button
    private lateinit var btnStartDetect: Button
    private lateinit var btnStopDetect: Button
    private lateinit var btnPip: Button

    private var cameraProvider: ProcessCameraProvider? = null
    private var isCameraOpened = false
    private var isDetecting = false

    private lateinit var analyzer: TFLiteAnalyzer
    private val executor = Executors.newSingleThreadExecutor()

    private var currentModelType = 1
    private val CAMERA_REQUEST_CODE = 1001
    private val OVERLAY_PERMISSION_CODE = 2001

    private lateinit var controlPanel: LinearLayout   // 👈 เพิ่มบรรทัดนี้

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // UI mapping
        previewView = findViewById(R.id.previewView)
        overlay = findViewById(R.id.overlay)
        startBackground = findViewById(R.id.startBackground)
        appIcon = findViewById(R.id.appIcon)

        controlPanel = findViewById(R.id.controlPanel) // 👈 เพิ่ม

        btnOpenCamera = findViewById(R.id.btnOpenCamera)
        btnCloseCamera = findViewById(R.id.btnCloseCamera)
        btnStartDetect = findViewById(R.id.btnStart)
        btnStopDetect = findViewById(R.id.btnStop)
        btnPip = findViewById(R.id.btnPipStart)

        // โหลด model type
        val pref = getSharedPreferences("model_pref", Context.MODE_PRIVATE)
        currentModelType = pref.getInt("model_type", 1)

        // ปุ่มเปิดกล้อง
        btnOpenCamera.setOnClickListener {
            if (!checkCameraPermission()) return@setOnClickListener
            openCamera()
        }

        // ปุ่มปิดกล้อง
        btnCloseCamera.setOnClickListener {
            closeCamera()
        }

        // ปุ่มเริ่ม detect
        btnStartDetect.setOnClickListener {
            if (!checkOverlayPermission()) return@setOnClickListener
            if (!isCameraOpened) {
                Toast.makeText(this, "Please open the camera first!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startDetect()
        }

        // ปุ่มหยุด detect
        btnStopDetect.setOnClickListener {
            stopDetect()
        }

        btnPip.setOnClickListener {
            pipStartFn()
        }

        showStartUI()
    }

    // -----------------------------------------------------
    // UI STATE
    // -----------------------------------------------------

    private fun showCameraUI() {
        startBackground.visibility = View.GONE
        previewView.visibility = View.VISIBLE
        overlay.visibility = View.VISIBLE
    }

    private fun showStartUI() {
        previewView.visibility = View.GONE
        overlay.visibility = View.GONE
        startBackground.visibility = View.VISIBLE
    }

    // -----------------------------------------------------
    // CAMERA
    // -----------------------------------------------------

    private fun openCamera() {
        if (isCameraOpened) return

        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            cameraProvider = future.get()
            cameraProvider?.unbindAll()

            val preview = androidx.camera.core.Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            cameraProvider?.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                preview
            )

            isCameraOpened = true
            showCameraUI()

            Toast.makeText(this, "Camera Opened", Toast.LENGTH_SHORT).show()

        }, ContextCompat.getMainExecutor(this))
    }

    private fun closeCamera() {
        cameraProvider?.unbindAll()
        isCameraOpened = false

        stopDetect() // หยุด detect ด้วย
        overlay.clearDetections()

        showStartUI()

        Toast.makeText(this, "Camera Closed", Toast.LENGTH_SHORT).show()
    }

    // -----------------------------------------------------
    // PIP SYSTEM
    // --
    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(9, 16)) // สี่เหลี่ยม
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()

        if (isDetecting) {
            enterPipMode()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)

        if (isInPictureInPictureMode) {
            // PiP = content only
            controlPanel.visibility = View.GONE
            startBackground.visibility = View.GONE
            overlay.visibility = View.GONE
        } else {
            // Fullscreen = UI
            controlPanel.visibility = View.VISIBLE
            overlay.visibility = View.VISIBLE
        }
    }



    // -----------------------------------------------------
    // DETECT TFLITE
    // -----------------------------------------------------

    private fun startDetect() {
        if (isDetecting) return

        analyzer = TFLiteAnalyzer(this, currentModelType) { _, objects, imgW, imgH ->
            runOnUiThread {
                overlay.setDetections(objects, imgW, imgH)
            }
        }

        val analysis = ImageAnalysis.Builder().build().apply {
            setAnalyzer(executor, analyzer)
        }

        cameraProvider?.bindToLifecycle(
            this,
            CameraSelector.DEFAULT_FRONT_CAMERA,
            analysis
        )

        isDetecting = true
        Toast.makeText(this, "Detection Started", Toast.LENGTH_SHORT).show()

        //enterPipMode() // 👈 เพิ่มบรรทัดนี้
    }

    private fun pipStartFn() {
        enterPipMode() // 👈 เพิ่มบรรทัดนี้
    }
    private fun stopDetect() {
        if (!isDetecting) return

        cameraProvider?.unbindAll()

        val preview = androidx.camera.core.Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        cameraProvider?.bindToLifecycle(
            this,
            CameraSelector.DEFAULT_FRONT_CAMERA,
            preview
        )

        isDetecting = false
        Toast.makeText(this, "Detection Stopped", Toast.LENGTH_SHORT).show()
    }

    // -----------------------------------------------------
    // PERMISSION
    // -----------------------------------------------------

    private fun checkCameraPermission(): Boolean {
        if (checkSelfPermission(android.Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), CAMERA_REQUEST_CODE)
            return false
        }
        return true
    }

    private fun checkOverlayPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                Toast.makeText(this, "Grant overlay permission!", Toast.LENGTH_LONG).show()
                startActivityForResult(intent, OVERLAY_PERMISSION_CODE)
                return false
            }
        }
        return true
    }

    // -----------------------------------------------------
    // CAMERA PERMISSION RESULT
    // -----------------------------------------------------

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        if (requestCode == CAMERA_REQUEST_CODE &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            openCamera()
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
}
