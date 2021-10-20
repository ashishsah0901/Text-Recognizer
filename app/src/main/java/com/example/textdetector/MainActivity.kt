package com.example.textdetector

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.example.textdetector.databinding.ActivityMainBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import org.opencv.android.*
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import kotlin.Exception

class MainActivity : AppCompatActivity(), CameraBridgeViewBase.CvCameraViewListener2 {

    private lateinit var binding: ActivityMainBinding
    private lateinit var mat: Mat
    private var isFront = 1
    private var bitmap: Bitmap? = null
    private val baseLoaderCallback = object : BaseLoaderCallback(this) {
        override fun onManagerConnected(status: Int) {
            when(status) {
                LoaderCallbackInterface.SUCCESS -> {
                    binding.cameraView.enableView()
                }
                else -> {
                    super.onManagerConnected(status)
                }
            }
        }
    }
    private lateinit var textRecognizer: TextRecognizer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)== PackageManager.PERMISSION_DENIED||
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA,Manifest.permission.READ_EXTERNAL_STORAGE), 111)
        }else{
            binding.cameraView.setCameraPermissionGranted()
        }
        setContentView(binding.root)

        binding.cameraView.visibility = CameraBridgeViewBase.VISIBLE
        binding.cameraView.setCvCameraViewListener(this)
        binding.cameraView.setCameraIndex(isFront)

        binding.flipCamera.setOnClickListener {
            isFront = if(isFront == 1) 0 else 1
            binding.cameraView.disableView()
            binding.cameraView.setCameraIndex(isFront)
            binding.cameraView.enableView()
        }

        binding.captureImage.setOnClickListener {
//            if(isCamera) {
                Core.flip(mat.t(), mat, 1)
                bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
                Utils.matToBitmap(mat, bitmap)
//                isCamera = false
                toggleTranslateButton(true)
                toggleGalleryButton(false)
                toggleCameraButtons(false)
                toggleCameraVisibility(false)
                toggleImageVisibility(true)
//            }
        }

        binding.convertToText.setOnClickListener {
            if(bitmap != null) {
                toggleCameraVisibility(false)
                toggleScrollViewVisibility(true)
                val image = InputImage.fromBitmap(bitmap!!,0)
                textRecognizer.process(image)
                    .addOnSuccessListener {
                        binding.textDetected.text = it.text
                    }
                    .addOnFailureListener {
                        it.printStackTrace()
                    }
                toggleTranslateButton(false)
                toggleScrollViewVisibility(true)
                toggleCameraButtons(false)
                toggleGalleryButton(false)
                toggleImageVisibility(false)
            }
        }

        binding.openGallery.setOnClickListener {
            val intent = Intent()
            intent.type = "image/*"
            intent.action = Intent.ACTION_GET_CONTENT
            startActivityForResult(Intent.createChooser(intent, "Select Image"),111)
        }

        if(bitmap == null) {
            toggleTranslateButton(false)
        }

        textRecognizer = TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if(requestCode == 111) {
            if (grantResults.isNotEmpty()
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                binding.cameraView.setCameraPermissionGranted()
            } else {
                Toast.makeText(this, "Camera permission required.", Toast.LENGTH_LONG).show()
                this.finish()
            }
        } else{
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == 111 && resultCode == RESULT_OK && data != null) {
            val uri = data.data
            uri?.let {
                try {
                    bitmap = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, it))
                    } else {
                        MediaStore.Images.Media.getBitmap(contentResolver, it)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            toggleCameraVisibility(false)
            toggleScrollViewVisibility(false)
            toggleCameraButtons(false)
            toggleImageVisibility(true)
            toggleTranslateButton(true)
        }
    }

    private fun toggleCameraVisibility(isVisible: Boolean) {
        binding.cameraView.isVisible = isVisible
        if(isVisible) {
            binding.cameraView.enableView()
        } else {
            binding.cameraView.disableView()
        }
        binding.galleryImage.setImageBitmap(bitmap)
    }

    private fun toggleScrollViewVisibility(isVisible: Boolean) {
        binding.scrollView.isVisible = isVisible
    }

    private fun toggleImageVisibility(isVisible: Boolean) {
        binding.galleryImage.isVisible = isVisible
    }
    
    private fun toggleCameraButtons(isClickable: Boolean) {
        binding.captureImage.isClickable = isClickable
        binding.flipCamera.isClickable = isClickable
    }
    
    private fun toggleTranslateButton(isClickable: Boolean) {
        binding.convertToText.isClickable = isClickable
    }

    private fun toggleGalleryButton(isClickable: Boolean) {
        binding.openGallery.isClickable = isClickable
    }

    override fun onResume() {
        super.onResume()
        if(OpenCVLoader.initDebug()) {
            baseLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS)
        }else{
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0,this, baseLoaderCallback)
        }
    }

    override fun onCameraViewStarted(width: Int, height: Int) {
        mat = Mat(width, height, CvType.CV_8UC4)
    }

    override fun onCameraViewStopped() {
        mat.release()
    }

    override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame?): Mat {
        mat = inputFrame!!.rgba()
        if(isFront == 1) {
            Core.rotate(mat, mat, Core.ROTATE_180)
            Core.flip(mat, mat,0)
        }
        return mat
    }

    override fun onPause() {
        super.onPause()
        binding.cameraView.disableView()
    }

    override fun onBackPressed() {
        if(bitmap != null) {
            bitmap = null
            toggleTranslateButton(false)
            toggleGalleryButton(true)
            toggleCameraButtons(true)
            toggleScrollViewVisibility(false)
            toggleImageVisibility(false)
            toggleCameraVisibility(true)
        } else {
            super.onBackPressed()
        }
    }

}