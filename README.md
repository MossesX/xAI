# xAI — AI software-development agent for Android

An AI assistant that specialises in building software with AI: coding agents (Claude Code, Codex, Gemini CLI, Cursor…), SSH/Mosh/tmux, Tailscale, VPS deployment, MCP, n8n, Docker, CI — and how to wire them together so you can work from your phone. It writes scripts, helper apps and documentation, and learns your setup as you chat.

## How it talks to Claude
| Mode | How | Billing |
|---|---|---|
| **Claude Code subscription** (default) | App SSHes into your server and runs `claude -p --output-format stream-json` there; answers stream back | Your Claude plan (Claude Code is logged in on the server) |
| **Anthropic API** (fallback) | Direct Messages API with streaming + optional web search | Pay-as-you-go API key |

Auto mode tries Claude Code first and falls back to the API if the server is unreachable, not logged in, or out of usage. Everything is configurable in **Settings**.

## Server setup (once)
```bash
curl -fsSL https://claude.ai/install.sh | bash   # install Claude Code
claude                                            # log in with your Claude plan
# or for headless servers:
claude setup-token                                # paste token into the app
which claude                                      # path for Settings if not on PATH
```

## Features
- Streaming chat with Markdown, code blocks with **Copy** and **Save as file**
- Voice input, "Share → xAI" from any app
- Notes & docs: save any answer, edit, export `.md`
- Knowledge: facts about your setup, auto-learned from chats, sent with every request
- Secrets (SSH password/key, API key, OAuth token) encrypted with the Android Keystore; no cloud backup
- Server host-key pinning (trust on first use)

## Build
GitHub Actions builds a debug APK on every push to `main` and attaches it to a GitHub Release (`build-N`). Builds are signed with the committed debug key, so each new APK installs over the last one.
