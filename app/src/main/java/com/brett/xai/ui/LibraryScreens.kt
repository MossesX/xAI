@file:OptIn(ExperimentalMaterial3Api::class)

package com.brett.xai.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.brett.xai.data.KnowledgeItem
import com.brett.xai.data.Note
import java.text.DateFormat
import java.util.Date

@Composable
private fun BackBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

// ===================================================================== Knowledge
@Composable
fun KnowledgeScreen(vm: AppViewModel, onBack: () -> Unit) {
    val items by vm.store.knowledge.collectAsState()
    var draft by rememberSaveable { mutableStateOf("") }
    Scaffold(topBar = { BackBar("Knowledge", onBack) }, containerColor = MaterialTheme.colorScheme.background) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().imePadding()) {
            LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text(
                        "What xAI knows about your setup. It's added to every request so answers fit your phone, servers and tools. " +
                            "Items marked \"learned\" were picked up automatically from your chats. Never store passwords or keys here.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                items(items, key = { it.id }) { k -> KnowledgeRow(k, vm) }
            }
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft, onValueChange = { draft = it },
                    placeholder = { Text("e.g. My VPS runs Ubuntu 24.04 with Docker") },
                    modifier = Modifier.weight(1f), maxLines = 3,
                )
                IconButton(onClick = {
                    if (draft.isNotBlank()) {
                        vm.store.updateKnowledge { it + KnowledgeItem(text = draft.trim()) }
                        draft = ""
                    }
                }) { Icon(Icons.Outlined.Add, "Add") }
            }
        }
    }
}

@Composable
private fun KnowledgeRow(k: KnowledgeItem, vm: AppViewModel) {
    var editing by remember { mutableStateOf(false) }
    var text by remember(k.text) { mutableStateOf(k.text) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (editing) {
                    OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth())
                } else {
                    Text(k.text, style = MaterialTheme.typography.bodyMedium,
                        color = if (k.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (k.source == "learned") Text("learned", style = MaterialTheme.typography.labelSmall, color = LocalX.current.accent2)
            }
            if (editing) {
                IconButton(onClick = {
                    vm.store.updateKnowledge { list -> list.map { if (it.id == k.id) it.copy(text = text.trim()) else it } }
                    editing = false
                }) { Icon(Icons.Outlined.Check, "Save") }
            } else {
                IconButton(onClick = { editing = true }, Modifier.size(36.dp)) { Icon(Icons.Outlined.Edit, "Edit", Modifier.size(18.dp)) }
            }
            Switch(checked = k.enabled, onCheckedChange = { v ->
                vm.store.updateKnowledge { list -> list.map { if (it.id == k.id) it.copy(enabled = v) else it } }
            })
            IconButton(onClick = { vm.store.updateKnowledge { list -> list.filterNot { it.id == k.id } } }, Modifier.size(36.dp)) {
                Icon(Icons.Outlined.DeleteOutline, "Delete", Modifier.size(18.dp))
            }
        }
    }
}

// ===================================================================== Notes
@Composable
fun NotesScreen(vm: AppViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val notes by vm.store.notes.collectAsState()
    Scaffold(
        topBar = { BackBar("Notes & docs", onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                val n = Note(title = "Untitled", body = "")
                vm.store.upsertNote(n); onOpen(n.id)
            }) { Icon(Icons.Outlined.Add, "New note") }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        if (notes.isEmpty()) {
            Column(Modifier.padding(pad).fillMaxSize().padding(24.dp)) {
                Text("No notes yet.", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text("Tap the note icon under any answer to save it here, or ask xAI to \"generate documentation for…\" and save the result.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else LazyColumn(Modifier.padding(pad), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(notes, key = { it.id }) { n ->
                Surface(onClick = { onOpen(n.id) }, color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(n.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(n.body.take(140).replace('\n', ' '), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(n.ts)),
                            style = MaterialTheme.typography.labelSmall, color = LocalX.current.muted)
                    }
                }
            }
        }
    }
}

@Composable
fun NoteScreen(vm: AppViewModel, id: String, onBack: () -> Unit) {
    val notes by vm.store.notes.collectAsState()
    val note = notes.firstOrNull { it.id == id }
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val saveFile = rememberFileSaver()
    var editing by rememberSaveable { mutableStateOf(note?.body?.isBlank() == true) }
    var title by rememberSaveable(id) { mutableStateOf(note?.title.orEmpty()) }
    var body by rememberSaveable(id) { mutableStateOf(note?.body.orEmpty()) }
    if (note == null) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onBack() }
        return
    }

    Scaffold(
        topBar = {
            BackBar(if (editing) "Edit note" else note.title, onBack = {
                if (editing) vm.store.upsertNote(note.copy(title = title.ifBlank { "Untitled" }, body = body, ts = System.currentTimeMillis()))
                onBack()
            }) {
                if (editing) IconButton(onClick = {
                    vm.store.upsertNote(note.copy(title = title.ifBlank { "Untitled" }, body = body, ts = System.currentTimeMillis())); editing = false
                }) { Icon(Icons.Outlined.Check, "Done") }
                else IconButton(onClick = { title = note.title; body = note.body; editing = true }) { Icon(Icons.Outlined.Edit, "Edit") }
                IconButton(onClick = { shareText(ctx, note.body, note.title) }) { Icon(Icons.Outlined.Share, "Share") }
                IconButton(onClick = {
                    saveFile(PendingSave(note.title.replace(Regex("[^\\w -]"), "").take(40).ifBlank { "note" } + ".md", note.body))
                }) { Icon(Icons.Outlined.SaveAlt, "Save .md") }
                IconButton(onClick = { vm.store.deleteNote(note.id) }) { Icon(Icons.Outlined.DeleteOutline, "Delete") }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        if (editing) {
            Column(Modifier.padding(pad).fillMaxSize().imePadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = body, onValueChange = { body = it }, label = { Text("Markdown") },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                )
            }
        } else {
            Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                Markdown(
                    note.body,
                    onCopyCode = { clipboard.setText(AnnotatedString(it)); Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show() },
                    onSaveCode = { lang, code, t -> saveFile(PendingSave(suggestedFileName(lang, t), code)) },
                )
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}
