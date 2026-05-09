# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this project is

**Claude Voice** — an Android app (Kotlin, minSdk 26) that:
1. Lets users chat with Claude via a simple message interface
2. Speaks Claude's responses aloud using Android's `TextToSpeech` engine
3. Lets users configure the voice by typing natural-language guidelines (e.g. "speak slowly in a deep, calm voice") — Claude parses those guidelines into concrete TTS parameters

## Build & run

Open in Android Studio (Hedgehog or newer) and sync Gradle, or use the CLI:

```bash
# debug build
./gradlew assembleDebug

# install on connected device / emulator
./gradlew installDebug

# run all unit tests
./gradlew test

# run a single test class
./gradlew :app:testDebugUnitTest --tests "com.claudevoice.YourTestClass"
```

Requires a Claude API key (entered in-app on the Voice Settings screen). The key is stored in `SharedPreferences` and never leaves the device except in API calls to `api.anthropic.com`.

## Architecture

```
app/src/main/java/com/claudevoice/
├── api/
│   └── ClaudeApiClient.kt      # All Anthropic API calls (streaming chat + guideline parsing)
├── data/
│   └── SettingsRepository.kt   # SharedPreferences wrapper (API key, VoiceSettings, guidelines)
├── model/
│   └── VoiceSettings.kt        # Data class: speechRate, pitch, volume, voiceLocale, voiceGender
├── ui/
│   ├── MainActivity.kt         # Chat screen; streams Claude response and feeds it to VoiceManager
│   ├── VoiceSettingsActivity.kt # Settings screen; calls ClaudeApiClient to parse guidelines
│   └── ChatAdapter.kt          # RecyclerView adapter; supports live-update of streaming messages
└── voice/
    └── VoiceManager.kt         # TextToSpeech wrapper; applies VoiceSettings, queues utterances
```

### Key data flow

**Chat flow:**
`MainActivity` → `ClaudeApiClient.sendMessage` (streaming SSE) → chunks appended to `ChatAdapter` in real-time → on completion, full response passed to `VoiceManager.speak`

**Voice configuration flow:**
User types guidelines in `VoiceSettingsActivity` → `ClaudeApiClient.parseVoiceGuidelines` sends guidelines to Claude with a strict system prompt → Claude returns a JSON `VoiceSettings` object → saved via `SettingsRepository` → `VoiceManager.applySettings` applies it immediately

### Claude API usage

- Model: `claude-sonnet-4-6`
- Chat responses use SSE streaming (`"stream": true`)
- Voice guideline parsing uses a non-streaming call with `max_tokens: 256` and a system prompt that forces pure JSON output
- Both paths live in `ClaudeApiClient.kt` and are called via coroutines (`Dispatchers.IO`)

## Key conventions

- All API calls are `suspend` functions returning `Result<T>`; callers use `.onSuccess`/`.onFailure`
- `VoiceManager` accepts a queued `pendingSettings` when TTS isn't ready yet and applies it on `onInit`
- `MainActivity.onResume` re-applies stored `VoiceSettings` so changes made in the settings screen take effect immediately on return
- View binding is enabled; never use `findViewById` directly
