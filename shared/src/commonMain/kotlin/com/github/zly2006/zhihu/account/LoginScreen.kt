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

package com.github.zly2006.zhihu.account

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
expect fun QrLoginPane(onLoginSuccess: (String) -> Unit)

@Composable
expect fun WebLoginPane(onLoginSuccess: (String) -> Unit)

/**
 * 登录页。
 *
 * 本 fork 只保留手机号登录，因此没有方式选择器：首次打开的声明页、扫码登录与
 * 备用网页登录都已移除，声明内容视为用户已知晓并同意，不再需要逐条确认。
 *
 * 扫码与网页登录的 `expect`/`actual` 仍保留在各平台源码中，但不再有入口；
 * 若要恢复多方式登录，重新加回方式枚举与选择器即可。
 */
@Composable
fun LoginScreen(
    onLoginComplete: () -> Unit,
) {
    var loggedInUsername by remember { mutableStateOf<String?>(null) }
    val onLoginSuccess: (String) -> Unit = { username -> loggedInUsername = username }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        PhoneLoginPane(onLoginSuccess)
    }

    loggedInUsername?.let { username ->
        AlertDialog(
            onDismissRequest = onLoginComplete,
            title = { Text("登录成功") },
            text = { Text("欢迎回来，$username") },
            confirmButton = {
                TextButton(onClick = onLoginComplete) {
                    Text("确定")
                }
            },
        )
    }
}
