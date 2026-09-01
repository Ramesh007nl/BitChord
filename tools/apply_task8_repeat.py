from pathlib import Path


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected exactly one match, found {count}: {old[:80]!r}")
    path.write_text(text.replace(old, new, 1))


service = Path("app/src/main/java/com/music/bitchord/playback/PlaybackService.kt")
main = Path("app/src/main/java/com/music/bitchord/MainActivity.kt")
smoke = Path(".github/workflows/android-auto-legacy-smoke.yml")

replace_once(
    service,
    '''/** Session command used by the media notification's Shuffle button. */
const val ACTION_TOGGLE_SHUFFLE = "com.music.bitchord.action.TOGGLE_SHUFFLE"
''',
    '''/** Session command used by the media notification's Shuffle button. */
const val ACTION_TOGGLE_SHUFFLE = "com.music.bitchord.action.TOGGLE_SHUFFLE"

/** Shared repeat cycle for phone, notification, and Android Auto controllers. */
internal fun nextRepeatMode(current: Int): Int = when (current) {
    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
    else -> Player.REPEAT_MODE_OFF
}

/** Session command used by Android Auto/media controllers to cycle repeat mode. */
const val ACTION_CYCLE_REPEAT = "com.tantov.music.action.CYCLE_REPEAT"
''',
)

replace_once(
    service,
    '''    private val favoriteCommand = SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle.EMPTY)
    private val autoplayCommand = SessionCommand(ACTION_TOGGLE_AUTOPLAY, Bundle.EMPTY)
    private val shuffleCommand = SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY)
''',
    '''    private val favoriteCommand = SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle.EMPTY)
    private val autoplayCommand = SessionCommand(ACTION_TOGGLE_AUTOPLAY, Bundle.EMPTY)
    private val shuffleCommand = SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY)
    private val repeatCommand = SessionCommand(ACTION_CYCLE_REPEAT, Bundle.EMPTY)
''',
)

replace_once(
    service,
    '''                .add(favoriteCommand)
                .add(autoplayCommand)
                .add(shuffleCommand)
                .build()
''',
    '''                .add(favoriteCommand)
                .add(autoplayCommand)
                .add(shuffleCommand)
                .add(repeatCommand)
                .build()
''',
)

replace_once(
    service,
    '''                ACTION_TOGGLE_AUTOPLAY -> toggleAutoplayFromNotification()
                ACTION_TOGGLE_SHUFFLE -> toggleShuffleFromNotification()
                ACTION_TOGGLE_FAVORITE -> session.player.currentMediaItem?.mediaId?.let {
''',
    '''                ACTION_TOGGLE_AUTOPLAY -> toggleAutoplayFromNotification()
                ACTION_TOGGLE_SHUFFLE -> toggleShuffleFromNotification()
                ACTION_CYCLE_REPEAT -> {
                    session.player.repeatMode = nextRepeatMode(session.player.repeatMode)
                    session.setCustomLayout(notificationButtons())
                }
                ACTION_TOGGLE_FAVORITE -> session.player.currentMediaItem?.mediaId?.let {
''',
)

replace_once(
    service,
    '''        return listOf(favorite, shuffle)
''',
    '''        val repeatMode = player?.repeatMode ?: Player.REPEAT_MODE_OFF
        val repeat = CommandButton.Builder(
            when (repeatMode) {
                Player.REPEAT_MODE_ONE -> CommandButton.ICON_REPEAT_ONE
                Player.REPEAT_MODE_ALL -> CommandButton.ICON_REPEAT_ALL
                else -> CommandButton.ICON_REPEAT_OFF
            },
        )
            .setSessionCommand(repeatCommand)
            .setDisplayName(
                when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> "Repeat one"
                    Player.REPEAT_MODE_ALL -> "Repeat all"
                    else -> "Repeat off"
                },
            )
            .build()
        return listOf(favorite, shuffle, repeat)
''',
)

replace_once(
    main,
    '''import com.music.bitchord.playback.QueueShuffle
import com.music.bitchord.playback.autoplaySectionStart
''',
    '''import com.music.bitchord.playback.QueueShuffle
import com.music.bitchord.playback.nextRepeatMode
import com.music.bitchord.playback.autoplaySectionStart
''',
)

replace_once(
    main,
    '''                    val next = when (it.repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
''',
    '''                    val next = nextRepeatMode(it.repeatMode)
''',
)

replace_once(
    smoke,
    '''    branches: [feat/android-auto-full]
''',
    '''    branches: [feat/android-auto-full, feat/tantov-music-v1]
''',
)

print("Task 8 repeat patch applied successfully")
