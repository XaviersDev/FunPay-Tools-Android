/*
 *
 *  * Copyright (c) 2026 XaviersDev (AlliSighs). All rights reserved.
 *  *
 *  * This code is proprietary. Modification, distribution, or use
 *  * of this file without express written permission is strictly prohibited.
 *  * Unauthorized use will be prosecuted.
 *
 */

package ru.allisighs.funpaytools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsSelectorScreen(
    navController: NavController,
    repository: FunPayRepository,
    currentTheme: AppTheme
) {
    var accounts by remember { mutableStateOf(repository.getAllAccounts()) }

    var activeAccount by remember { mutableStateOf(repository.getActiveAccount()) }
    var showDeleteDialog by remember { mutableStateOf<Account?>(null) }
    var showProxyDialog by remember { mutableStateOf<Account?>(null) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Аккаунты",
                        color = ThemeManager.parseColor(currentTheme.textPrimaryColor),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            Icons.Default.ArrowBack,
                            "Назад",
                            tint = ThemeManager.parseColor(currentTheme.textPrimaryColor)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate("web_login") }) {
                        Icon(
                            Icons.Default.Add,
                            "Добавить аккаунт",
                            tint = ThemeManager.parseColor(currentTheme.textPrimaryColor)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ThemeManager.parseColor(currentTheme.surfaceColor)
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = ThemeManager.parseColor(currentTheme.surfaceColor).copy(alpha = currentTheme.containerOpacity)
                ),
                shape = RoundedCornerShape(currentTheme.borderRadius.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = ThemeManager.parseColor(currentTheme.accentColor),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Мультиаккаунты",
                            fontWeight = FontWeight.Bold,
                            color = ThemeManager.parseColor(currentTheme.textPrimaryColor),
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Вы можете включить фоновую работу для дополнительных аккаунтов. Настоятельно рекомендуем настроить прокси (🔑) для фоновых аккаунтов, чтобы избежать бана за частые запросы с одного IP.",
                            color = ThemeManager.parseColor(currentTheme.textSecondaryColor),
                            fontSize = 12.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            if (accounts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.PersonOff,
                            contentDescription = null,
                            tint = ThemeManager.parseColor(currentTheme.textSecondaryColor),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Нет аккаунтов",
                            color = ThemeManager.parseColor(currentTheme.textSecondaryColor),
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Нажмите + чтобы добавить",
                            color = ThemeManager.parseColor(currentTheme.textSecondaryColor).copy(alpha = 0.7f),
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(accounts, key = { it.id }) { account ->
                        AccountCard(
                            account = account,
                            isActive = account.id == activeAccount?.id,
                            theme = currentTheme,
                            onActivate = {
                                repository.setActiveAccount(account.id)
                                accounts = repository.getAllAccounts()
                                activeAccount = repository.getActiveAccount()
                            },
                            onDelete = {
                                showDeleteDialog = account
                            },
                            onEditProxy = {
                                showProxyDialog = account
                            },
                            onToggleBackground = { isEnabled ->
                                val updatedAcc = account.copy(runInBackground = isEnabled)
                                val accountsData = repository.getAccountsData()
                                val newList = accountsData.accounts.map { if (it.id == updatedAcc.id) updatedAcc else it }
                                repository.saveAccountsData(accountsData.copy(accounts = newList))
                                accounts = repository.getAllAccounts()
                            }
                        )
                    }
                }
            }
        }
    }

    showProxyDialog?.let { account ->
        ProxyDialog(
            account = account,
            theme = currentTheme,
            onSave = { updatedAcc ->
                val accountsData = repository.getAccountsData()
                val newList = accountsData.accounts.map { if (it.id == updatedAcc.id) updatedAcc else it }
                repository.saveAccountsData(accountsData.copy(accounts = newList))
                accounts = repository.getAllAccounts()
                if (activeAccount?.id == updatedAcc.id) {
                    activeAccount = updatedAcc
                }
                showProxyDialog = null
            },
            onDismiss = { showProxyDialog = null }
        )
    }

    showDeleteDialog?.let { account ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = {
                Text(
                    "Удалить аккаунт?",
                    color = ThemeManager.parseColor(currentTheme.textPrimaryColor)
                )
            },
            text = {
                Text(
                    "Вы уверены, что хотите удалить аккаунт ${account.username.ifEmpty { "без имени" }}?",
                    color = ThemeManager.parseColor(currentTheme.textSecondaryColor)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        repository.deleteAccount(account.id)
                        accounts = repository.getAllAccounts()
                        activeAccount = repository.getActiveAccount()
                        showDeleteDialog = null
                    }
                ) {
                    Text("Удалить", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text(
                        "Отмена",
                        color = ThemeManager.parseColor(currentTheme.textSecondaryColor)
                    )
                }
            },
            containerColor = ThemeManager.parseColor(currentTheme.surfaceColor),
            shape = RoundedCornerShape(currentTheme.borderRadius.dp)
        )
    }
}

@Composable
fun AccountCard(
    account: Account,
    isActive: Boolean,
    theme: AppTheme,
    onActivate: () -> Unit,
    onDelete: () -> Unit,
    onEditProxy: () -> Unit,
    onToggleBackground: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isActive) { onActivate() },
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                ThemeManager.parseColor(theme.accentColor).copy(alpha = 0.15f)
            } else {
                ThemeManager.parseColor(theme.surfaceColor).copy(alpha = theme.containerOpacity)
            }
        ),
        shape = RoundedCornerShape(theme.borderRadius.dp),
        border = if (isActive) {
            androidx.compose.foundation.BorderStroke(2.dp, ThemeManager.parseColor(theme.accentColor))
        } else null
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                if (account.avatarUrl.isNotEmpty()) {
                    AsyncImage(
                        model = account.avatarUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .border(
                                2.dp,
                                if (isActive) ThemeManager.parseColor(theme.accentColor)
                                else ThemeManager.parseColor(theme.textSecondaryColor).copy(alpha = 0.3f),
                                CircleShape
                            )
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(ThemeManager.parseColor(theme.accentColor).copy(alpha = 0.2f))
                            .border(
                                2.dp,
                                if (isActive) ThemeManager.parseColor(theme.accentColor)
                                else ThemeManager.parseColor(theme.textSecondaryColor).copy(alpha = 0.3f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, null, tint = ThemeManager.parseColor(theme.accentColor), modifier = Modifier.size(32.dp))
                    }
                }

                if (isActive) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(ThemeManager.parseColor(theme.accentColor))
                            .border(2.dp, Color.White, CircleShape)
                            .align(Alignment.BottomEnd)
                    ) {
                        Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(12.dp).align(Alignment.Center))
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.username.ifEmpty { "Аккаунт без имени" },
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = ThemeManager.parseColor(theme.textPrimaryColor)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (account.userId.isNotEmpty()) "ID: ${account.userId}" else "ID не определён",
                    fontSize = 12.sp,
                    color = ThemeManager.parseColor(theme.textSecondaryColor)
                )

                if (isActive) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("ОСНОВНОЙ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ThemeManager.parseColor(theme.accentColor))
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onToggleBackground(!account.runInBackground) }
                    ) {
                        Switch(
                            checked = account.runInBackground,
                            onCheckedChange = { onToggleBackground(it) },
                            modifier = Modifier.scale(0.7f),
                            colors = SwitchDefaults.colors(checkedThumbColor = ThemeManager.parseColor(theme.accentColor))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Работа в фоне",
                            fontSize = 12.sp,
                            color = if (account.runInBackground) ThemeManager.parseColor(theme.accentColor) else ThemeManager.parseColor(theme.textSecondaryColor)
                        )
                    }
                }
            }

            IconButton(onClick = onEditProxy, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.VpnKey,
                    contentDescription = "Прокси",
                    tint = if (!account.proxyHost.isNullOrBlank()) ThemeManager.parseColor(theme.accentColor) else ThemeManager.parseColor(theme.textSecondaryColor).copy(alpha = 0.5f)
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Delete, "Удалить", tint = Color.Red.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
fun ProxyDialog(
    account: Account,
    theme: AppTheme,
    onSave: (Account) -> Unit,
    onDismiss: () -> Unit
) {
    var proxyType by remember { mutableStateOf(if (!account.proxyType.isNullOrBlank()) account.proxyType else "HTTP") }
    var proxyHost by remember { mutableStateOf(account.proxyHost ?: "") }
    var proxyPort by remember { mutableStateOf(if ((account.proxyPort ?: 0) > 0) account.proxyPort.toString() else "") }
    var proxyUsername by remember { mutableStateOf(account.proxyUsername ?: "") }
    var proxyPassword by remember { mutableStateOf(account.proxyPassword ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Настройка прокси", color = ThemeManager.parseColor(theme.textPrimaryColor)) },
        text = {
            Column {
                Text("Прокси нужен для параллельной работы этого аккаунта в фоне, но не обязателен.",
                    fontSize = 12.sp, color = ThemeManager.parseColor(theme.textSecondaryColor))
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = proxyType == "HTTP",
                        onClick = { proxyType = "HTTP" },
                        label = { Text("HTTP(S)") }
                    )
                    FilterChip(
                        selected = proxyType == "SOCKS",
                        onClick = { proxyType = "SOCKS" },
                        label = { Text("SOCKS5") }
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = proxyHost,
                    onValueChange = { proxyHost = it },
                    label = { Text("Хост/IP") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = proxyPort,
                    onValueChange = { proxyPort = it.filter { c -> c.isDigit() } },
                    label = { Text("Порт") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = proxyUsername,
                    onValueChange = { proxyUsername = it },
                    label = { Text("Логин (необязательно)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = proxyPassword,
                    onValueChange = { proxyPassword = it },
                    label = { Text("Пароль (необязательно)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(account.copy(
                        proxyType = proxyType,
                        proxyHost = proxyHost.trim(),
                        proxyPort = proxyPort.toIntOrNull() ?: 0,
                        proxyUsername = proxyUsername.trim(),
                        proxyPassword = proxyPassword.trim()
                    ))
                },
                colors = ButtonDefaults.buttonColors(containerColor = ThemeManager.parseColor(theme.accentColor))
            ) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена", color = ThemeManager.parseColor(theme.textSecondaryColor)) }
        },
        containerColor = ThemeManager.parseColor(theme.surfaceColor)
    )
}