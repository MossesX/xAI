# xAI

Android app (Kotlin + Compose): an AI assistant specialised in **building software with AI** — coding agents, SSH/Mosh/tmux, Tailscale, VPS deploys, MCP, n8n, Docker, CI. It chats through **Claude Code on Brett's VPS** (SSH → `claude -p --output-format stream-json`, billed to his Claude plan) with the **Anthropic API** as fallback, and includes a Learn suite, web research, notes and a self-learned "knowledge" store about his setup. (Not related to the company xAI.)

## Status (as of Oct 17 2026)
- **Built:** streaming chat (Markdown, copy/save code blocks), Auto/Claude Code/API modes with fallback, web research + **Deep research**, voice input, Share → xAI, notes & docs (export `.md`), auto-learned knowledge, Keystore-encrypted secrets, SSH host-key pinning (TOFU), built-in browser/player, **Learn suite** (15 tracks, 160 topics, 450+ live YouTube search playlists, channels, free courses, Teach me / Quiz me, progress). Latest commit: README for learn suite and web research.
- **In progress / next:** nothing tracked as TODO in code. Ask Brett before starting new features.

## Stack & layout
Kotlin 2.0.20, AGP 8.5.2, compileSdk/targetSdk 35, minSdk 26, Java 17, Compose BOM 2024.09.03, Navigation-Compose, kotlinx.serialization, OkHttp, JSch + BouncyCastle (SSH), Coil. Versions in `gradle/libs.versions.toml`. Package `com.brett.xai`.
```
app/src/main/java/com/brett/xai/
  ai/     AssistantEngine (mode choice + fallback), ClaudeCodeSsh (claude -p over SSH),
          AnthropicApi (Messages API streaming + web search), Prompts, Events, YouTube
  data/   Store, Models (settings incl. libraryUrl), Learn (curriculum load/cache), SecretBox (AES-GCM Keystore)
  ui/     ChatScreen, SettingsScreen, LearnScreens, LibraryScreens, BrowserScreen, Markdown, AppViewModel, Theme
  MainActivity.kt
app/src/main/assets/curriculum.json   Learn library (GENERATED — see below)
app/debug.keystore                    committed debug signing key (intentional)
tools/gen_curriculum.py               generates curriculum.json
```

## Build, run, test
- **CI (normal path):** push to `main` (or manual dispatch) → **Build APK** → `gradle assembleDebug` (Gradle 8.9). Outputs artifact **xAI-apk** and a **GitHub Release** `build-<run_number>` with `xAI-<n>.apk`. `versionCode` = `GITHUB_RUN_NUMBER`. Compile errors appear as `::error::` annotations on the run.
- **Local:** `gradle assembleDebug` (no wrapper committed). Not verified on the VPS.
- **Regenerate the Learn library:** `python3 tools/gen_curriculum.py app/src/main/assets/curriculum.json` (prints track/topic counts). Edit the Python, not the JSON. The app pulls updates over the air from `raw.githubusercontent.com/MossesX/xAI/main/app/src/main/assets/curriculum.json`, so pushing to `main` updates installed apps without a new APK.
- **Tests:** none in the repo.
- **Server side (once):** Claude Code installed and logged in on the VPS (`claude`, or `claude setup-token` for headless); set the `claude` path in Settings if it isn't on PATH.

## Conventions & decisions
- **Claude Code subscription first, API second.** Auto mode falls back to the API if the server is unreachable, not logged in, or out of usage.
- **Secrets** (SSH password/key, API key, OAuth token) are encrypted with `SecretBox` (Android Keystore); backups exclude them (`res/xml/data_extraction_rules.xml`). Never commit real secrets.
- `app/debug.keystore` (password `android`) is committed **on purpose** so each CI APK installs over the last one. Don't remove or regenerate it.
- Video "playlists" are YouTube **searches**, so they never go stale; an optional YouTube Data API key gives native video cards.
- Web research prefers a configurable list of trusted sources and cites them.

## Related repos
- `MossesX/pilotx` — sets up the VPS side (SSH key, Tailscale, Claude Code sign-in, tmux) that xAI's Claude Code mode relies on.
- `MossesX/axHub` — connector hub on the same VPS.
- `MossesX/ActiveX` — sibling Android app with the same CI/Release pattern.

## Working with Brett (same in every MossesX repo)

- **Where he works:** mostly from his Android phone (Samsung Galaxy S26 Ultra), often through **Moshi** (mosh) into his Hostinger VPS, where Claude Code runs. Keep replies phone-readable: lead with the result, short paragraphs, no walls of text.
- **Model choice:** before any build task, recommend which model to use and why, then let him pick from the top three (Fable, Opus, Sonnet). Use Fable where it's warranted, Opus for most work, and Sonnet only when you're sure Opus isn't needed.
- **The repo is the shared memory.** Sessions on the phone (Moshi), desktop, web and claude.ai don't share chat history, so anything a future session needs must live in this repo. Commit and push often with clear messages, and update the **Status** section of this file whenever something meaningful changes.
- **No secrets in the repo:** no tokens, passwords, keys or IPs with credentials. Use environment variables or GitHub Actions secrets.
- **GitHub:** account `MossesX`. A new repo must exist on GitHub before a session can push to it.
- **Server:** a Hostinger VPS running **Coolify**. Coolify's Traefik proxy owns ports 80/443, so new web apps go behind it as Coolify apps (no separate nginx). His domain is `amberxai.com`.
- **axHub:** his connector hub on the VPS (repo `MossesX/axHub`). If the axHub MCP server is configured in this Claude Code install, use its `list_services`, `describe_service` and `call` tools to reach his other services instead of asking him for credentials.
