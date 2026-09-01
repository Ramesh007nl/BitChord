package com.music.bitchord.data.local

data class LocalMusicAccessConfig(
    val setupSeen: Boolean = false,
    val allMusicEnabled: Boolean = false,
    val treeUris: Set<String> = emptySet(),
) {
    fun markSetupSeen() = copy(setupSeen = true)
    fun withAllMusic(enabled: Boolean) = copy(allMusicEnabled = enabled)
    fun addTree(uri: String) = copy(treeUris = treeUris + uri)
    fun removeTree(uri: String) = copy(treeUris = treeUris - uri)
}

enum class LocalMusicSetupChoice {
    ALL_MUSIC,
    CHOOSE_FOLDERS,
    BOTH,
    NOT_NOW,
}

data class LocalMusicSetupRequest(
    val enableAllMusic: Boolean,
    val pickFolder: Boolean,
)

fun requestForSetupChoice(choice: LocalMusicSetupChoice): LocalMusicSetupRequest = when (choice) {
    LocalMusicSetupChoice.ALL_MUSIC -> LocalMusicSetupRequest(enableAllMusic = true, pickFolder = false)
    LocalMusicSetupChoice.CHOOSE_FOLDERS -> LocalMusicSetupRequest(enableAllMusic = false, pickFolder = true)
    LocalMusicSetupChoice.BOTH -> LocalMusicSetupRequest(enableAllMusic = true, pickFolder = true)
    LocalMusicSetupChoice.NOT_NOW -> LocalMusicSetupRequest(enableAllMusic = false, pickFolder = false)
}
