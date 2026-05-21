package com.example.plant_disease_detection

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.plant_disease_detection.adapters.ScanHistoryAdapter
import com.example.plant_disease_detection.databinding.ActivityChatHistoryBinding
import com.example.plant_disease_detection.utils.ChatHistoryManager
import com.example.plant_disease_detection.utils.ScanHistoryManager
import com.example.plant_disease_detection.utils.UserManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout

class ChatHistoryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityChatHistoryBinding
    private lateinit var chatUserKey: String
    private lateinit var scanAdapter: ScanHistoryAdapter
    private var selectedTab = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        chatUserKey = UserManager.getCurrentUserEmail() ?: "guest"
        binding.accountLabel.text =
            if (chatUserKey == "guest") "Signed in as: guest (log in to save under your account)"
            else "Signed in as: $chatUserKey"

        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        scanAdapter = ScanHistoryAdapter()
        binding.scanRecycler.layoutManager = LinearLayoutManager(this)
        binding.scanRecycler.adapter = scanAdapter

        binding.historyTabs.addTab(binding.historyTabs.newTab().setText("AI chat"))
        binding.historyTabs.addTab(binding.historyTabs.newTab().setText("Scans"))

        binding.historyTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                selectedTab = tab?.position ?: 0
                updateVisiblePanel()
                updateClearButtonLabel()
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        updateVisiblePanel()
        updateClearButtonLabel()
        refreshChat()
        refreshScans()

        binding.clearHistoryButton.setOnClickListener {
            if (selectedTab == 0) {
                MaterialAlertDialogBuilder(this)
                    .setTitle("Clear chat history?")
                    .setMessage("This removes saved AI chat for this account on this device.")
                    .setPositiveButton("Clear") { _, _ ->
                        ChatHistoryManager.clear(chatUserKey)
                        refreshChat()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else {
                MaterialAlertDialogBuilder(this)
                    .setTitle("Clear scan history?")
                    .setMessage("This removes saved detection results and thumbnails for this account on this device.")
                    .setPositiveButton("Clear") { _, _ ->
                        ScanHistoryManager.clear(chatUserKey)
                        refreshScans()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        chatUserKey = UserManager.getCurrentUserEmail() ?: "guest"
        binding.accountLabel.text =
            if (chatUserKey == "guest") "Signed in as: guest (log in to save under your account)"
            else "Signed in as: $chatUserKey"
        refreshChat()
        refreshScans()
    }

    private fun updateVisiblePanel() {
        when (selectedTab) {
            0 -> {
                binding.panelChat.visibility = View.VISIBLE
                binding.panelScans.visibility = View.GONE
            }
            else -> {
                binding.panelChat.visibility = View.GONE
                binding.panelScans.visibility = View.VISIBLE
            }
        }
    }

    private fun updateClearButtonLabel() {
        binding.clearHistoryButton.text =
            if (selectedTab == 0) "Clear chat history" else "Clear scan history"
    }

    private fun refreshChat() {
        val text = ChatHistoryManager.load(chatUserKey)
        binding.historyText.text =
            if (text.isBlank()) {
                "No messages yet. Run a scan, tap Ask AI for Treatment, then chat here will appear."
            } else {
                text
            }
        binding.historyScroll.post {
            binding.historyScroll.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun refreshScans() {
        val list = ScanHistoryManager.loadList(chatUserKey)
        scanAdapter.submit(list)
        val showEmpty = list.isEmpty()
        binding.emptyScans.visibility = if (showEmpty) View.VISIBLE else View.GONE
        binding.scanRecycler.visibility = if (showEmpty) View.GONE else View.VISIBLE
    }
}

