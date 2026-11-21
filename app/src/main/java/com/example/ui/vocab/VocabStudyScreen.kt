package com.example.ui.vocab

import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.PenOsUiState
import com.example.ui.VocabQuizOption
import com.example.ui.theme.PenMotion
import com.example.ui.theme.PenTheme
import com.example.ui.theme.springPressScale
import java.util.Locale

private val BOOK_LABELS = listOf(
    "middle" to "中考核心",
    "highschool" to "高考高频",
    "cet4" to "四级精选",
    "cet6" to "六级进阶",
    "daily" to "日常实用",
    "mistakes" to "错词本",
    "custom" to "导入词书"
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
    onPickVocabFile: () -> Unit,
    onImportVocabPath: (String) -> Unit,
    onSyncWebDav: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    var ttsReady by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importPathText by remember { mutableStateOf("/sdcard/Download/words.txt") }

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

    // Animated diagonal "斩" slash effect!
    val slashProgress = remember { Animatable(0f) }
    LaunchedEffect(vocab.slashEffectNonce) {
        if (vocab.slashEffectNonce > 0) {
            slashProgress.snapTo(0f)
            slashProgress.animateTo(1f, animationSpec = tween(290))
            slashProgress.snapTo(0f)
        }
    }

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
            // LEFT PANE (~46%): Word + Phonetic + Root Breakdown + Visual Scene Cue + TTS + 斩/例句/导入/换词
            Surface(
                modifier = Modifier
                    .weight(0.46f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(colors.cardCornerRadius),
                color = colors.glassCardElevated,
                border = if (colors.showCardBorder) BorderStroke(1.dp, colors.glassBorder) else null
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Top Row: Back + Graded Book Switcher (incl. 错词本/导入词书) + Import + Sync
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = onBack,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .testTag("vocab_back_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = "返回桌面",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(3.dp))
                                val bookName = BOOK_LABELS.firstOrNull { it.first == vocab.activeBook }?.second ?: "词库"
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (vocab.activeBook == "mistakes") colors.dangerRose.copy(alpha = 0.18f) else glacier.copy(alpha = 0.16f),
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
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = if (vocab.activeBook == "mistakes") colors.dangerRose else glacier,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { showImportDialog = true },
                                    modifier = Modifier
                                        .size(26.dp)
                                        .testTag("vocab_import_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.FileUpload,
                                        contentDescription = "导入TXT/CSV词书",
                                        tint = colors.poetryGold,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                IconButton(
                                    onClick = onSyncWebDav,
                                    modifier = Modifier
                                        .size(26.dp)
                                        .testTag("vocab_quick_sync_button")
                                ) {
                                    if (state.isSyncing) {
                                        CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 1.5.dp, color = glacier)
                                    } else {
                                        Icon(Icons.Rounded.CloudSync, contentDescription = "云同步", tint = glacier, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        // Middle: Word + Phonetic + Root/Affix Pill + Visual Memory Cue
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currentWord?.word ?: "Loading...",
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontSize = 19.sp,
                                            lineHeight = 21.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${currentWord?.phonetic.orEmpty()}  今日${vocab.dailyMasteredCount}/${state.config.vocabDailyGoal}  连胜${vocab.sessionStreak}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            lineHeight = 11.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Surface(
                                    shape = CircleShape,
                                    color = coral.copy(alpha = 0.18f),
                                    modifier = Modifier
                                        .size(28.dp)
                                        .springPressScale {
                                            if (ttsReady && currentWord != null) {
                                                tts.speak(currentWord.word, TextToSpeech.QUEUE_FLUSH, null, "penos_word")
                                            }
                                        }
                                        .clip(CircleShape)
                                        .testTag("vocab_speak_button")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = "朗读", tint = coral, modifier = Modifier.size(15.dp))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(3.dp))

                            // Word Root & Visual Scene Cue Card (Real Baicizhan Memory Aid!)
                            if (currentWord != null) {
                                val rootText = currentWord.rootBreakdown.ifBlank { currentWord.mnemonicTag }
                                val cueText = currentWord.visualCue.ifBlank { currentWord.exampleZh }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = colors.chipSurface.copy(alpha = 0.72f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = colors.poetryGold, modifier = Modifier.size(10.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "$rootText · $cueText",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 9.sp,
                                                lineHeight = 11.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        // Bottom Action Strip: 斩 (with slash anim) / 例句 / 换词
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
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
                                tint = colors.poetryGold,
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

                    // Diagonal Animated Slash Overlay when "斩" is triggered!
                    if (slashProgress.value > 0f) {
                        val p = slashProgress.value
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val start = Offset(size.width * 0.08f, size.height * 0.85f)
                            val end = Offset(
                                start.x + (size.width * 0.84f) * p,
                                start.y - (size.height * 0.72f) * p
                            )
                            drawLine(
                                color = emerald.copy(alpha = (1f - p * 0.55f).coerceIn(0f, 1f)),
                                start = start,
                                end = end,
                                strokeWidth = 10f,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }
            }

            // RIGHT PANE (~54%): 2x2 Baicizhan Spring Meaning Cards + Example Hint Bar
            Column(
                modifier = Modifier
                    .weight(0.54f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AnimatedVisibility(visible = vocab.showExampleHint && currentWord != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = colors.poetryGold.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, colors.poetryGold.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = currentWord?.let { "${it.exampleEn}  ${it.exampleZh}" }.orEmpty(),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                }

                val opts = vocab.options
                if (opts.size >= 4) {
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        VocabOptionCard(opts[0], vocab.selectedOptionId, { onSelectOption(opts[0].wordId) }, Modifier.weight(1f))
                        VocabOptionCard(opts[1], vocab.selectedOptionId, { onSelectOption(opts[1].wordId) }, Modifier.weight(1f))
                    }
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        VocabOptionCard(opts[2], vocab.selectedOptionId, { onSelectOption(opts[2].wordId) }, Modifier.weight(1f))
                        VocabOptionCard(opts[3], vocab.selectedOptionId, { onSelectOption(opts[3].wordId) }, Modifier.weight(1f))
                    }
                }
            }
        }

        // Inline TXT/CSV Custom Wordbook Importer (MT Manager + Direct Path)
        AnimatedVisibility(
            visible = showImportDialog,
            enter = PenMotion.overlayEnter(),
            exit = PenMotion.overlayExit()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.overlayScrim)
                    .clickable { showImportDialog = false }
                    .padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(enabled = false) {},
                    shape = RoundedCornerShape(colors.cardCornerRadius),
                    color = colors.glassCardElevated,
                    border = if (colors.showCardBorder) BorderStroke(1.dp, colors.glassBorderBright) else null
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("导入自定义 TXT / CSV 词书（支持 MT 管理器）", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                            IconButton(onClick = { showImportDialog = false }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Rounded.Close, contentDescription = "关闭", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                            }
                        }

                        Text(
                            text = "每行格式支持：word,音标,中文释义,例句  或  word\\t释义  或  word - 释义",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textMuted
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = importPathText,
                                onValueChange = { importPathText = it },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    onImportVocabPath(importPathText)
                                    showImportDialog = false
                                },
                                shape = RoundedCornerShape(980.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = colors.primaryAction),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("从路径导入", style = MaterialTheme.typography.labelSmall)
                            }
                            Button(
                                onClick = {
                                    showImportDialog = false
                                    onPickVocabFile()
                                },
                                shape = RoundedCornerShape(980.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = colors.vocabEmerald),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("MT 管理器选择", style = MaterialTheme.typography.labelSmall)
                            }
                        }
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
            .height(30.dp)
            .springPressScale(pressedScale = 0.92f, onClick = onClick)
            .clip(RoundedCornerShape(10.dp)),
        shape = RoundedCornerShape(10.dp),
        color = tint.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.30f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 5.dp)
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(13.dp))
            Spacer(modifier = Modifier.width(3.dp))
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = tint, fontWeight = FontWeight.Bold)
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
            .then(if (!revealed) Modifier.springPressScale(pressedScale = 0.94f, onClick = onClick) else Modifier)
            .clip(RoundedCornerShape(colors.cardCornerRadius))
            .testTag("vocab_option_${option.wordId}"),
        shape = RoundedCornerShape(colors.cardCornerRadius),
        color = bgColor,
        border = border
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(shape = RoundedCornerShape(6.dp), color = cueColor.copy(alpha = 0.18f)) {
                    Text(
                        text = option.mnemonicTag,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = cueColor,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
                if (revealed && option.isCorrect) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = "正确", tint = emerald, modifier = Modifier.size(14.dp))
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
