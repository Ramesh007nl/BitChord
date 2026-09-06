from pathlib import Path

path = Path("app/build.gradle.kts")
text = path.read_text()
needle = '    testImplementation("androidx.test.ext:junit:1.3.0")\n'
replacement = needle + '    testImplementation("org.robolectric:robolectric:4.14.1")\n'
if text.count(needle) != 1:
    raise SystemExit(f"expected one test dependency insertion point, found {text.count(needle)}")
if 'org.robolectric:robolectric:' in text:
    raise SystemExit("Robolectric dependency already present")
path.write_text(text.replace(needle, replacement, 1))
