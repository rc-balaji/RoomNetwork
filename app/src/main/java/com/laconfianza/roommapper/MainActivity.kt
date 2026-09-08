package com.laconfianza.roommapper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.laconfianza.roommapper.ui.RoomMapperApp
import com.laconfianza.roommapper.ui.RoomMapperTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RoomMapperTheme {
                RoomMapperApp()
            }
        }
    }
}
