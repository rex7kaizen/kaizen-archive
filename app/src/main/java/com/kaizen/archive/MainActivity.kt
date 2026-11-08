package com.kaizen.archive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.kaizen.archive.ui.KaizenArchiveTheme
import com.kaizen.archive.ui.KaizenArchiveScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KaizenArchiveTheme {
                KaizenArchiveScreen()
            }
        }
    }
}
