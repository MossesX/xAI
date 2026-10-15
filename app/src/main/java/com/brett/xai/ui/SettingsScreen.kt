@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.brett.xai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brett.xai.ai.Prompts
import com.brett.xai.data.BackendMode
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val s by vm.store.settings.collectAsState()
    val sec by vm.store.secrets.collectAsState()
    val scope = rememberCoroutineScope()
    var sshResult by remember { mutableStateOf<String?>(null) }
    var apiResult by remember { mutableStateOf<String?>(null) }
    var testingSsh by remember { mutableStateOf(false) }
    var testingApi by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }
    fun set(f: (com.brett.xai.data.AppSettings) -> com.brett.xai.data.AppSettings) = vm.store.updateSettings(f)
    fun setSec(f: (com.brett.xai.data.Secrets) -> com.brett.xai.data.Secrets) = vm.store.updateSecrets(f)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---------------------------------------------------------------- routing
            Section("Which brain answers", "Your Claude subscription runs through Claude Code on your server. The API is the backup.") {
                Chips(
                    listOf(BackendMode.AUTO to "Auto (subscription → API)", BackendMode.SUBSCRIPTION to "Claude Code only", BackendMode.API to "API only"),
                    s.mode,
                ) { m -> set { it.copy(mode = m) } }
                Toggle("Fall back to API when Claude Code fails", "Used in Auto mode — e.g. server offline, usage limit hit, not logged in.", s.fallbackToApi) { v -> set { it.copy(fallbackToApi = v) } }
            }

            // ---------------------------------------------------------------- subscription
            Section("Claude Code subscription (via your server)", "The app SSHes into your VPS/PC and runs `claude -p` there. Claude Code must be installed and logged in to your Claude plan on that machine.") {
                TextButton(onClick = { showGuide = true }) { Text("How to set this up →") }
                Field("Host (IP, domain or Tailscale name)", s.sshHost, KeyboardType.Uri) { v -> set { it.copy(sshHost = v.trim()) } }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field("Port", s.sshPort.toString(), KeyboardType.Number, Modifier.weight(0.35f)) { v -> v.toIntOrNull()?.let { p -> set { it.copy(sshPort = p) } } }
                    Field("Username", s.sshUser, KeyboardType.Ascii, Modifier.weight(0.65f)) { v -> set { it.copy(sshUser = v.trim()) } }
                }
                Chips(listOf(false to "Password", true to "Private key"), s.sshUseKey) { v -> set { it.copy(sshUseKey = v) } }
                if (!s.sshUseKey) {
                    Secret("SSH password", sec.sshPassword) { v -> setSec { it.copy(sshPassword = v) } }
                } else {
                    Secret("Private key (paste the whole PEM / OpenSSH key)", sec.sshPrivateKey, multiline = true) { v -> setSec { it.copy(sshPrivateKey = v) } }
                    Secret("Key passphrase (optional)", sec.sshKeyPassphrase) { v -> setSec { it.copy(sshKeyPassphrase = v) } }
                }
                Field("Working directory on server", s.remoteWorkDir, KeyboardType.Uri) { v -> set { it.copy(remoteWorkDir = v) } }
                Field("Claude Code command / path", s.claudePath, KeyboardType.Uri, hint = "e.g. claude or /root/.local/bin/claude") { v -> set { it.copy(claudePath = v) } }

                Label("Model")
                Chips(listOf("" to "Default", "sonnet" to "Sonnet", "opus" to "Opus", "haiku" to "Haiku"), s.ccModel.takeIf { it in listOf("", "sonnet", "opus", "haiku") } ?: "custom") { v -> set { it.copy(ccModel = v) } }
                Field("…or exact model id", s.ccModel, KeyboardType.Ascii) { v -> set { it.copy(ccModel = v.trim()) } }

                Label("Permission mode (what Claude Code may do on the server)")
                Chips(
                    listOf("default" to "Ask (read-only)", "plan" to "Plan only", "acceptEdits" to "Edit files", "bypassPermissions" to "Full access"),
                    s.ccPermissionMode,
                ) { v -> set { it.copy(ccPermissionMode = v) } }
                Hint(
                    when (s.ccPermissionMode) {
                        "bypassPermissions" -> "⚠ Full access: Claude Code can run ANY command as ${s.sshUser}. Avoid on production servers or as root."
                        "acceptEdits" -> "Can create/edit files in the working directory. Shell commands still need allowed tools below."
                        "plan" -> "Analyses and plans only — never changes anything."
                        else -> "Headless default: tools that need approval are denied, so it can read and answer but not change things."
                    }
                )
                Field("Allowed tools (optional)", s.ccAllowedTools, KeyboardType.Ascii, hint = "e.g. Bash(git:*) Bash(npm run:*) Read Edit") { v -> set { it.copy(ccAllowedTools = v) } }
                Field("Extra CLI arguments (optional)", s.ccExtraArgs, KeyboardType.Ascii, hint = "e.g. --add-dir /var/www --max-turns 20") { v -> set { it.copy(ccExtraArgs = v) } }
                Secret("CLAUDE_CODE_OAUTH_TOKEN (optional)", sec.ccOauthToken, hint = "From `claude setup-token` on the server — only needed if Claude Code there isn't already logged in.") { v -> setSec { it.copy(ccOauthToken = v) } }
                Toggle("Continue server sessions", "Follow-ups reuse the same Claude Code session (--resume).", s.ccResume) { v -> set { it.copy(ccResume = v) } }
                Toggle("Live token streaming", "Shows words as they're written (--include-partial-messages).", s.ccPartial) { v -> set { it.copy(ccPartial = v) } }
                Field("Connect timeout (seconds)", s.sshTimeoutSec.toString(), KeyboardType.Number) { v -> v.toIntOrNull()?.let { t -> set { it.copy(sshTimeoutSec = t.coerceIn(3, 120)) } } }
                if (s.pinnedHostKey.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Pinned server key: ${s.pinnedHostKey.take(23)}…", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        TextButton(onClick = { set { it.copy(pinnedHostKey = "") } }) { Text("Forget") }
                    }
                }
                TestRow("Test SSH + Claude Code", testingSsh, sshResult) {
                    testingSsh = true; sshResult = null
                    scope.launch { sshResult = vm.engine.ssh.test(vm.store.settings.value, vm.store.secrets.value); testingSsh = false }
                }
            }

            // ---------------------------------------------------------------- API
            Section("Anthropic API (fallback)", "Pay-as-you-go key from console.anthropic.com. Used when the subscription path isn't available, or always in API-only mode.") {
                Secret("API key (sk-ant-…)", sec.apiKey) { v -> setSec { it.copy(apiKey = v.trim()) } }
                Label("Model")
                Chips(
                    listOf("claude-opus-5-5" to "Opus 5.5", "claude-sonnet-5-5" to "Sonnet 5.5", "claude-haiku-5-5" to "Haiku 5.5"),
                    s.apiModel,
                ) { v -> set { it.copy(apiModel = v) } }
                Field("…or exact model id", s.apiModel, KeyboardType.Ascii) { v -> set { it.copy(apiModel = v.trim()) } }
                Toggle("Web search", "Lets the API model search the web for current docs and versions.", s.webSearch) { v -> set { it.copy(webSearch = v) } }
                Field("Max output tokens", s.maxTokens.toString(), KeyboardType.Number) { v -> v.toIntOrNull()?.let { t -> set { it.copy(maxTokens = t.coerceIn(256, 128000)) } } }
                Label("Temperature: ${"%.1f".format(s.temperature)}")
                Slider(value = s.temperature, onValueChange = { v -> set { it.copy(temperature = (v * 10).toInt() / 10f) } }, valueRange = 0f..1f)
                Field("Messages of history sent", s.historyLimit.toString(), KeyboardType.Number) { v -> v.toIntOrNull()?.let { t -> set { it.copy(historyLimit = t.coerceIn(1, 200)) } } }
                Field("API base URL", s.apiBaseUrl, KeyboardType.Uri) { v -> set { it.copy(apiBaseUrl = v.trim()) } }
                TestRow("Test API key", testingApi, apiResult) {
                    testingApi = true; apiResult = null
                    scope.launch { apiResult = vm.engine.api.test(vm.store.settings.value, vm.store.secrets.value.apiKey); testingApi = false }
                }
            }

            // ---------------------------------------------------------------- assistant
            Section("Assistant", "The expert brain. Edit the instructions to change how it thinks and answers.") {
                Toggle("Use my Knowledge", "Include the facts in Knowledge in every request.", s.useKnowledge) { v -> set { it.copy(useKnowledge = v) } }
                Toggle("Learn automatically", "The assistant saves new facts about your setup to Knowledge as you chat.", s.autoLearn) { v -> set { it.copy(autoLearn = v) } }
                OutlinedTextField(
                    value = s.systemPrompt,
                    onValueChange = { v -> set { it.copy(systemPrompt = v) } },
                    label = { Text("System instructions") },
                    modifier = Modifier.fillMaxWidth().height(260.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                )
                TextButton(onClick = { set { it.copy(systemPrompt = Prompts.DEFAULT_SYSTEM) } }) { Text("Reset to default instructions") }
            }

            // ---------------------------------------------------------------- appearance
            Section("Appearance", null) {
                Chips(listOf("dark" to "Dark", "light" to "Light", "system" to "System"), s.theme) { v -> set { it.copy(theme = v) } }
                Toggle("Show which backend answered", null, s.showBackendBadge) { v -> set { it.copy(showBackendBadge = v) } }
            }

            // ---------------------------------------------------------------- data
            Section("Data", "Chats, notes and knowledge stay on this phone. Secrets are encrypted with the Android Keystore and excluded from backups.") {
                OutlinedButton(onClick = { confirmClear = true }) { Text("Delete all chats") }
            }
            Text("xAI ${com.brett.xai.BuildConfig.VERSION_NAME} (build ${com.brett.xai.BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Delete all chats?") },
        text = { Text("Notes and Knowledge are kept.") },
        confirmButton = { TextButton(onClick = { vm.store.deleteAllChats(); vm.newChat(); confirmClear = false }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
    )
    if (showGuide) AlertDialog(
        onDismissRequest = { showGuide = false },
        title = { Text("Use your Claude subscription") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Markdown(SETUP_GUIDE, onCopyCode = { }, onSaveCode = { _, _, _ -> })
            }
        },
        confirmButton = { TextButton(onClick = { showGuide = false }) { Text("Got it") } },
        dismissButton = {
            TextButton(onClick = { showGuide = false; vm.send("Walk me step by step through setting up Claude Code on my VPS so the xAI app can use my Claude subscription over SSH, including a non-root user, SSH keys and Tailscale."); onBack() }) {
                Text("Ask xAI to walk me through")
            }
        },
    )
}

private val SETUP_GUIDE = """
**1. Install Claude Code on your server** (over SSH, e.g. from Termius):

```bash
curl -fsSL https://claude.ai/install.sh | bash
```

**2. Log in once with your Claude plan** — run `claude` and follow the login link, *or* for headless servers run:

```bash
claude setup-token
```

and paste the token into *CLAUDE_CODE_OAUTH_TOKEN* here.

**3. Find the full path** if `claude` isn't on PATH for non-interactive shells:

```bash
which claude
```

**4. Fill in host, user and password/key** here, then tap *Test SSH + Claude Code*.

Tips: prefer an SSH key and a non-root user; with Tailscale you can use your server's tailnet name and close port 22 to the internet.
""".trimIndent()

@Composable
fun Section(title: String, subtitle: String?, content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun Label(t: String) = Text(t, style = MaterialTheme.typography.labelLarge)

@Composable
private fun Hint(t: String) = Text(t, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun <T> Chips(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (v, label) ->
            FilterChip(selected = v == selected, onClick = { onSelect(v) }, label = { Text(label) })
        }
    }
}

@Composable
private fun Toggle(title: String, subtitle: String?, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.size(12.dp))
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    keyboard: KeyboardType,
    modifier: Modifier = Modifier.fillMaxWidth(),
    hint: String? = null,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        supportingText = if (hint != null) { { Text(hint) } } else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, autoCorrectEnabled = false),
        modifier = modifier,
    )
}

@Composable
private fun Secret(label: String, value: String, multiline: Boolean = false, hint: String? = null, onChange: (String) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        supportingText = if (hint != null) { { Text(hint) } } else null,
        singleLine = !multiline,
        minLines = if (multiline) 4 else 1,
        maxLines = if (multiline) 8 else 1,
        textStyle = if (multiline) MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodyLarge,
        visualTransformation = if (visible || (multiline && value.isEmpty())) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (visible) "Hide" else "Show")
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TestRow(label: String, running: Boolean, result: String?, onTest: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Button(onClick = onTest, enabled = !running) {
            if (running) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.size(8.dp)) }
            Text(label)
        }
        if (result != null) {
            Text(
                result,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = if (result.startsWith("✓")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
    }
}
