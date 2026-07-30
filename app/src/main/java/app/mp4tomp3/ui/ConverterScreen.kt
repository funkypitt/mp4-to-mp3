package app.mp4tomp3.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mp4tomp3.R
import app.mp4tomp3.core.AudioConverter
import app.mp4tomp3.core.ConversionItem
import app.mp4tomp3.core.Status

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConverterScreen(
    items: List<ConversionItem>,
    isRunning: Boolean,
    onPickGallery: () -> Unit,
    onPickFiles: () -> Unit,
    onCancel: () -> Unit,
    onClear: () -> Unit,
    onShare: (List<ConversionItem>) -> Unit,
) {
    val done = items.filter { it.status == Status.DONE && it.outputUri != null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    if (items.isNotEmpty() && !isRunning) {
                        IconButton(onClick = onClear) {
                            Icon(
                                Icons.Rounded.DeleteSweep,
                                contentDescription = stringResource(R.string.clear),
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (items.isNotEmpty()) {
                BottomActions(
                    isRunning = isRunning,
                    doneCount = done.size,
                    onPickGallery = onPickGallery,
                    onCancel = onCancel,
                    onShareAll = { onShare(done) },
                )
            }
        },
    ) { padding ->
        if (items.isEmpty()) {
            EmptyState(
                modifier = Modifier.padding(padding),
                onPickGallery = onPickGallery,
                onPickFiles = onPickFiles,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 8.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    ItemCard(item = item, onShare = { onShare(listOf(item)) })
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    modifier: Modifier = Modifier,
    onPickGallery: () -> Unit,
    onPickFiles: () -> Unit,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.empty_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.empty_subtitle, AudioConverter.BITRATE_KBPS),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onPickGallery,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                Icon(Icons.Rounded.VideoLibrary, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.choose_videos))
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onPickFiles) {
                Icon(Icons.Rounded.FolderOpen, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.browse_files))
            }
        }
    }
}

@Composable
private fun ItemCard(item: ConversionItem, onShare: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusGlyph(item)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (item.status == Status.DONE) item.outputName else item.sourceName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = statusLine(item),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.status == Status.FAILED) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.status == Status.CONVERTING) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { item.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (item.status == Status.DONE) {
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = onShare,
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.share))
                }
            }
        }
    }
}

@Composable
private fun StatusGlyph(item: ConversionItem) {
    when (item.status) {
        Status.CONVERTING -> CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.5.dp,
        )

        Status.DONE -> Glyph(Icons.Rounded.CheckCircle, MaterialTheme.colorScheme.primary)
        Status.FAILED -> Glyph(Icons.Rounded.ErrorOutline, MaterialTheme.colorScheme.error)
        else -> Glyph(Icons.Rounded.MusicNote, MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Glyph(icon: ImageVector, tint: androidx.compose.ui.graphics.Color) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = tint)
}

@Composable
private fun statusLine(item: ConversionItem): String = when (item.status) {
    Status.QUEUED -> stringResource(R.string.status_queued)
    Status.CONVERTING -> stringResource(R.string.status_converting, (item.progress * 100).toInt())
    Status.DONE -> stringResource(R.string.status_done, AudioConverter.BITRATE_KBPS)
    Status.FAILED -> item.error ?: stringResource(R.string.status_failed)
    Status.CANCELLED -> stringResource(R.string.status_cancelled)
}

@Composable
private fun BottomActions(
    isRunning: Boolean,
    doneCount: Int,
    onPickGallery: () -> Unit,
    onCancel: () -> Unit,
    onShareAll: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isRunning) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.cancel))
                }
            } else {
                OutlinedButton(onClick = onPickGallery, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Add, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.add_videos))
                }
                if (doneCount > 1) {
                    Button(onClick = onShareAll, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Share, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.share_all, doneCount))
                    }
                }
            }
        }
    }
}
