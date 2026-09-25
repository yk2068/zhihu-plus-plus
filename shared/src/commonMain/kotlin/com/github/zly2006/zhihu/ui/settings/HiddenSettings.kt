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
 * 可被用户隐藏的账号页设置入口。
 *
 * 按需求，除「系统与更新」外的所有设置入口都支持隐藏：系统与更新页承载隐藏设置项本身，
 * 隐藏它会让用户失去恢复其他入口的唯一途径，因此固定展示。
 *
 * 隐藏只影响入口是否展示，不改变对应设置项的值，也不影响这些设置的运行时行为。
 * 被隐藏的入口在账号页直接消失，不再留提示行；要恢复请到「系统与更新 → 隐藏设置项」关闭对应开关。
 *
 * 新增可隐藏入口时在这里加一个枚举项即可，不要在设置页里散写 preference key。
 */
enum class HideableSettingGroup(
    val preferenceKey: String,
    val title: String,
    val description: String,
) {
    IDENTITY_MANAGEMENT(
        preferenceKey = "hideSettingIdentityManagement",
        title = "身份管理",
        description = "创建马甲号或切换当前账号",
    ),
    APPEARANCE(
        preferenceKey = "hideSettingAppearance",
        title = "外观与阅读体验",
        description = "主题颜色、字体大小等",
    ),
    READING(
        preferenceKey = "hideSettingReading",
        title = "朗读与播放",
        description = "朗读内容、播放队列与条目过渡",
    ),
    RECOMMEND(
        preferenceKey = "hideSettingRecommend",
        title = "推荐系统与内容过滤",
        description = "推荐、智能过滤、关键词屏蔽等",
    ),
    ;

    companion object {
        val all: List<HideableSettingGroup> = entries
    }
}

/**
 * 隐藏设置项的持久化状态。
 *
 * 隐藏是纯本地偏好：读取时缺省为「显示」，写入时只保存被隐藏的入口。
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
 * 供 Compose 观察某个入口是否被隐藏，并写回改动。
 *
 * 账号页用它控制入口可见性：设置页写回后账号页需要重组，所以这里用可变状态而不是一次性读取。
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
