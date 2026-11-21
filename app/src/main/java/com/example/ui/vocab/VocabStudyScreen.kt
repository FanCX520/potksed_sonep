package com.example.ui.vocab

import android.speech.tts.TextToSpeech
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.PenOsUiState
import com.example.ui.VocabQuizOption
import com.example.ui.theme.PenTheme
import java.util.Locale

private val BOOK_LABELS = listOf(
    "highschool" to "高中核心",
    "cet4" to "四级高频",
    "daily" to "日常实用"
)

@Composable
fun VocabStudyScreen(
    state: PenOsUiState,
    onBack: () -> Unit,
    onSelectOption: (String) -> Unit,
    onKillWord: () -> Unit,
    onToggleHint: () -> Unit,
    onNextWord: () -> Unit,
    onSwitchBook: (String) -> Unit,
    onSyncWebDav: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var ttsReady by remember { mutableStateOf(false) }
    val tts = remember {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale.US
                ttsReady = true
            }
        }
        engine
    }

    DisposableEffect(Unit) {
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    val colors = PenTheme.colors
    val vocab = state.vocabSession
    val currentWord = vocab.currentWord?.word
    val emerald = if (colors.isAppleLight) colors.primaryAction else colors.vocabEmerald
    val glacier = colors.linkAccent
    val coral = colors.primaryAction

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvasBackground)
            .padding(horizontal = 8.dp, vertical = 7.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // LEFT PANE (~44%): Word + Phonetic + Offline TTS + Baicizhan "斩" + Book Switcher
            Surface(
                modifier = Modifier
                    .weight(0.44f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(colors.cardCornerRadius),
                color = colors.glassCardElevated,
                border = if (colors.showCardBorder) BorderStroke(1.dp, colors.glassBorder) else null
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Header row: Back + Book cycle pill + Cloud Sync
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("vocab_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "返回桌面",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            val bookName = BOOK_LABELS.firstOrNull { it.first == vocab.activeBook }?.second ?: "词库"
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = glacier.copy(alpha = 0.16f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        val idx = BOOK_LABELS.indexOfFirst { it.first == vocab.activeBook }
                                        val next = BOOK_LABELS[(idx + 1) % BOOK_LABELS.size].first
                                        onSwitchBook(next)
                                    }
                                    .testTag("vocab_book_switch")
                            ) {
                                Text(
                                    text = "$bookName ${vocab.masteredCount}/${vocab.totalInBook}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = glacier,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = onSyncWebDav,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("vocab_quick_sync_button")
                        ) {
                            if (state.isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(15.dp),
                                    strokeWidth = 1.5.dp,
                                    color = glacier
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.CloudSync,
                                    contentDescription = "云同步单词进度",
                                    tint = glacier,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Center Word & Phonetic + Speak button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentWord?.word ?: "Loading...",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontSize = 23.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = currentWord?.phonetic ?: "",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = coral.copy(alpha = 0.18f),
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable {
                                    if (ttsReady && currentWord != null) {
                                        tts.speak(currentWord.word, TextToSpeech.QUEUE_FLUSH, null, "penos_word")
                                    }
                                }
                                .testTag("vocab_speak_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                                    contentDescription = "朗读单词",
                                    tint = coral,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Bottom Action Strip: 斩 (Kill) / 提示 (Hint) / 跳过 (Next)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CompactActionChip(
                            label = "斩",
                            icon = Icons.Rounded.ContentCut,
                            tint = emerald,
                            onClick = onKillWord,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("vocab_kill_button")
                        )
                        CompactActionChip(
                            label = "例句",
                            icon = Icons.Rounded.Lightbulb,
                            tint = PenTheme.colors.poetryGold,
                            onClick = onToggleHint,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("vocab_hint_button")
                        )
                        CompactActionChip(
                            label = "换词",
                            icon = Icons.Rounded.SkipNext,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = onNextWord,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("vocab_next_button")
                        )
                    }
                }
            }

            // RIGHT PANE (~56%): 2x2 Baicizhan Meaning Cards + Example Hint Bar
            Column(
                modifier = Modifier
                    .weight(0.56f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AnimatedVisibility(visible = vocab.showExampleHint && currentWord != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = PenTheme.colors.poetryGold.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, PenTheme.colors.poetryGold.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = currentWord?.let { "${it.exampleEn}  ${it.exampleZh}" }.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                val opts = vocab.options
                if (opts.size >= 4) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        VocabOptionCard(
                            option = opts[0],
                            selectedId = vocab.selectedOptionId,
                            onClick = { onSelectOption(opts[0].wordId) },
                            modifier = Modifier.weight(1f)
                        )
                        VocabOptionCard(
                            option = opts[1],
                            selectedId = vocab.selectedOptionId,
                            onClick = { onSelectOption(opts[1].wordId) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        VocabOptionCard(
                            option = opts[2],
                            selectedId = vocab.selectedOptionId,
                            onClick = { onSelectOption(opts[2].wordId) },
                            modifier = Modifier.weight(1f)
                        )
                        VocabOptionCard(
                            option = opts[3],
                            selectedId = vocab.selectedOptionId,
                            onClick = { onSelectOption(opts[3].wordId) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactActionChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = tint.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.30f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = tint,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun VocabOptionCard(
    option: VocabQuizOption,
    selectedId: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = PenTheme.colors
    val revealed = selectedId != null
    val isSelected = selectedId == option.wordId
    val emerald = if (colors.isAppleLight) colors.primaryAction else colors.vocabEmerald
    val rose = colors.dangerRose
    val cueColor = if (colors.isAppleLight) colors.badgeEmber else Color(option.accentHex)

    val bgColor = when {
        revealed && option.isCorrect -> emerald.copy(alpha = 0.20f)
        revealed && isSelected && !option.isCorrect -> rose.copy(alpha = 0.20f)
        else -> colors.glassCard
    }
    val border = when {
        revealed && option.isCorrect -> BorderStroke(1.5.dp, emerald)
        revealed && isSelected && !option.isCorrect -> BorderStroke(1.5.dp, rose)
        colors.showCardBorder -> BorderStroke(1.dp, colors.glassBorder)
        else -> null
    }

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(colors.cardCornerRadius))
            .clickable(enabled = !revealed, onClick = onClick)
            .testTag("vocab_option_${option.wordId}"),
        shape = RoundedCornerShape(colors.cardCornerRadius),
        color = bgColor,
        border = border
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = cueColor.copy(alpha = 0.18f)
                ) {
                    Text(
                        text = option.mnemonicTag,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = cueColor,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
                if (revealed && option.isCorrect) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "正确",
                        tint = emerald,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Text(
                text = option.meaningZh,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
