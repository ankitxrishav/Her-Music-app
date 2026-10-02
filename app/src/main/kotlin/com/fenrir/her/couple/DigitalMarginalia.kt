package com.fenrir.her.couple

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fenrir.her.R
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.abs

@Serializable
data class DigitalMarginaliaNote(
  val id: String = System.currentTimeMillis().toString() + "_" + (100..999).random(),
  val songId: String,
  val songTitle: String,
  val timestampMs: Long,
  val text: String,
  val author: String,
  val createdEpochMs: Long = System.currentTimeMillis()
)

object DigitalMarginaliaSerialization {
  private val json = Json { ignoreUnknownKeys = true }

  fun parseNotes(rawJson: String): List<DigitalMarginaliaNote> {
    return try {
      json.decodeFromString<List<DigitalMarginaliaNote>>(rawJson)
    } catch (_: Exception) {
      emptyList()
    }
  }

  fun serializeNotes(notes: List<DigitalMarginaliaNote>): String {
    return try {
      json.encodeToString(notes)
    } catch (_: Exception) {
      "[]"
    }
  }
}

@Composable
fun MarginaliaOverlay(
  currentPositionMs: Long,
  notes: List<DigitalMarginaliaNote>,
  modifier: Modifier = Modifier
) {
  val activeNote = remember(currentPositionMs, notes) {
    notes.firstOrNull { abs(currentPositionMs - it.timestampMs) <= 3500L }
  }

  AnimatedVisibility(
    visible = activeNote != null,
    enter = fadeIn(tween(600)) + slideInVertically(tween(600)) { it / 2 },
    exit = fadeOut(tween(500)) + slideOutVertically(tween(500)) { it / 2 },
    modifier = modifier
  ) {
    if (activeNote != null) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 8.dp)
          .clip(RoundedCornerShape(24.dp))
          .background(Color(0xE614131A))
          .border(
            width = 1.5.dp,
            brush = Brush.linearGradient(
              colors = listOf(
                Color(0xFFFFD54F),
                Color(0xFFFF4081),
                Color(0xFFFFB300)
              )
            ),
            shape = RoundedCornerShape(24.dp)
          )
          .padding(20.dp)
      ) {
        Column(modifier = Modifier.fillMaxWidth()) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                painter = painterResource(R.drawable.favorite),
                contentDescription = null,
                tint = Color(0xFFFFD54F),
                modifier = Modifier.size(18.dp)
              )
              Spacer(Modifier.width(8.dp))
              Text(
                text = "Marginalia by ${activeNote.author}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFD54F)
              )
            }

            val totalSec = activeNote.timestampMs / 1000
            val min = totalSec / 60
            val sec = totalSec % 60
            Text(
              text = "%d:%02d".format(min, sec),
              style = MaterialTheme.typography.labelSmall,
              color = Color.White.copy(alpha = 0.7f),
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.12f))
                .padding(horizontal = 8.dp, vertical = 2.dp)
            )
          }

          Spacer(Modifier.height(12.dp))

          Text(
            text = "“${activeNote.text}”",
            style = MaterialTheme.typography.bodyLarge.copy(
              fontSize = 17.sp,
              lineHeight = 24.sp,
              fontStyle = FontStyle.Italic
            ),
            color = Color.White,
            fontWeight = FontWeight.Medium
          )

          Spacer(Modifier.height(8.dp))

          Text(
            text = "A love note anchored in this melody ✨",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.5f)
          )
        }
      }
    }
  }
}

@Composable
fun AddMarginaliaDialog(
  currentPositionMs: Long,
  songId: String,
  songTitle: String,
  author: String,
  onDismiss: () -> Unit,
  onSave: (DigitalMarginaliaNote) -> Unit
) {
  var noteText by remember { mutableStateOf("") }
  val totalSec = currentPositionMs / 1000
  val min = totalSec / 60
  val sec = totalSec % 60
  val formattedTime = "%d:%02d".format(min, sec)

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          painter = painterResource(R.drawable.favorite),
          contentDescription = null,
          tint = Color(0xFFFF4081),
          modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text("Digital Marginalia")
      }
    },
    text = {
      Column(modifier = Modifier.fillMaxWidth()) {
        Text(
          text = "Anchor a thought, memory, or quote to $formattedTime of \"$songTitle\" for your partner.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
          value = noteText,
          onValueChange = { noteText = it },
          placeholder = { Text("Write your handwritten note in the margin...") },
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFFFF4081),
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
          ),
          maxLines = 5
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (noteText.isNotBlank()) {
            onSave(
              DigitalMarginaliaNote(
                songId = songId,
                songTitle = songTitle,
                timestampMs = currentPositionMs,
                text = noteText.trim(),
                author = author
              )
            )
          }
        },
        enabled = noteText.isNotBlank(),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4081))
      ) {
        Text("Anchor Note", color = Color.White)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    }
  )
}
