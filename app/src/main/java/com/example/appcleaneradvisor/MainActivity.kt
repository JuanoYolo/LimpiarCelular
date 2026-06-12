package com.example.appcleaneradvisor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.appcleaneradvisor.ui.AppCleanerAdvisorApp
import com.example.appcleaneradvisor.ui.AppCleanerViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: AppCleanerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppCleanerAdvisorApp(viewModel = viewModel)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshUsagePermission()
    }
}
