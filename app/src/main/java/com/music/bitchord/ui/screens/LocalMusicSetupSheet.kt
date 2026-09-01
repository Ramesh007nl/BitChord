package com.music.bitchord.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LocalMusicSetupSheet(
    onAllMusic: () -> Unit,
    onChooseFolders: () -> Unit,
    onUseBoth: () -> Unit,
    onNotNow: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Set up Local Music",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = "TanTov Music can use Android's music library, folders you choose, or both. You can change this later in Settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Button(
            onClick = onAllMusic,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("All Music on this phone")
        }
        OutlinedButton(
            onClick = onChooseFolders,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Choose music folders")
        }
        OutlinedButton(
            onClick = onUseBoth,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Use both")
        }
        TextButton(
            onClick = onNotNow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Not now")
        }
    }
}
