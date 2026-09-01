from pathlib import Path

path = Path("app/src/main/java/com/music/bitchord/playback/PlaybackService.kt")
text = path.read_text()

old = "val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS\n                .buildUpon()"
new = "val commands = defaultBitChordLibraryCommands()\n                .buildUpon()"

count = text.count(old)
if count != 1:
    raise SystemExit(f"Expected exactly one DEFAULT_SESSION_COMMANDS onConnect match, got {count}")

path.write_text(text.replace(old, new, 1))
print("Switched Android Auto connection policy to MediaLibrary defaults.")
