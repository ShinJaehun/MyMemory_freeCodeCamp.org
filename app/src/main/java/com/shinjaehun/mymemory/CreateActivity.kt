package com.shinjaehun.mymemory

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.Log
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.shinjaehun.mymemory.models.BoardSize
import com.shinjaehun.mymemory.utils.BitmapScaler
import com.shinjaehun.mymemory.utils.EXTRA_BOARD_SIZE
import com.shinjaehun.mymemory.utils.isPermissionGranted
import com.shinjaehun.mymemory.utils.requestPermission
import java.io.ByteArrayOutputStream

class CreateActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "CreateActivity"
        private const val PICK_PHOTO_CODE = 655
        private const val READ_EXTERNAL_PHOTO_CODE = 248
        private const val READ_PHOTO_PERMISSION = android.Manifest.permission.READ_EXTERNAL_STORAGE
        private const val MIN_GAME_NAME_LENGTH = 3
        private const val MAX_GAME_NAME_LENGTH = 14
    }

    private lateinit var adapter: ImagePickerAdapter
    private lateinit var rvImagePicker: RecyclerView
    private lateinit var etGameName: EditText
    private lateinit var btnSave: Button

    private lateinit var boardSize: BoardSize
    private var numImagesRequired = -1

    private val chosenImageUris = mutableListOf<Uri>()

    private val storage = Firebase.storage
    private val db = Firebase.firestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create)

        rvImagePicker = findViewById(R.id.rvImagePicker)
        etGameName = findViewById(R.id.etGameName)
        btnSave = findViewById(R.id.btnSave)

        supportActionBar?.setDisplayHomeAsUpEnabled(true) // 아, 요건 뭐지?
        boardSize = intent.getSerializableExtra(EXTRA_BOARD_SIZE) as BoardSize
        numImagesRequired = boardSize.getNumPairs()
        supportActionBar?.title = "Choose pics (0 / ${numImagesRequired})"

        // etGameName 필터링/textChangedListener
        etGameName.filters = arrayOf(InputFilter.LengthFilter(MAX_GAME_NAME_LENGTH))
        etGameName.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(p0: Editable?) {
                btnSave.isEnabled = shouldEnableSaveButton()
            }
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
        })

        // btnSave
        btnSave.setOnClickListener {
            saveDataToFireBase()
        }

        // adapter
        adapter = ImagePickerAdapter(this, chosenImageUris, boardSize, object: ImagePickerAdapter.ImageClickListener{
            override fun onPlaceholderClicked() {
                if(isPermissionGranted(this@CreateActivity, READ_PHOTO_PERMISSION)) {
                    launchIntentForPhotos()
                } else {
                    requestPermission(this@CreateActivity, READ_PHOTO_PERMISSION, READ_EXTERNAL_PHOTO_CODE) // onRequestPermissionResult()
                }
            }
        })
        rvImagePicker.adapter = adapter
        rvImagePicker.setHasFixedSize(true)
        rvImagePicker.layoutManager = GridLayoutManager(this, boardSize.getWidth())
    }

    // requestPermission() 결과 처리
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if(requestCode == READ_EXTERNAL_PHOTO_CODE) {
            if(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) { // 권한이 존재하면
                launchIntentForPhotos()
            } else {
                Toast.makeText(this, "In order to create a custom game, you need to provide access to your photos", Toast.LENGTH_LONG).show()
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if(item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    // startActivityResult()의 결과로 받아온 uri 처리
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode != PICK_PHOTO_CODE || resultCode != Activity.RESULT_OK || data == null) {
            Log.w(TAG, "Dod not get data back from the launched activity, user likely canceled flow")
            return
        }
        val selectedUri: Uri? = data.data // single image
        val clipData: ClipData? = data.clipData // multi images
        if (clipData != null) { // multi image처리
            Log.i(TAG, "clipData numImages ${clipData.itemCount}: $clipData")
            for (i in 0 until clipData.itemCount) {
                val clipItem = clipData.getItemAt(i)
                if (chosenImageUris.size < numImagesRequired) {
                    chosenImageUris.add(clipItem.uri)
                }
            }
        } else if (selectedUri != null) { // single image 처리
            Log.i(TAG, "data: $selectedUri")
            chosenImageUris.add(selectedUri)
        }
        adapter.notifyDataSetChanged() // adapter 갱신
        supportActionBar?.title="Choose pics (${chosenImageUris.size} / $numImagesRequired)" // bar title에 선택한 이미지 표시
        btnSave.isEnabled = shouldEnableSaveButton() // save btn 활성화 여부 확인
    }

    // 이미지를 FB로 전달?
    private fun saveDataToFireBase() {
        val customGameName = etGameName.text.toString()
        Log.i(TAG, "saveDataToFirebase")
        var didEncounterError = false
        val uploadedImageUrls = mutableListOf<String>()
        for ((index, photoUri) in chosenImageUris.withIndex()) {
            val imageByteArray = getImageByteArray(photoUri) // uri to byte stream
            val filePath = "images/$customGameName/${System.currentTimeMillis()}-${index}.jpg"
            val photoReference = storage.reference.child(filePath) // file path에 byte stream을 이런 식으로 연결
            photoReference.putBytes(imageByteArray) // firebase api : upload to storage
                .continueWithTask{ photoUploadTask ->
                    Log.i(TAG, "Uploaded bytes: ${photoUploadTask.result?.bytesTransferred}")
                    photoReference.downloadUrl
                }.addOnCompleteListener{ downloadUrlTask ->
                    if (!downloadUrlTask.isSuccessful) {
                        Log.e(TAG, "Exception with Firebase storage", downloadUrlTask.exception)
                        Toast.makeText(this, "Failed to upload image", Toast.LENGTH_SHORT).show()
                        didEncounterError = true
                        return@addOnCompleteListener
                    }
                    if (didEncounterError) {
                        return@addOnCompleteListener
                    }
                    val downloadUrl = downloadUrlTask.result.toString()
                    uploadedImageUrls.add(downloadUrl)
                    Log.i(TAG, "finished uploading $photoUri, num uploaded ${uploadedImageUrls.size}")
                    if (uploadedImageUrls.size == chosenImageUris.size) { // if upload finish,
                        handleAllImagesUploaded(customGameName, uploadedImageUrls)
                    }
                }
        }
    }

    private fun handleAllImagesUploaded(gameName: String, imageUrls: MutableList<String>) {
        // upload this info to firestore
    }

    // 이미지 uri 전달해서 바이트 배열로 받아오기
    private fun getImageByteArray(photoUri: Uri): ByteArray {
        // 버전에 따라 이미지 처리 방법이 다름
        val originalBitmap = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P){
            val source = ImageDecoder.createSource(contentResolver, photoUri)
            ImageDecoder.decodeBitmap(source)
        } else {
            MediaStore.Images.Media.getBitmap(contentResolver, photoUri)
        }
        // 이미지 압축 처리(JPEG 형식으로...scale down)
        Log.i(TAG, "Original width ${originalBitmap.width} and height ${originalBitmap.height}")
        val scaledBitmap = BitmapScaler.scaleToFitHeight(originalBitmap, 250)
        Log.i(TAG, "Scaled width ${scaledBitmap.width} and ${scaledBitmap.height}")
        val byteOutputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 60, byteOutputStream)
        return byteOutputStream.toByteArray()
    }

    // save btn 활성화 여부 확인
    private fun shouldEnableSaveButton(): Boolean {
        // check if we should enable the save button or not
        if (chosenImageUris.size != numImagesRequired) {
            return false
        }
        if (etGameName.text.isBlank() || etGameName.text.length < MIN_GAME_NAME_LENGTH) {
            return false
        }
        return true
    }

    // photo 선택할 프로그램을 implicit intent로 실행
    private fun launchIntentForPhotos() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true) // android permission 모델에 대해 더 공부할 것! manifest 수정이 필요
        startActivityForResult(Intent.createChooser(intent, "Choose pics"), PICK_PHOTO_CODE)
    }
}