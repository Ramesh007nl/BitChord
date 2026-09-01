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
