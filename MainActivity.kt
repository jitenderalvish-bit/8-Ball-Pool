package com.billiards.analyzer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val intent = Intent(this, AnalysisActivity::class.java)
            intent.putExtra(AnalysisActivity.EXTRA_IMAGE_URI, uri.toString())
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<android.widget.Button>(R.id.btnAnalyze).setOnClickListener {
            pickImage.launch("image/*")
        }
    }
}
