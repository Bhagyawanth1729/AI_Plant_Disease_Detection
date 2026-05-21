package com.example.plant_disease_detection

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.plant_disease_detection.databinding.ActivitySettingsBinding
import com.example.plant_disease_detection.utils.UserManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        val email = UserManager.getCurrentUserEmail()
        binding.signedInLabel.text =
            if (email.isNullOrBlank()) {
                "You are not logged in. Chat history uses guest storage on this device."
            } else {
                "Signed in as: $email"
            }

        binding.logoutButton.setOnClickListener {
            if (UserManager.getCurrentUserEmail().isNullOrBlank()) {
                MaterialAlertDialogBuilder(this)
                    .setMessage("You are not logged in.")
                    .setPositiveButton("OK", null)
                    .show()
                return@setOnClickListener
            }
            MaterialAlertDialogBuilder(this)
                .setTitle("Log out?")
                .setMessage("You will return to the welcome screen.")
                .setPositiveButton("Log out") { _, _ ->
                    UserManager.logoutAndGoHome(this)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        val email = UserManager.getCurrentUserEmail()
        binding.signedInLabel.text =
            if (email.isNullOrBlank()) {
                "You are not logged in. Chat history uses guest storage on this device."
            } else {
                "Signed in as: $email"
            }
    }
}

