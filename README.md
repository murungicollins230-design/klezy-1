# Klezy — Assembled Android Project

Every phase (P1–P5), Groq wiring, conversation memory, retry/backoff,
contacts, and now a full voice-first UX pass: an orb-only interface (no
nav bar, no button grid), a lock-screen assistant, continuous
conversation, natural-voice selection, and a first pass at adaptive
advice. Opens directly in Android Studio.

## Read this first: what actually happens when you say "Hey Klezy"

**Phone unlocked:** a small orb appears — not the app, not a full
screen, just a ~280dp floating circle over whatever you were doing. It
listens, thinks, replies out loud, and closes itself. If your command
needs a follow-up ("now do the same for dad"), it keeps listening
without you repeating the wake word. Full capability here: apps,
contacts, messages, calls, media, screens, everything.

**Phone locked:** same small orb, shown over the lock screen via
`setShowWhenLocked()`. But the capability set is narrow on purpose —
torch, media playback, volume, camera launch. Anything else gets a
spoken "unlock your phone for that" instead of silently failing.

**This isn't a missing feature — it's Android's own security boundary.**
The keyguard blocks interaction with app content behind it, even for a
system-level assistant, on any non-rooted phone. Siri and Google
Assistant hit the exact same wall for the exact same reason. No app can
open WhatsApp and type a message while the phone is locked, full stop.

## What's here

```
app/src/main/java/com/klezy/app/
  MainActivity.kt              launcher entry point — deliberately quiet, see its own comment
  ui/
    OrbActivity.kt              THE only thing that appears on wake word — small floating window
    ChatActivity.kt             only reachable by saying "open chat" — has the erase-history button
    SettingsActivity.kt         only reachable by saying "open settings" — everything manual lives here
  voice/
    VoicePipelineOrchestrator.kt   wake-word detection only — lives in the foreground service now
    ConversationRouter.kt          the actual routing logic (screens/media/device/offline/online)
    ScreenCommandMatcher.kt        "open chat" / "open settings"
    LockScreenCommandMatcher.kt    the narrow locked-phone command set
    DeviceCommandMatcher.kt        "open Instagram", "call mum", "text..."
    ...(P3's WakeWordManager, VoiceInputManager, VoiceOutputManager, OfflineLlmEngine)
  media/
    MediaRuntime.kt              ONE shared MediaControlRouter — orb and automations both use it
    TorchController.kt           flashlight — works over the lock screen, no permission needed
    ...(P5's LocalMediaLibrary, MediaControlRouter, MediaPlaybackService, MediaCommandMatcher)
  automation/
    KlezyForegroundService.kt    owns AutomationEngine AND wake-word listening — see below
    ...(P2's AutomationModels, BootReceiver, ScheduledTriggerWorker)
  data/
    ActivityLogRepository.kt     logs executed commands — raw material for insights
    InsightsEngine.kt / InsightsWorker.kt   daily LLM-summarized pattern notes, see honesty note below
    ApiKeyStore.kt                now holds both Groq and Picovoice keys, encrypted
    ...(P4's AuthManager, AutomationRuleMapper/Repository, ChatHistoryRepository, FileStorageManager)
  network/GroqApiClient.kt       retry/backoff on 429/5xx
  contacts/ContactLookup.kt
  actions/, notifications/, util/   P1, unchanged
```

## The one architectural fix in this pass: where wake-word listening lives

It used to be started and stopped by `MainActivity` — which meant it
died the moment you left the app or the Activity got destroyed. That
directly contradicted "I should be able to call it anytime, even
locked." Wake-word listening now starts inside `KlezyForegroundService`
(the same always-on service that already ran automations), alongside a
new shared `MediaRuntime` so the orb and automation rules use one
connected media session instead of two competing ones. `VoicePipelineOrchestrator`
shrank to just wake-word detection + launching the orb; all the actual
listening/routing/speaking moved into `OrbActivity` + `ConversationRouter`,
fresh on every wake-up, nothing lingering in memory between calls.

## Continuous conversation

After any conversational reply (not a screen-open command), the orb
keeps listening for a few seconds without needing "Hey Klezy" again —
this is `ConversationRouter`'s `continueListening` flag, checked in
`OrbActivity` after each response. So: "Hey Klezy, WhatsApp mum saying
I'm on my way" → she gets it → "now tell dad the same" works as a direct
follow-up. Conversation memory (P4/Groq wiring) means the online-brain
path actually has the last exchange as context when you say "the same."

## Voice-driven automation builder

No form screen — say what you want and Klezy builds the rule. "Every day
at 10, pause my music" gets parsed by Groq into the exact JSON shape
`AutomationRuleMapper` already reads (see `VoiceAutomationBuilder.kt`),
then Klezy says back what it understood and waits for a yes before
saving — a wrongly-parsed automation (wrong time, wrong app) is a worse
failure than asking once. Also handles "what automations do I have" and
"delete the [name] automation" by voice. Needs a Groq key (same one used
for chat) since natural-language parsing needs the LLM, not regex.

**A real type bug caught and fixed while wiring this in:** the JSON
library used to parse Groq's output (`org.json`) returns `Integer` for
whole numbers, but `AutomationRuleMapper` was written assuming
Firestore's `Long` — every voice-created time-based rule would have
silently failed to parse. Fixed `AutomationRuleMapper` to accept any
`Number` type instead of hardcoding `Long`, which also makes it more
robust generally, not just for this new caller.

## Voice

`VoiceOutputManager` now tries to default to a female, on-device voice
automatically, and Settings lets you preview and pick from whatever
voices your phone actually has installed — quality varies a lot by
phone/Android version, since this is Android's own TTS engine, not a
premium one. A genuinely human-sounding voice (ElevenLabs) is a paid
step, deliberately not built — flagged in Settings rather than silently
using something that costs money.

## Adaptive advice — honest scoping

`InsightsEngine` is **not** real behavioral machine learning. What's
actually built: once a day, the last 7 days of executed-command logs get
handed to Groq with a prompt asking "anything worth noting here?" — the
result is a few sentences, stored once, and folded into the system
prompt on future conversations so Klezy's answers can reference it. This
is closer to "Klezy re-reads its own notes once a day" than a trained
model of your habits. Good enough to feel a little adaptive at personal
scale; know that it isn't more than that. With under 5 days of log
entries it says nothing rather than inventing a pattern from too little
data.

## Setup checklist, in order

### 1. Firebase (P4)
Create a project (Spark/free plan), register `com.klezy.app`, drop
`google-services.json` into `app/`, enable Anonymous Auth + Firestore +
Storage, publish `firestore.rules` and `storage.rules`.

### 2. Groq — say "open settings" once the app is running, paste your key
Free account at console.groq.com. Without this, Klezy still runs but
falls back to "I don't have an online brain connected yet" for anything
beyond device/media commands.

### 3. Picovoice — same Settings screen
Free account at console.picovoice.ai, train "Hey Klezy," drop the `.ppn`
into `app/src/main/assets/hey-klezy.ppn`, paste the AccessKey. **Until
this is set, the wake word literally cannot fire** — MainActivity's
status screen is tappable specifically to solve this bootstrap problem
(you can't say "open settings" if the wake word that gets you there
doesn't work yet).

### 4. Offline model (P3) — optional
The app works fine without it, always falling to Groq instead. See P3's
original setup notes for the llama.cpp/GGUF process if you want it.

### 5. Launcher icon
No `android:icon` is set — add one via Android Studio's Image Asset tool
before your first real build.

### 6. Runtime permissions
`MainActivity` requests RECORD_AUDIO, READ_CONTACTS, POST_NOTIFICATIONS,
READ_MEDIA_AUDIO on first launch. Accessibility Service and Notification
Listener access still need a manual settings-screen toggle (Android
rule) — Settings screen shows their status with a "fix in settings" link.

## minSdk change

Bumped from 26 to **33 (Android 13)** per your compatibility target —
your phone's on 14, so this covers you with room to spare, and let me
drop the pre-13 storage-permission fallback code entirely, simplifying
both the manifest and `MainActivity`.

## What still doesn't exist

- **Media library browser** — you can say "play [song]" but there's no
  visual list to scroll through.
- **Contact disambiguation** — two "John"s still just picks one, silently.
- **Streaming services** (Spotify/YouTube Music) — still local-library only.

## Known limitations carried forward, unchanged

- Time-based automation triggers: ~15-minute resolution (WorkManager's floor)
- Contact matching: substring-based, no fuzzy matching
- Battery optimization: Samsung/Xiaomi etc. may still kill the foreground
  service despite `START_STICKY` — no exemption-request prompt built yet
