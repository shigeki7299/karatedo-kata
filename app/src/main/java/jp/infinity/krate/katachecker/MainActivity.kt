package jp.infinity.krate.katachecker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import jp.infinity.krate.katachecker.ui.CompareScreen
import jp.infinity.krate.katachecker.ui.theme.KarateKataTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KarateKataTheme {
                CompareScreen()
            }
        }
    }
}
