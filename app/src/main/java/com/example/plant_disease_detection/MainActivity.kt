package com.example.plant_disease_detection

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.plant_disease_detection.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding


    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val imageUri: Uri? = result.data?.data
            if (imageUri != null) {

                val intent = Intent(this, DetectionActivity::class.java).apply {
                    putExtra(DetectionActivity.EXTRA_IMAGE_PATH, imageUri.toString())
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "Failed to get image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
        setupBottomNavigation()
    }

    private fun setupClickListeners() {
        binding.scanButton.setOnClickListener {
            startActivity(Intent(this, CameraActivity::class.java))
        }

        binding.uploadButton.setOnClickListener {
            openGallery()
        }

        binding.searchInput.setOnClickListener {

            Toast.makeText(this, "Search feature coming soon!", Toast.LENGTH_SHORT).show()
        }

        binding.exampleHealthyCard.setOnClickListener {
            openExampleScan(R.drawable.healthy_plant, "Example: Healthy plant")
        }
        binding.exampleBlightCard.setOnClickListener {
            openExampleScan(R.drawable.blight_plant, "Example: Blight")
        }
        binding.exampleFungalCard.setOnClickListener {
            openExampleScan(R.drawable.fungal_plant, "Example: Fungal infection")
        }
    }

    private fun openExampleScan(drawableRes: Int, sourceLabel: String) {
        val uri = Uri.parse("android.resource://$packageName/$drawableRes")
        startActivity(Intent(this, DetectionActivity::class.java).apply {
            putExtra(DetectionActivity.EXTRA_IMAGE_PATH, uri.toString())
            putExtra(DetectionActivity.EXTRA_SOURCE_LABEL, sourceLabel)
        })
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryLauncher.launch(intent)
    }

    private fun setupBottomNavigation() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {

                    true
                }
                R.id.navigation_history -> {
                    startActivity(Intent(this, ChatHistoryActivity::class.java))
                    true
                }
                R.id.navigation_community -> {
                    Toast.makeText(this, "Community feature coming soon!", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.navigation_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }
}