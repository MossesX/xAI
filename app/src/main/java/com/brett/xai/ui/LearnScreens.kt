@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.brett.xai.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.brett.xai.ai.Video
import com.brett.xai.ai.YouTube
import com.brett.xai.data.Links
import com.brett.xai.data.SavedItem
import com.brett.xai.data.Topic
import com.brett.xai.data.Track
import kotlinx.coroutines.launch

private fun parseColor(hex: String, fallback: Color): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)

@Composable
private fun LearnBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

// ============================================================================ Learn home
@Composable
fun LearnScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    openTrack: (String) -> Unit,
    openTopic: (String) -> Unit,
    openUrl: (String) -> Unit,
    toChat: () -> Unit,
) {
    val cur by vm.learn.curriculum.collectAsState()
    val st by vm.learn.state.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    val tabs = listOf("Tracks", "Videos", "Courses", "Channels", "Saved")
    val totalTopics = cur.tracks.sumOf { it.topics.size }

    Scaffold(
        topBar = {
            LearnBar("Learn", onBack) {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Update library") }, leadingIcon = { Icon(Icons.Outlined.CloudDownload, null) }, onClick = {
                        menu = false
                        scope.launch {
                            val msg = vm.learn.updateFrom(vm.store.settings.value.libraryUrl)
                            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                        }
                    })
                    DropdownMenuItem(text = { Text("Clear watch history") }, leadingIcon = { Icon(Icons.Outlined.History, null) }, onClick = { menu = false; vm.learn.clearHistory() })
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 12.dp, containerColor = MaterialTheme.colorScheme.background) {
                tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
            }
            when (tab) {
                0 -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        OutlinedTextField(
                            value = query, onValueChange = { query = it },
                            leadingIcon = { Icon(Icons.Outlined.Search, null) },
                            placeholder = { Text("Search ${totalTopics} topics…") },
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                        )
                    }
                    if (query.isNotBlank()) {
                        val hits = vm.learn.search(query)
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AssistChip(onClick = { openUrl(Links.ytSearch(query)) }, label = { Text("Videos") }, leadingIcon = { Icon(Icons.Outlined.PlayCircle, null, Modifier.size(18.dp)) })
                                AssistChip(onClick = { openUrl(Links.webSearch(query)) }, label = { Text("Web") }, leadingIcon = { Icon(Icons.Outlined.Language, null, Modifier.size(18.dp)) })
                                AssistChip(onClick = { vm.send("Research and teach me: $query", research = true); toChat() }, label = { Text("Ask xAI") },
                                    leadingIcon = { Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp)) })
                            }
                        }
                        if (hits.isEmpty()) item { Text("No topics match — try the Videos or Web buttons above.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        items(hits, key = { it.second.id }) { (tr, t) -> TopicRow(tr, t, st.completed.contains(t.id)) { openTopic(t.id) } }
                    } else {
                        item { ProgressCard(st.completed.size, totalTopics) }
                        items(cur.tracks, key = { it.id }) { tr ->
                            val done = tr.topics.count { it.id in st.completed }
                            TrackCard(tr, done) { openTrack(tr.id) }
                        }
                    }
                }
                1 -> VideoSearchTab(vm, openUrl)
                2 -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { Text("Free courses & interactive tutorials — they open in the built-in browser.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(cur.courses) { c ->
                        LinkCard(c.title, "${c.provider} · ${c.about}", Icons.Outlined.School) { openUrl(c.url) }
                    }
                }
                3 -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { Text("Channels worth following for AI dev, servers and tooling. Tap to browse their videos in-app.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(cur.channels) { ch ->
                        LinkCard(ch.name, ch.about, Icons.Outlined.Subscriptions) { openUrl(Links.ytChannel(ch.handle)) }
                    }
                }
                else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { SectionLabel("Saved (${st.saved.size})") }
                    if (st.saved.isEmpty()) item { Text("Tap the bookmark in the browser or on a video to save it here.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                    items(st.saved, key = { "s" + it.id }) { s -> SavedRow(s, vm, openUrl) }
                    item { Spacer(Modifier.height(8.dp)); SectionLabel("Recently watched & read") }
                    items(st.history.take(60), key = { "h" + it.id }) { s -> SavedRow(s, vm, openUrl) }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(t: String) = Text(t, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)

@Composable
private fun ProgressCard(done: Int, total: Int) {
    val x = LocalX.current
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.School, null, tint = x.accent2)
                Spacer(Modifier.width(8.dp))
                Text("Your AI dev curriculum", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("$done / $total", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(progress = { if (total == 0) 0f else done / total.toFloat() }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape))
            Spacer(Modifier.height(8.dp))
            Text("Every topic has live video playlists, official docs, and a one-tap lesson or quiz from xAI.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TrackCard(tr: Track, done: Int, onClick: () -> Unit) {
    val c = parseColor(tr.color, MaterialTheme.colorScheme.primary)
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(c.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Text(tr.title.take(1), color = c, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(tr.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(tr.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { if (tr.topics.isEmpty()) 0f else done / tr.topics.size.toFloat() },
                        color = c, modifier = Modifier.weight(1f).height(4.dp).clip(CircleShape),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("$done/${tr.topics.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun TopicRow(tr: Track?, t: Topic, done: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (done) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null,
                tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    listOfNotNull(tr?.title, t.level.ifBlank { null }).joinToString(" · ").ifBlank { t.summary },
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun LinkCard(title: String, sub: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SavedRow(s: SavedItem, vm: AppViewModel, openUrl: (String) -> Unit) {
    val st by vm.learn.state.collectAsState()
    val saved = st.saved.any { it.url == s.url }
    Surface(onClick = { openUrl(s.url) }, color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (s.thumb.isNotBlank()) {
                AsyncImage(model = s.thumb, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.width(96.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)))
            } else Icon(if (s.kind == "video") Icons.Outlined.PlayCircle else Icons.AutoMirrored.Outlined.MenuBook, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(s.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                Text(android.net.Uri.parse(s.url).host.orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { vm.learn.toggleSaved(s) }) {
                Icon(if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, "Save", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

// ============================================================================ Videos
private val POPULAR = listOf(
    "claude code tutorial", "claude code on phone", "mcp server tutorial", "tailscale setup", "mosh tmux tutorial",
    "n8n ai agent", "docker compose tutorial", "vps setup ubuntu", "github actions tutorial", "codex cli tutorial",
    "jetpack compose tutorial", "ollama tutorial",
)

@Composable
private fun VideoSearchTab(vm: AppViewModel, openUrl: (String) -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    var active by rememberSaveable { mutableStateOf("") }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            OutlinedTextField(
                value = q, onValueChange = { q = it },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                placeholder = { Text("Search tutorials on YouTube") },
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (q.isNotBlank()) active = q.trim() }),
            )
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                POPULAR.forEach { p -> FilterChip(selected = active == p, onClick = { q = p; active = p }, label = { Text(p) }) }
            }
        }
        if (active.isNotBlank()) videoResults(vm, active, openUrl)
    }
}

/** Live video results for a query: native cards with a YouTube API key, otherwise the in-app YouTube results page. */
private fun LazyListScope.videoResults(vm: AppViewModel, query: String, openUrl: (String) -> Unit) {
    item(key = "vr-$query") { VideoResults(vm, query, openUrl) }
}

@Composable
fun VideoResults(vm: AppViewModel, query: String, openUrl: (String) -> Unit) {
    val secrets by vm.store.secrets.collectAsState()
    val key = secrets.youtubeApiKey
    val videos = remember(query) { mutableStateListOf<Video>() }
    var next by remember(query) { mutableStateOf<String?>(null) }
    var loading by remember(query) { mutableStateOf(false) }
    var error by remember(query) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun load(token: String?) {
        if (key.isBlank() || loading) return
        loading = true
        scope.launch {
            runCatching { YouTube.search(key, query, token) }
                .onSuccess { page -> videos.addAll(page.videos.filter { v -> videos.none { it.id == v.id } }); next = page.next; error = null }
                .onFailure { error = it.message }
            loading = false
        }
    }
    LaunchedEffect(query, key) { if (videos.isEmpty()) load(null) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = { openUrl(Links.ytSearch(query)) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.PlayCircle, null); Spacer(Modifier.width(8.dp))
            Text("Watch all \"$query\" videos", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (key.isBlank()) {
            Text("Tip: add a free YouTube Data API key in Settings → Learning to get video cards with thumbnails right here.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        videos.forEach { v -> VideoCard(vm, v, openUrl) }
        if (loading) Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
        if (next != null && !loading) OutlinedButton(onClick = { load(next) }, modifier = Modifier.fillMaxWidth()) { Text("Load more videos") }
    }
}

@Composable
private fun VideoCard(vm: AppViewModel, v: Video, openUrl: (String) -> Unit) {
    val st by vm.learn.state.collectAsState()
    val url = Links.ytWatch(v.id)
    val saved = st.saved.any { it.url == url }
    Surface(onClick = { openUrl(url) }, color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(14.dp)) {
        Column {
            Box {
                AsyncImage(model = v.thumb, contentDescription = v.title, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)))
                Icon(Icons.Outlined.PlayCircle, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.align(Alignment.Center).size(52.dp))
            }
            Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(v.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${v.channel} · ${v.published}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { vm.learn.toggleSaved(SavedItem(title = v.title, url = url, kind = "video", thumb = v.thumb)) }) {
                    Icon(if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, "Save", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

// ============================================================================ Track
@Composable
fun TrackScreen(vm: AppViewModel, id: String, onBack: () -> Unit, openTopic: (String) -> Unit, toChat: () -> Unit) {
    val st by vm.learn.state.collectAsState()
    vm.learn.curriculum.collectAsState().value
    val tr = vm.learn.track(id) ?: run { LaunchedEffect(Unit) { onBack() }; return }
    val c = parseColor(tr.color, MaterialTheme.colorScheme.primary)
    val done = tr.topics.count { it.id in st.completed }
    Scaffold(topBar = { LearnBar(tr.title, onBack) }, containerColor = MaterialTheme.colorScheme.background) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text(tr.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(progress = { if (tr.topics.isEmpty()) 0f else done / tr.topics.size.toFloat() }, color = c,
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape))
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$done of ${tr.topics.size} done", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        vm.send("Give me a study plan for \"${tr.title}\": ${tr.summary}. Topics: ${tr.topics.joinToString("; ") { it.title }}. " +
                            "Order them for my setup, estimate time for each, and tell me what to build to practise.")
                        toChat()
                    }) { Text("Plan this track with xAI") }
                }
                Spacer(Modifier.height(4.dp))
            }
            items(tr.topics, key = { it.id }) { t -> TopicRow(null, t, t.id in st.completed) { openTopic(t.id) } }
        }
    }
}

// ============================================================================ Topic
@Composable
fun TopicScreen(vm: AppViewModel, id: String, onBack: () -> Unit, openUrl: (String) -> Unit, toChat: () -> Unit) {
    val st by vm.learn.state.collectAsState()
    vm.learn.curriculum.collectAsState().value
    val (tr, t) = vm.learn.topic(id) ?: run { LaunchedEffect(Unit) { onBack() }; return }
    val done = t.id in st.completed
    var activeQuery by rememberSaveable(id) { mutableStateOf(t.videos.firstOrNull().orEmpty()) }
    val x = LocalX.current

    Scaffold(topBar = { LearnBar(t.title, onBack) }, containerColor = MaterialTheme.colorScheme.background) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("${tr.title}${if (t.level.isNotBlank()) " · ${t.level}" else ""}", style = MaterialTheme.typography.labelMedium, color = x.accent2)
                Spacer(Modifier.height(4.dp))
                Text(t.summary, style = MaterialTheme.typography.bodyLarge)
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        vm.send(
                            "Teach me: ${t.title} (${tr.title}). ${t.summary}\n" +
                                "Start from basics, relate it to my setup, give hands-on steps I can do from my phone/VPS, " +
                                "a short exercise, and end with a Watch & read section.",
                            research = true,
                        )
                        toChat()
                    }) { Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Teach me") }
                    OutlinedButton(onClick = {
                        vm.send("Quiz me on ${t.title}: ask 5 multiple-choice questions one at a time, wait for my answer, then explain.")
                        toChat()
                    }) { Icon(Icons.Outlined.Quiz, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Quiz me") }
                    FilterChip(selected = done, onClick = { vm.learn.toggleDone(t.id) },
                        label = { Text(if (done) "Done" else "Mark done") },
                        leadingIcon = { Icon(if (done) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null, Modifier.size(18.dp)) })
                }
            }
            item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
            item { SectionLabel("Read the docs") }
            items(t.docs) { d -> LinkCard(d.title, android.net.Uri.parse(d.url).host.orEmpty(), Icons.AutoMirrored.Outlined.MenuBook) { openUrl(d.url) } }
            item {
                LinkCard("Search the web for \"${t.title}\"", "Latest guides, blog posts and docs", Icons.Outlined.TravelExplore) {
                    openUrl(Links.webSearch(t.title + " tutorial"))
                }
            }
            item { SectionLabel("Watch") }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    t.videos.forEach { q -> FilterChip(selected = activeQuery == q, onClick = { activeQuery = q }, label = { Text(q, maxLines = 1) }) }
                }
            }
            if (activeQuery.isNotBlank()) item(key = "videos-$activeQuery") { VideoResults(vm, activeQuery, openUrl) }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
