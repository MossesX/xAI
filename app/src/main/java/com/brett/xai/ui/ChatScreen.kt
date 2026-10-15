@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.brett.xai.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brett.xai.ai.Prompts
import com.brett.xai.data.BackendMode
import com.brett.xai.data.ChatMessage
import kotlinx.coroutines.launch

/** Holds content waiting for the system "Save as" picker. */
data class PendingSave(val name: String, val content: String)

@Composable
fun rememberFileSaver(onDone: (Boolean) -> Unit = {}): (PendingSave) -> Unit {
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf<PendingSave?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val p = pending
        if (uri != null && p != null) {
            val ok = runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(p.content.toByteArray()) } }.isSuccess
            Toast.makeText(ctx, if (ok) "Saved ${p.name}" else "Save failed", Toast.LENGTH_SHORT).show()
            onDone(ok)
        }
        pending = null
    }
    return { p -> pending = p; launcher.launch(p.name) }
}

fun shareText(ctx: android.content.Context, text: String, subject: String = "xAI") {
    val i = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    ctx.startActivity(Intent.createChooser(i, "Share"))
}

@Composable
fun ChatScreen(vm: AppViewModel, onNavigate: (String) -> Unit) {
    val ui by vm.ui.collectAsState()
    val chats by vm.store.chats.collectAsState()
    val settings by vm.store.settings.collectAsState()
    val secrets by vm.store.secrets.collectAsState()
    val conv = chats.firstOrNull { it.id == ui.currentId }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val saveFile = rememberFileSaver()

    LaunchedEffect(ui.toast) {
        ui.toast?.let { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show(); vm.toast(null) }
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(Modifier.padding(20.dp, 20.dp, 20.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(">_", color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Spacer(Modifier.size(8.dp))
                    Text("xAI", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("  AI dev agent", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                NavigationDrawerItem(
                    label = { Text("New chat") }, selected = false,
                    icon = { Icon(Icons.Outlined.Add, null) },
                    onClick = { vm.newChat(); scope.launch { drawer.close() } },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                LazyColumn(Modifier.weight(1f)) {
                    items(chats, key = { it.id }) { c ->
                        NavigationDrawerItem(
                            label = { Text(c.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            selected = c.id == ui.currentId,
                            icon = { Icon(Icons.Outlined.ChatBubbleOutline, null, Modifier.size(18.dp)) },
                            badge = {
                                IconButton(onClick = { vm.deleteChat(c.id) }, Modifier.size(32.dp)) {
                                    Icon(Icons.Outlined.DeleteOutline, "Delete", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            onClick = { vm.openChat(c.id); scope.launch { drawer.close() } },
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                listOf(
                    Triple("Notes & docs", Icons.Outlined.EditNote, "notes"),
                    Triple("Knowledge (what I've learned)", Icons.Outlined.Psychology, "knowledge"),
                    Triple("Settings", Icons.Outlined.Settings, "settings"),
                ).forEach { (label, icon, route) ->
                    NavigationDrawerItem(
                        label = { Text(label) }, selected = false, icon = { Icon(icon, null) },
                        onClick = { scope.launch { drawer.close() }; onNavigate(route) },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(conv?.title ?: "xAI", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                            Text(modeLabel(settings.mode, settings.sshHost.isNotBlank(), secrets.apiKey.isNotBlank()),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    navigationIcon = { IconButton(onClick = { scope.launch { drawer.open() } }) { Icon(Icons.Outlined.Menu, "Menu") } },
                    actions = {
                        IconButton(onClick = { vm.newChat() }) { Icon(Icons.Outlined.Add, "New chat") }
                        var menu by remember { mutableStateOf(false) }
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            listOf(BackendMode.AUTO to "Auto: Claude Code → API", BackendMode.SUBSCRIPTION to "Claude Code only", BackendMode.API to "API only")
                                .forEach { (m, label) ->
                                    DropdownMenuItem(
                                        text = { Text((if (settings.mode == m) "✓ " else "    ") + label) },
                                        onClick = { vm.store.updateSettings { it.copy(mode = m) }; menu = false },
                                    )
                                }
                            HorizontalDivider()
                            if (conv != null) {
                                DropdownMenuItem(text = { Text("Export chat as Markdown") }, leadingIcon = { Icon(Icons.Outlined.SaveAlt, null) }, onClick = {
                                    menu = false
                                    saveFile(PendingSave(conv.title.replace(Regex("[^\\w -]"), "").take(40).ifBlank { "chat" } + ".md", vm.conversationMarkdown(conv)))
                                })
                                DropdownMenuItem(text = { Text("Save chat to Notes") }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Notes, null) }, onClick = {
                                    menu = false; vm.saveToNotes(vm.conversationMarkdown(conv), conv.title)
                                })
                                DropdownMenuItem(text = { Text("Share chat") }, leadingIcon = { Icon(Icons.Outlined.Share, null) }, onClick = {
                                    menu = false; shareText(ctx, vm.conversationMarkdown(conv), conv.title)
                                })
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { pad ->
            Column(Modifier.padding(pad).fillMaxSize().imePadding()) {
                val messages = conv?.messages.orEmpty()
                val listState = rememberLazyListState()
                val lastLen = messages.lastOrNull()?.text?.length ?: 0
                LaunchedEffect(messages.size, lastLen / 200) {
                    if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size)
                }
                Box(Modifier.weight(1f)) {
                    if (messages.isEmpty()) {
                        EmptyState(onPick = { vm.send(it) })
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            items(messages, key = { it.id }) { m ->
                                if (m.role == "user") UserBubble(m, onCopy = { clipboard.setText(AnnotatedString(m.text)) })
                                else AssistantMessage(
                                    m = m,
                                    streaming = ui.streamingId == m.id,
                                    status = if (ui.streamingId == m.id) ui.status else "",
                                    showBadge = settings.showBackendBadge,
                                    isLast = m.id == messages.last().id,
                                    busy = ui.busy,
                                    onCopy = { clipboard.setText(AnnotatedString(it)); Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show() },
                                    onSaveCode = { lang, code, title -> saveFile(PendingSave(suggestedFileName(lang, title), code)) },
                                    onSaveNote = { vm.saveToNotes(m.text) },
                                    onShare = { shareText(ctx, m.text) },
                                    onExport = { saveFile(PendingSave("xai-answer.md", m.text)) },
                                    onRegenerate = { vm.regenerate(it) },
                                )
                            }
                            item { Spacer(Modifier.height(4.dp)) }
                        }
                    }
                }
                Composer(vm, busy = ui.busy)
            }
        }
    }
}

private fun modeLabel(mode: BackendMode, sub: Boolean, api: Boolean): String = when (mode) {
    BackendMode.AUTO -> when {
        sub && api -> "Claude Code subscription → API fallback"
        sub -> "Claude Code subscription (no API fallback key)"
        api -> "API (no Claude Code server set)"
        else -> "Not configured — open Settings"
    }
    BackendMode.SUBSCRIPTION -> if (sub) "Claude Code subscription only" else "Claude Code only — server not set"
    BackendMode.API -> if (api) "Anthropic API only" else "API only — key not set"
}

@Composable
private fun EmptyState(onPick: (String) -> Unit) {
    val x = LocalX.current
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 24.dp)) {
        item {
            Text(">_ xAI", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 34.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text("Your AI software-development agent.", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Coding agents, SSH, VPS, Tailscale, Mosh, tmux, MCP, n8n, Docker, CI — ask how to wire it all together from your phone. " +
                    "I can write the scripts, helper apps and docs too.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Lightbulb, null, tint = x.accent2, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("Try asking", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
        }
        items(Prompts.SUGGESTIONS) { s ->
            Surface(
                onClick = { onPick(s) },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) {
                Text(s, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun UserBubble(m: ChatMessage, onCopy: () -> Unit) {
    val x = LocalX.current
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        SelectionContainer {
            Text(
                m.text,
                modifier = Modifier
                    .widthIn(max = 330.dp)
                    .clip(RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp))
                    .background(x.userBubble)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.5.sp),
            )
        }
    }
}

@Composable
private fun AssistantMessage(
    m: ChatMessage,
    streaming: Boolean,
    status: String,
    showBadge: Boolean,
    isLast: Boolean,
    busy: Boolean,
    onCopy: (String) -> Unit,
    onSaveCode: (String, String, String?) -> Unit,
    onSaveNote: () -> Unit,
    onShare: () -> Unit,
    onExport: () -> Unit,
    onRegenerate: (BackendMode?) -> Unit,
) {
    val x = LocalX.current
    Column(Modifier.fillMaxWidth()) {
        if (m.note.isNotBlank()) {
            Text("↪ ${m.note}", style = MaterialTheme.typography.labelSmall, color = x.accent2, modifier = Modifier.padding(bottom = 6.dp))
        }
        val shown = Prompts.clean(m.text)
        if (shown.isNotBlank()) {
            if (m.error) {
                Surface(color = MaterialTheme.colorScheme.error.copy(alpha = 0.10f), shape = RoundedCornerShape(10.dp)) {
                    Text("⚠ $shown", Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Markdown(shown, onCopyCode = onCopy, onSaveCode = onSaveCode)
            }
        }
        if (streaming) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
                Text(status.ifBlank { "Writing…" }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                if (showBadge && m.via.isNotBlank()) {
                    Icon(if (m.via.startsWith("Claude Code")) Icons.Outlined.Terminal else Icons.Outlined.Cloud, null,
                        tint = x.muted, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.size(4.dp))
                    Text(m.via, style = MaterialTheme.typography.labelSmall, color = x.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f))
                } else Spacer(Modifier.weight(1f))
                SmallAction(Icons.Outlined.ContentCopy, "Copy") { onCopy(shown) }
                SmallAction(Icons.Outlined.EditNote, "Save to notes", onSaveNote)
                var more by remember { mutableStateOf(false) }
                Box {
                    SmallAction(Icons.Outlined.MoreVert, "More") { more = true }
                    DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                        DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Icons.Outlined.Share, null) }, onClick = { more = false; onShare() })
                        DropdownMenuItem(text = { Text("Save as .md file") }, leadingIcon = { Icon(Icons.Outlined.SaveAlt, null) }, onClick = { more = false; onExport() })
                        if (isLast && !busy) {
                            DropdownMenuItem(text = { Text("Regenerate") }, leadingIcon = { Icon(Icons.Outlined.Refresh, null) }, onClick = { more = false; onRegenerate(null) })
                            DropdownMenuItem(text = { Text("Regenerate via Claude Code") }, leadingIcon = { Icon(Icons.Outlined.Terminal, null) }, onClick = { more = false; onRegenerate(BackendMode.SUBSCRIPTION) })
                            DropdownMenuItem(text = { Text("Regenerate via API") }, leadingIcon = { Icon(Icons.Outlined.Cloud, null) }, onClick = { more = false; onRegenerate(BackendMode.API) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallAction(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(34.dp)) {
        Icon(icon, desc, modifier = Modifier.size(17.dp), tint = LocalX.current.muted)
    }
}

@Composable
private fun Composer(vm: AppViewModel, busy: Boolean) {
    val shared by vm.pendingInput.collectAsState()
    var text by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(shared) { if (shared.isNotEmpty()) { text = shared; vm.pendingInput.value = "" } }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            val heard = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            if (heard.isNotBlank()) text = (if (text.isBlank()) "" else "$text ") + heard
        }
    }
    val ctx = LocalContext.current
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 10.dp, end = 10.dp, bottom = 8.dp, top = 4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("Ask about AI dev, servers, agents…") },
            modifier = Modifier.weight(1f),
            maxLines = 8,
            shape = RoundedCornerShape(22.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
            trailingIcon = {
                IconButton(onClick = {
                    val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask xAI")
                    }
                    runCatching { voice.launch(i) }.onFailure { Toast.makeText(ctx, "No speech recognizer available", Toast.LENGTH_SHORT).show() }
                }) { Icon(Icons.Outlined.Mic, "Voice input") }
            },
        )
        Spacer(Modifier.size(8.dp))
        val enabled = busy || text.isNotBlank()
        Box(
            Modifier
                .padding(bottom = 4.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(enabled = enabled) {
                    if (busy) vm.stop() else { vm.send(text); text = "" }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (busy) Icons.Filled.Stop else Icons.AutoMirrored.Filled.Send,
                if (busy) "Stop" else "Send",
                tint = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
