/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation (version 3 only).
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.zly2006.zhihu.ui.subscreens

import androidx.compose.runtime.Composable
import com.github.zly2006.zhihu.ui.TtsState

expect val isWebViewCustomFontSupported: Boolean

@Composable
expect fun WebViewCustomFontSettings(
    customFontName: String?,
    onCustomFontNameChange: (String?) -> Unit,
)

data class DeveloperInfoSnapshot(
    val networkStatus: String = "网络状态：未知",
    val powerSaveModeText: String? = null,
    val continuousUsageDurationMs: Long = 0L,
    val ttsState: TtsState = TtsState.Uninitialized,
    val currentTtsEngineLabel: String = "未初始化",
    val availableTtsEngineLabels: List<String> = emptyList(),
)

@Composable
expect fun rememberDeveloperInfo(): DeveloperInfoSnapshot
