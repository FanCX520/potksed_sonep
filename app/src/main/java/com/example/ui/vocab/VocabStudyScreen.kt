package com.example.ui.vocab

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.PenOsUiState
import com.example.ui.components.MonoCard
import com.example.ui.components.MonoChip
import com.example.ui.components.MonoOutlineButton
import com.example.ui.components.MonoPrimaryButton
import com.example.ui.components.MonoTopBar
import com.example.ui.theme.ClockDisplayFont
import com.example.ui.theme.PenTheme

private val VOCAB_BOOKS = listOf(
    "middle" to "中考",
    "highschool" to "高中",
    "cet4" to "四级",
    "cet6" to "六级",
    "daily" to "日常",
    "custom" to "自定义"
)

@Composable
fun VocabStudyScreen(
    state: PenOsUiState,
    onBack: () -> Unit,
    onRevealMeaning: () -> Unit,
    onGradeWord: (Int) -> Unit,
    onSwitchBook: (String) -> Unit,
    onPickVocabFile: () -> Unit,
    onImportVocabPath: (String) -> Unit
) {
    BackHandler(onBack = onBack)

    val palette = PenTheme.palette
    val vocab = state.vocab
    val word = vocab.currentWord
    var showImportDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.canvas)
            .safeDrawingPadding()
    ) {
        MonoTopBar(
            title = "百词斩 · 离线背词",
            subtitle = "今日 ${vocab.todayReviewed}/${state.config.vocabDailyGoal} · 已斩 ${vocab.masteredCount} 词",
            onBack = onBack,
            trailing = {
                MonoChip(
                    label = "导入词书",
                    selected = false,
                    onClick = { showImportDialog = true },
                    testTag = "vocab_import_button"
                )
            }
        )

        // Book switcher strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VOCAB_BOOKS.forEach { (id, label) ->
                MonoChip(
                    label = label,
                    selected = vocab.activeBookId == id,
                    onClick = { onSwitchBook(id) },
                    testTag = "vocab_book_$id"
                )
            }
        }

        val progress = (vocab.todayReviewed.toFloat() / state.config.vocabDailyGoal.coerceAtLeast(1)).coerceIn(0f, 1f)
        LinearProgressIndicator(
            progress = { progress },
            color = palette.accent,
            trackColor = palette.hairline,
            modifier = Modifier.fillMaxWidth().height(2.dp)
        )

        // Main Word Card Stage
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            if (word == null) {
                Spacer(modifier = Modifier.height(48.dp))
                Text(
                    text = if (vocab.activeBookId == "custom") {
                        "自定义词书为空，请点击右上角「导入词书」导入 TXT / CSV"
                    } else {
                        "正在加载离线词库…"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.inkMuted
                )
            } else {
                MonoCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 520.dp)
                        .testTag("vocab_word_card"),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = word.partOfSpeech.ifBlank { "WORD" }.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.accent
                            )
                            Text(
                                text = "词库 ${vocab.wordsInBook} 词",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.inkMuted
                            )
                        }

                        Text(
                            text = word.word,
                            fontFamily = ClockDisplayFont,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 36.sp,
                            lineHeight = 40.sp,
                            color = palette.ink,
                            modifier = Modifier.testTag("vocab_current_word")
                        )

                        if (word.phonetic.isNotBlank()) {
                            Text(
                                text = word.phonetic,
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.inkMuted
                            )
                        }

                        if (!vocab.showMeaning) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(palette.surfaceAlt)
                                    .border(1.dp, palette.hairline, RoundedCornerShape(10.dp))
                                    .clickable(onClick = onRevealMeaning)
                                    .testTag("vocab_reveal_button")
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "点击显示释义、词根与例句",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = palette.inkMuted
                                )
                            }
                        } else {
                            HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                            Text(
                                text = word.meaningZh,
                                style = MaterialTheme.typography.headlineMedium,
                                color = palette.ink,
                                modifier = Modifier.testTag("vocab_meaning_text")
                            )
                            if (word.rootBreakdown.isNotBlank()) {
                                Text(
                                    text = "词根拆解：${word.rootBreakdown}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.inkMuted
                                )
                            }
                            if (word.visualCue.isNotBlank() || word.mnemonicTag.isNotBlank()) {
                                Text(
                                    text = "场景助记：${word.mnemonicTag} · ${word.visualCue}".trim(' ', '·'),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.inkMuted
                                )
                            }
                            if (word.exampleEn.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = word.exampleEn,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = palette.ink
                                )
                                if (word.exampleZh.isNotBlank()) {
                                    Text(
                                        text = word.exampleZh,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = palette.inkMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Rating Bar
        if (word != null) {
            HorizontalDivider(thickness = 1.dp, color = palette.hairline)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.canvas)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MonoOutlineButton(
                    text = "忘记",
                    onClick = { onGradeWord(0) },
                    modifier = Modifier.weight(1f),
                    testTag = "vocab_grade_forgot"
                )
                MonoOutlineButton(
                    text = "模糊",
                    onClick = { onGradeWord(1) },
                    modifier = Modifier.weight(1f),
                    testTag = "vocab_grade_vague"
                )
                MonoPrimaryButton(
                    text = "认识",
                    onClick = { onGradeWord(2) },
                    modifier = Modifier.weight(1.1f),
                    testTag = "vocab_grade_know"
                )
                MonoPrimaryButton(
                    text = "斩词",
                    useAccent = true,
                    onClick = { onGradeWord(3) },
                    modifier = Modifier.weight(0.95f),
                    testTag = "vocab_grade_kill"
                )
            }
        }
    }

    if (showImportDialog) {
        var pathText by remember { mutableStateOf("/sdcard/Download/words.txt") }
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            containerColor = palette.surface,
            title = { Text("导入自定义词书", color = palette.ink) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "支持 TXT/CSV，每行格式：word,phonetic,meaning,example 或 word\\tmeaning。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.inkMuted
                    )
                    MonoOutlineButton(
                        text = "从文件管理器选择 TXT / CSV",
                        onClick = {
                            showImportDialog = false
                            onPickVocabFile()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pathText,
                        onValueChange = { pathText = it },
                        label = { Text("或直接输入本地路径") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                MonoPrimaryButton(
                    text = "按路径导入",
                    onClick = {
                        onImportVocabPath(pathText)
                        showImportDialog = false
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("取消", color = palette.inkMuted)
                }
            }
        )
    }
}
