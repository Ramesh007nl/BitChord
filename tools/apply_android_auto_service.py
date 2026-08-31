from pathlib import Path

path = Path("app/src/main/java/com/music/bitchord/playback/AndroidAutoCatalog.kt")
text = path.read_text()

replacements = [
    (
        "import com.music.bitchord.data.model.artworkAt\n",
        "import com.music.bitchord.data.model.artworkAt\n"
        "import kotlinx.coroutines.async\n"
        "import kotlinx.coroutines.awaitAll\n"
        "import kotlinx.coroutines.coroutineScope\n",
    ),
    (
        "        } else {\n"
        "            val rows = buildList {\n"
        "                for (filter in SEARCH_FILTERS) {\n"
        "                    dataSource.search(clean, filter).getOrThrow().forEach { result ->\n"
        "                        when (result) {\n"
        "                            is SearchResult.Track -> add(playableRow(result.song))\n"
        "                            is SearchResult.Browse -> add(collectionRow(result.item))\n"
        "                        }\n"
        "                    }\n"
        "                }\n"
        "            }.distinctBy { it.mediaId }\n",
        "        } else {\n"
        "            val trackResults = dataSource.search(clean, SearchFilter.SONGS).getOrThrow()\n"
        "            val browseResults = coroutineScope {\n"
        "                SEARCH_FILTERS.drop(1)\n"
        "                    .map { filter -> async { dataSource.search(clean, filter).getOrThrow() } }\n"
        "                    .awaitAll()\n"
        "                    .flatten()\n"
        "            }\n"
        "            val rows = (trackResults + browseResults).map { result ->\n"
        "                when (result) {\n"
        "                    is SearchResult.Track -> playableRow(result.song)\n"
        "                    is SearchResult.Browse -> collectionRow(result.item)\n"
        "                }\n"
        "            }.distinctBy { it.mediaId }\n",
    ),
]

for old, new in replacements:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one match, got {count}: {old[:140]!r}")
    text = text.replace(old, new, 1)

path.write_text(text)
print("Applied assertion-checked concurrent Android Auto search edit.")
