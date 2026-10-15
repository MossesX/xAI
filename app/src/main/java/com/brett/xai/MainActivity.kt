package com.brett.xai

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.brett.xai.ui.AppViewModel
import com.brett.xai.ui.ChatScreen
import com.brett.xai.ui.KnowledgeScreen
import com.brett.xai.ui.NoteScreen
import com.brett.xai.ui.NotesScreen
import com.brett.xai.ui.SettingsScreen
import com.brett.xai.ui.XaiTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleShare(intent)
        setContent {
            val settings by vm.store.settings.collectAsState()
            XaiTheme(settings.theme) {
                val nav = rememberNavController()
                NavHost(navController = nav, startDestination = "chat") {
                    composable("chat") { ChatScreen(vm) { route -> nav.navigate(route) } }
                    composable("settings") { SettingsScreen(vm) { nav.popBackStack() } }
                    composable("knowledge") { KnowledgeScreen(vm) { nav.popBackStack() } }
                    composable("notes") { NotesScreen(vm, onBack = { nav.popBackStack() }, onOpen = { id -> nav.navigate("note/$id") }) }
                    composable("note/{id}") { entry ->
                        NoteScreen(vm, entry.arguments?.getString("id").orEmpty()) { nav.popBackStack() }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** "Share → xAI" from any app (an error log, a README, a link) lands in the input box. */
    private fun handleShare(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { vm.pendingInput.value = it }
        }
    }
}
