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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.data.AIGC_MARKING_ENABLED_PREFERENCE_KEY
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.platform.platformName
import com.github.zly2006.zhihu.platform.rememberExternalUrlOpener
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.ui.components.SettingItem
import com.github.zly2006.zhihu.ui.components.SettingItemGroup
import com.github.zly2006.zhihu.ui.components.SettingItemWithSwitch
import com.github.zly2006.zhihu.ui.components.pageTurnViewportWithGuide
import com.github.zly2006.zhihu.ui.components.rememberPageTurnTarget
import com.github.zly2006.zhihu.ui.settings.HideableSettingGroup
import com.github.zly2006.zhihu.ui.settings.rememberGroupHidden
import com.github.zly2006.zhihu.util.ContinuousUsageReminderPolicy

internal const val CONTINUOUS_USAGE_REMINDER_INTERVAL_MINUTES_KEY = "continuousUsageReminderIntervalMinutes"
internal const val MACOS_QUIT_ON_WINDOW_CLOSE_PREFERENCE_KEY = "macosQuitOnWindowClose"
const val SYSTEM_SETTINGS_AIGC_MARKING_TAG = "system_settings_aigc_marking"

/**
 * 系统、更新和外部服务设置页。
 *
 * 页面展示 GitHub Token、遥测、AIGC 标记、隐藏设置项和防沉迷提醒。
 * 更新相关状态与动作由细粒度平台能力提供，防沉迷间隔写入 [CONTINUOUS_USAGE_REMINDER_INTERVAL_MINUTES_KEY]，
 * 改动时要同时考虑 Android 更新管理器和 Desktop 运行时。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemAndUpdateSettingsScreen(
    setting: String? = null,
) {
    val settings = rememberSettingsStore()
    val openExternalUrl = rememberExternalUrlOpener()
    val navigator = LocalNavigator.current
    val highlightedSetting = setting.orEmpty()

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            LargeTopAppBar(
                title = { Text("系统与更新") },
                navigationIcon = {
                    IconButton(
                        onClick = navigator.onNavigateBack,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors().copy(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            )
        },
    ) { innerPadding ->
        val scrollState = rememberScrollState()
        val pageTurnTarget = rememberPageTurnTarget(scrollState, enabled = true)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .pageTurnViewportWithGuide(pageTurnTarget)
                .verticalScroll(scrollState)
                .padding(innerPadding)
                .padding(vertical = 16.dp),
        ) {
            // GitHub Token。
            var githubToken by remember { mutableStateOf(settings.getString("githubToken", "")) }
            var showGithubToken by remember { mutableStateOf(false) }

            SettingItemGroup {
                SettingItem(
                    title = { Text("GitHub Token") },
                    description = {
                        Text(
                            "用于访问 GitHub API 时解除限速。留空则使用匿名访问，部分请求可能会因限速失败。",
                        )
                    },
                    settingKey = "githubToken",
                    highlightedKey = highlightedSetting,
                    bottomAction = {
                        OutlinedTextField(
                            value = githubToken,
                            onValueChange = {
                                githubToken = it
                                settings.putString("githubToken", it)
                            },
                            visualTransformation = if (showGithubToken) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showGithubToken = !showGithubToken }) {
                                    Icon(
                                        imageVector = if (showGithubToken) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = null,
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                            singleLine = true,
                        )
                    },
                )

                if (platformName == "macOS") {
                    var quitOnWindowClose by remember {
                        mutableStateOf(settings.getBoolean(MACOS_QUIT_ON_WINDOW_CLOSE_PREFERENCE_KEY, false))
                    }
                    SettingItemWithSwitch(
                        title = { Text("关闭窗口时退出应用") },
                        description = { Text("关闭最后一个窗口时同时退出 macOS 应用；默认关闭") },
                        checked = quitOnWindowClose,
                        onCheckedChange = {
                            quitOnWindowClose = it
                            settings.putBoolean(MACOS_QUIT_ON_WINDOW_CLOSE_PREFERENCE_KEY, it)
                        },
                        settingKey = MACOS_QUIT_ON_WINDOW_CLOSE_PREFERENCE_KEY,
                        highlightedKey = highlightedSetting,
                    )
                }

                var allowTelemetry by remember { mutableStateOf(settings.getBoolean("allowTelemetry", true)) }
                SettingItemWithSwitch(
                    title = { Text("允许发送遥测统计数据") },
                    description = { Text("仅用于统计使用人数，不包含个人隐私") },
                    checked = allowTelemetry,
                    onCheckedChange = {
                        allowTelemetry = it
                        settings.putBoolean("allowTelemetry", it)
                    },
                    settingKey = "allowTelemetry",
                    highlightedKey = highlightedSetting,
                )

                var aigcMarkingEnabled by remember {
                    mutableStateOf(settings.getBoolean(AIGC_MARKING_ENABLED_PREFERENCE_KEY, false))
                }
                SettingItemWithSwitch(
                    modifier = Modifier.testTag(SYSTEM_SETTINGS_AIGC_MARKING_TAG),
                    title = { Text("启用 AIGC 标记") },
                    description = {
                        Text(
                            "如果启用，会把你正在浏览的内容发送到我们的服务器，这样你可以知道其他用户是否认为其疑似 AIGC。默认关闭，不会发送隐私信息。",
                        )
                    },
                    checked = aigcMarkingEnabled,
                    onCheckedChange = {
                        aigcMarkingEnabled = it
                        settings.putBoolean(AIGC_MARKING_ENABLED_PREFERENCE_KEY, it)
                    },
                    settingKey = AIGC_MARKING_ENABLED_PREFERENCE_KEY,
                    highlightedKey = highlightedSetting,
                )
            }

            // 隐藏设置项：关闭对应入口的显示。只影响账号页入口可见性，不改动被隐藏设置本身的值。
            // 「系统与更新」不在此列——它就是本页入口，隐藏后用户将无法恢复任何入口。
            SettingItemGroup(
                title = "隐藏设置项",
                footer = { Text("开启后账号页不再显示对应入口；被隐藏入口会在原位置留一条「已隐藏」提示，点它即可恢复。") },
            ) {
                HideableSettingGroup.all.forEach { group ->
                    val (checked, setChecked) = rememberGroupHidden(group)
                    SettingItemWithSwitch(
                        modifier = Modifier.testTag("systemSettings:hide:${group.preferenceKey}"),
                        title = { Text(group.title) },
                        description = { Text(group.description) },
                        checked = checked,
                        onCheckedChange = setChecked,
                        settingKey = group.preferenceKey,
                        highlightedKey = highlightedSetting,
                    )
                }
            }

            var reminderExpanded by remember { mutableStateOf(false) }
            var reminderIntervalMinutes by remember {
                mutableIntStateOf(
                    ContinuousUsageReminderPolicy.normalizeIntervalMinutes(
                        settings.getInt(
                            CONTINUOUS_USAGE_REMINDER_INTERVAL_MINUTES_KEY,
                            0,
                        ),
                    ),
                )
            }
            val reminderOptions = listOf(
                0 to "关闭",
                15 to "每 15 分钟",
                30 to "每 30 分钟",
                60 to "每 1 小时",
            )

            SettingItemGroup(
                title = "防沉迷",
            ) {
                SettingItem(
                    title = { Text("防沉迷提醒") },
                    description = { Text("你已经连续浏览知乎 N 小时 M 分钟了，休息一下吧。退出后 5 分钟内重开仍视为连续使用。") },
                    settingKey = CONTINUOUS_USAGE_REMINDER_INTERVAL_MINUTES_KEY,
                    highlightedKey = highlightedSetting,
                    endAction = {
                        ExposedDropdownMenuBox(
                            expanded = reminderExpanded,
                            onExpandedChange = { reminderExpanded = it },
                        ) {
                            OutlinedTextField(
                                value = reminderOptions
                                    .find { it.first == reminderIntervalMinutes }
                                    ?.second ?: "关闭",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = reminderExpanded)
                                },
                                modifier = Modifier
                                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                    .width(160.dp),
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            )
                            ExposedDropdownMenu(
                                expanded = reminderExpanded,
                                onDismissRequest = { reminderExpanded = false },
                            ) {
                                reminderOptions.forEach { (minutes, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            reminderIntervalMinutes = minutes
                                            settings.putInt(CONTINUOUS_USAGE_REMINDER_INTERVAL_MINUTES_KEY, minutes)
                                            reminderExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}
