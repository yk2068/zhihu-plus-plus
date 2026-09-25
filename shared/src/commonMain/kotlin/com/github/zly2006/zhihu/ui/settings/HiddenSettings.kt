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

package com.github.zly2006.zhihu.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.github.zly2006.zhihu.platform.SettingsStore
import com.github.zly2006.zhihu.platform.rememberSettingsStore

/**
 * 可被用户隐藏的设置分组。
 *
 * 每一项对应账号页上的一个设置入口。隐藏只影响入口是否展示，不改变对应设置项的值，
 * 也不影响这些设置的运行时行为；重新打开显示即可恢复入口。
 *
 * 新增可隐藏入口时在这里加一个枚举项，并在 [HiddenSettingsState] 里补上读写分支，
 * 不要在设置页里散写 preference key。
 */
enum class HideableSettingGroup(
    val preferenceKey: String,
    val title: String,
    val description: String,
) {
    READING(
        preferenceKey = "hideSettingReading",
        title = "朗读与播放",
        description = "朗读内容、播放队列与条目过渡",
    ),
    ;

    companion object {
        val all: List<HideableSettingGroup> = entries
    }
}

/**
 * 隐藏设置项的持久化状态。
 *
 * 隐藏是纯本地偏好：读取时缺省为「显示」，写入时只保存被隐藏的分组，语义与设置页开关一致。
 */
class HiddenSettingsState internal constructor(
    private val settings: SettingsStore,
) {
    fun isHidden(group: HideableSettingGroup): Boolean = settings.getBoolean(group.preferenceKey, false)

    fun setHidden(group: HideableSettingGroup, hidden: Boolean) {
        settings.putBoolean(group.preferenceKey, hidden)
    }

    fun hiddenGroups(): Set<HideableSettingGroup> = HideableSettingGroup.all.filterTo(mutableSetOf()) { isHidden(it) }
}

/**
 * 读取隐藏设置状态。返回值随用户改动实时更新，设置页与账号页共享同一份偏好。
 */
@Composable
fun rememberHiddenSettingsState(): HiddenSettingsState {
    val settings = rememberSettingsStore()
    return remember(settings) { HiddenSettingsState(settings) }
}

/**
 * 供 Compose 直接观察某个分组是否被隐藏。
 *
 * 账号页用它控制入口可见性：设置页写回后需要重组账号页，所以这里用可变状态而不是一次性读取。
 */
@Composable
fun rememberGroupHidden(group: HideableSettingGroup): Pair<Boolean, (Boolean) -> Unit> {
    val settings = rememberSettingsStore()
    var hidden by remember(group.preferenceKey) {
        mutableStateOf(settings.getBoolean(group.preferenceKey, false))
    }
    val update: (Boolean) -> Unit = { value ->
        hidden = value
        settings.putBoolean(group.preferenceKey, value)
    }
    return hidden to update
}
