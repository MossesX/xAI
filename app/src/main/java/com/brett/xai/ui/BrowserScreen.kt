@file:OptIn(ExperimentalMaterial3Api::class)

package com.brett.xai.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.brett.xai.data.AppJson
import com.brett.xai.data.Links
import com.brett.xai.data.SavedItem
import kotlinx.serialization.builtins.serializer

/**
 * Built-in browser + video player. YouTube, docs and courses open here so you never leave the app.
 * Supports fullscreen video, rotation, bookmarks, history and "Ask xAI about this page".
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(vm: AppViewModel, startUrl: String, onClose: () -> Unit, onAskInChat: () -> Unit) {
    val ctx = LocalContext.current
    val learnState by vm.learn.state.collectAsState()
    var url by remember { mutableStateOf(startUrl) }
    var title by remember { mutableStateOf("") }
    var progress by remember { mutableIntStateOf(0) }
    var canBack by remember { mutableStateOf(false) }
    var canForward by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var addr by remember { mutableStateOf("") }
    var fullscreen by remember { mutableStateOf<View?>(null) }
    var fullscreenCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var menu by remember { mutableStateOf(false) }

    val web = remember {
        WebView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        }
    }

    DisposableEffect(web) {
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url
                if (u.scheme == "http" || u.scheme == "https") return false
                // intent://, market://, mailto: … hand to Android
                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                return true
            }

            override fun onPageStarted(view: WebView, u: String, favicon: Bitmap?) {
                url = u; canBack = view.canGoBack(); canForward = view.canGoForward()
            }

            override fun onPageFinished(view: WebView, u: String) {
                url = u; title = view.title.orEmpty(); canBack = view.canGoBack(); canForward = view.canGoForward()
                vm.learn.addHistory(SavedItem(title = title.ifBlank { u }, url = u, kind = if (Links.isVideo(u)) "video" else "page"))
            }

            override fun doUpdateVisitedHistory(view: WebView, u: String, isReload: Boolean) {
                url = u; canBack = view.canGoBack(); canForward = view.canGoForward()
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) { progress = newProgress }
            override fun onReceivedTitle(view: WebView, t: String?) { title = t.orEmpty() }
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                fullscreen = view; fullscreenCallback = callback
            }
            override fun onHideCustomView() {
                fullscreen = null; fullscreenCallback = null
            }
        }
        if (web.url == null) web.loadUrl(startUrl)
        web.onResume()
        onDispose {
            web.onPause()
            web.stopLoading()
            web.destroy()
        }
    }

    // Hide status/navigation bars while a video is fullscreen
    androidx.compose.runtime.LaunchedEffect(fullscreen) {
        val activity = ctx as? android.app.Activity ?: return@LaunchedEffect
        val ctl = androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        if (fullscreen != null) {
            ctl.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctl.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        } else ctl.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
    }

    BackHandler {
        when {
            fullscreen != null -> { fullscreenCallback?.onCustomViewHidden(); fullscreen = null }
            editing -> editing = false
            web.canGoBack() -> web.goBack()
            else -> onClose()
        }
    }

    fun go(input: String) {
        val t = input.trim()
        val target = when {
            t.startsWith("http://") || t.startsWith("https://") -> t
            t.contains('.') && !t.contains(' ') -> "https://$t"
            else -> Links.webSearch(t)
        }
        web.loadUrl(target); editing = false
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            if (editing) {
                                TextField(
                                    value = addr, onValueChange = { addr = it },
                                    singleLine = true,
                                    placeholder = { Text("Search or type a URL") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                                    keyboardActions = KeyboardActions(onGo = { go(addr) }),
                                    colors = TextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                Column(Modifier.fillMaxWidth().padding(end = 4.dp)) {
                                    Text(title.ifBlank { "Loading…" }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                                    Text(Uri.parse(url).host.orEmpty(), maxLines = 1, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 1.dp))
                                }
                            }
                        },
                        navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Close browser") } },
                        actions = {
                          if (!editing) {
                            IconButton(onClick = { addr = url; editing = true }) { Icon(Icons.Outlined.EditNote, "Edit address") }
                            val saved = learnState.saved.any { it.url == url }
                            IconButton(onClick = {
                                vm.learn.toggleSaved(SavedItem(title = title.ifBlank { url }, url = url, kind = if (Links.isVideo(url)) "video" else "page"))
                                Toast.makeText(ctx, if (saved) "Removed from Saved" else "Saved", Toast.LENGTH_SHORT).show()
                            }) { Icon(if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, "Save") }
                            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Ask xAI about this page") }, leadingIcon = { Icon(Icons.Outlined.AutoAwesome, null) }, onClick = {
                                    menu = false
                                    web.evaluateJavascript("(function(){return (document.body&&document.body.innerText||'').slice(0,12000)})()") { raw ->
                                        val text = runCatching { AppJson.decodeFromString(String.serializer(), raw ?: "\"\"") }.getOrDefault("")
                                        vm.askAboutPage(title, url, text)
                                        onAskInChat()
                                    }
                                })
                                DropdownMenuItem(text = { Text("Back") }, enabled = canBack, leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }, onClick = { menu = false; web.goBack() })
                                DropdownMenuItem(text = { Text("Forward") }, enabled = canForward, leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, null) }, onClick = { menu = false; web.goForward() })
                                DropdownMenuItem(text = { Text("Reload") }, leadingIcon = { Icon(Icons.Outlined.Refresh, null) }, onClick = { menu = false; web.reload() })
                                DropdownMenuItem(text = { Text("Share link") }, leadingIcon = { Icon(Icons.Outlined.Share, null) }, onClick = { menu = false; shareText(ctx, url, title) })
                                DropdownMenuItem(text = { Text("Open in YouTube / Chrome") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) }, onClick = {
                                    menu = false
                                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                })
                                DropdownMenuItem(text = { Text("Save link to Notes") }, leadingIcon = { Icon(Icons.Outlined.EditNote, null) }, onClick = {
                                    menu = false; vm.saveToNotes("# ${title.ifBlank { url }}\n\n$url\n", title.ifBlank { url })
                                })
                            }
                          }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    )
                    if (progress in 1..99) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth().height(2.dp))
                }
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { pad ->
            AndroidView(factory = { web }, modifier = Modifier.padding(pad).fillMaxSize())
        }
        // Fullscreen video layer
        fullscreen?.let { v ->
            AndroidView(
                factory = { v },
                modifier = Modifier.fillMaxSize().background(Color.Black),
            )
        }
    }
}
