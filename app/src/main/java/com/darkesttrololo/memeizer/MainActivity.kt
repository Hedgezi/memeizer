package com.darkesttrololo.memeizer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.darkesttrololo.memeizer.ui.MemeizerAppUi
import com.darkesttrololo.memeizer.ui.theme.MemeizerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as MemeizerApp).container

        setContent {
            MemeizerTheme {
                MemeizerAppUi(container = container)
            }
        }
    }
}
