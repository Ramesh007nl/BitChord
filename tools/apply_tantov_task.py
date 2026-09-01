from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected exactly one match, found {count}")
    p.write_text(text.replace(old, new, 1))


# Task 1: permanent TanTov dev identity.
replace_once(
    "app/build.gradle.kts",
    '''        create("dev") {\n            dimension = "env"\n            applicationId = "com.dev.bitchord"\n            resValue("string", "app_name", "BitChord Dev")\n        }''',
    '''        create("dev") {\n            dimension = "env"\n            applicationId = "com.tantov.music"\n            resValue("string", "app_name", "TanTov Music")\n        }''',
)

replace_once(
    "app/src/main/java/com/music/bitchord/playback/AndroidAutoCatalog.kt",
    '    fun root(): MediaItem = browsable(AndroidAutoRoute.Root, "BitChord")',
    '    fun root(): MediaItem = browsable(AndroidAutoRoute.Root, "TanTov Music")',
)

files = {
    "app/src/dev/res/drawable/ic_launcher_background.xml": '''<?xml version="1.0" encoding="utf-8"?>\n<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">\n    <gradient\n        android:angle="315"\n        android:startColor="#111827"\n        android:centerColor="#312E81"\n        android:endColor="#7C3AED" />\n</shape>\n''',
    "app/src/dev/res/drawable/ic_launcher_foreground.xml": '''<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android"\n    android:width="108dp"\n    android:height="108dp"\n    android:viewportWidth="108"\n    android:viewportHeight="108">\n    <path android:fillColor="#FFFFFFFF"\n        android:pathData="M22,30 L50,30 L50,38 L40,38 L40,76 L32,76 L32,38 L22,38 Z" />\n    <path android:fillColor="#FFFFFFFF"\n        android:pathData="M54,30 L82,30 L82,38 L72,38 L72,58 L64,58 L64,38 L54,38 Z" />\n    <path android:fillColor="#FFE9D5FF"\n        android:pathData="M69,49 L76,47 L76,69 C76,75 72,79 66,79 C61,79 58,76 58,72 C58,68 62,65 67,65 C68,65 69,65 69,66 Z" />\n</vector>\n''',
    "app/src/dev/res/drawable/ic_notification_logo.xml": '''<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android"\n    android:width="24dp"\n    android:height="24dp"\n    android:viewportWidth="24"\n    android:viewportHeight="24">\n    <path android:fillColor="#FFFFFFFF"\n        android:pathData="M2,4 H10 V6 H7 V20 H5 V6 H2 Z" />\n    <path android:fillColor="#FFFFFFFF"\n        android:pathData="M11,4 H21 V6 H18 V12.5 L16,13.1 V6 H11 Z M16,12 V17 C16,19 14.6,20 12.7,20 C11.1,20 10,19.1 10,17.8 C10,16.4 11.3,15.4 13,15.4 C13.4,15.4 13.8,15.5 14,15.6 V12.6 Z" />\n</vector>\n''',
    "app/src/dev/res/mipmap-anydpi/ic_launcher.xml": '''<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n    <background android:drawable="@drawable/ic_launcher_background" />\n    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n    <monochrome android:drawable="@drawable/ic_launcher_foreground" />\n</adaptive-icon>\n''',
    "app/src/dev/res/mipmap-anydpi/ic_launcher_round.xml": '''<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n    <background android:drawable="@drawable/ic_launcher_background" />\n    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n    <monochrome android:drawable="@drawable/ic_launcher_foreground" />\n</adaptive-icon>\n''',
}
for path, content in files.items():
    p = Path(path)
    p.parent.mkdir(parents=True, exist_ok=True)
    if p.exists():
        raise SystemExit(f"{path}: expected new file but it already exists")
    p.write_text(content)

Path("/tmp/tantov-commit-message").write_text("feat(tantov): establish app identity\n")
