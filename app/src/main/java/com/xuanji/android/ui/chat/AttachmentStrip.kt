package com.xuanji.android.ui.chat

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xuanji.android.AttachmentKind
import com.xuanji.android.PromptAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun AttachmentStrip(attachments: List<PromptAttachment>, modifier: Modifier = Modifier, onRemove: ((String) -> Unit)? = null) {
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(attachments, key = { it.id }) { attachment ->
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Row(Modifier.widthIn(max = 244.dp).padding(start = 8.dp, top = 6.dp, bottom = 6.dp, end = if (onRemove == null) 10.dp else 0.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AttachmentThumbnail(attachment)
                    Column(Modifier.widthIn(max = 138.dp)) {
                        Text(attachment.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (attachment.size > 0) "${(attachment.size / 1024).coerceAtLeast(1)} KB" else if (attachment.kind == AttachmentKind.IMAGE) "图片" else "附件",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (onRemove != null) IconButton({ onRemove(attachment.id) }, Modifier.size(48.dp)) {
                        Icon(Icons.Default.Close, "移除附件 ${attachment.name}", Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentThumbnail(attachment: PromptAttachment) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, attachment.id) {
        if (attachment.kind == AttachmentKind.IMAGE && attachment.localPath != null) value = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(attachment.localPath, bounds)
                val options = BitmapFactory.Options().apply {
                    inSampleSize = Integer.highestOneBit((maxOf(bounds.outWidth, bounds.outHeight) / 160).coerceAtLeast(1))
                }
                BitmapFactory.decodeFile(attachment.localPath, options)
            }.getOrNull()
        }
    }
    val image = bitmap
    if (image != null) Image(image.asImageBitmap(), attachment.name, Modifier.size(36.dp).clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.Crop)
    else Icon(if (attachment.kind == AttachmentKind.IMAGE) Icons.Default.Image else Icons.Default.Description,
        null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.secondary)
}
