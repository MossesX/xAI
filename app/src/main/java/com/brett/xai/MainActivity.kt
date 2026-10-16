package com.brett.xai

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.brett.xai.ui.AppViewModel
import com.brett.xai.ui.BrowserScreen
import com.brett.xai.ui.ChatScreen
import com.brett.xai.ui.KnowledgeScreen
import com.brett.xai.ui.LearnScreen
import com.brett.xai.ui.LocalOpenUrl
import com.brett.xai.ui.NoteScreen
import com.brett.xai.ui.NotesScreen
import com.brett.xai.ui.SettingsScreen
import com.brett.xai.ui.TopicScreen
import com.brett.xai.ui.TrackScreen
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
                fun openUrl(url: String) {
                    if (settings.openLinksInApp) nav.navigate("browser?url=" + Uri.encode(url))
                    else runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                }
                fun toChat() = nav.popBackStack("chat", inclusive = false)
                CompositionLocalProvider(LocalOpenUrl provides { url: String -> openUrl(url) }) {
                    NavHost(navController = nav, startDestination = "chat") {
                        composable("chat") {
                            ChatScreen(vm) { route ->
                                if (route == "browser") nav.navigate("browser?url=" + Uri.encode("https://www.youtube.com/results?search_query=ai+software+development"))
                                else nav.navigate(route)
                            }
                        }
                        composable("settings") { SettingsScreen(vm) { nav.popBackStack() } }
                        composable("knowledge") { KnowledgeScreen(vm) { nav.popBackStack() } }
                        composable("notes") { NotesScreen(vm, onBack = { nav.popBackStack() }, onOpen = { id -> nav.navigate("note/$id") }) }
                        composable("note/{id}") { e -> NoteScreen(vm, e.arguments?.getString("id").orEmpty()) { nav.popBackStack() } }
                        composable("learn") {
                            LearnScreen(
                                vm, onBack = { nav.popBackStack() },
                                openTrack = { nav.navigate("track/$it") },
                                openTopic = { nav.navigate("topic/$it") },
                                openUrl = ::openUrl,
                                toChat = { toChat() },
                            )
                        }
                        composable("track/{id}") { e ->
                            TrackScreen(vm, e.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() },
                                openTopic = { nav.navigate("topic/$it") }, toChat = { toChat() })
                        }
                        composable("topic/{id}") { e ->
                            TopicScreen(vm, e.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() },
                                openUrl = ::openUrl, toChat = { toChat() })
                        }
                        composable("browser?url={url}") { e ->
                            BrowserScreen(
                                vm, e.arguments?.getString("url").orEmpty().ifBlank { "https://www.youtube.com" },
                                onClose = { nav.popBackStack() },
                                onAskInChat = { toChat() },
                            )
                        }
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
