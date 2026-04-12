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

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import org.jsoup.Jsoup as JsoupParser
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip

@Composable
fun SupportScreenView(repository: FunPayRepository, currentTheme: AppTheme, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val support = remember { FunPaySupport(repository) }

    var allTickets by remember { mutableStateOf<List<SupportTicket>>(emptyList()) }
    var filteredTickets by remember { mutableStateOf<List<SupportTicket>>(emptyList()) }
    var selectedTicket by remember { mutableStateOf<TicketDetails?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showNotifDialog by remember { mutableStateOf(false) }
    var autoRefresh by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("active") }
    var sortOrder by remember { mutableStateOf("last_answered") }

    fun applyFilters() {
        var result = allTickets

        if (searchQuery.isNotBlank()) {
            result = result.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                        it.id.contains(searchQuery, ignoreCase = true)
            }
        }

        when (statusFilter) {
            "active" -> result = result.filter { it.status == "Открыт" || it.status == "В ожидании" }
            "solved" -> result = result.filter { it.status == "Закрыт" || it.status == "Решена" }
        }

        when (sortOrder) {
            "newest_first" -> result = result.sortedByDescending { it.id }
            "oldest_first" -> result = result.sortedBy { it.id }
            "last_answered" -> result = result
        }

        filteredTickets = result
    }

    LaunchedEffect(Unit) {
        support.init()
        allTickets = support.getTicketsList()
        applyFilters()
        isLoading = false

        while (autoRefresh) {
            delay(10000)
            if (selectedTicket == null) {
                allTickets = support.getTicketsList()
                applyFilters()
            }
        }
    }

    LaunchedEffect(searchQuery, statusFilter, sortOrder) {
        applyFilters()
    }

    DisposableEffect(Unit) {
        onDispose {
            autoRefresh = false
        }
    }

    BackHandler {
        if (selectedTicket != null) selectedTicket = null else onBack()
    }

    Box(modifier = Modifier.fillMaxSize().background(AppGradient)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Топбар: только стрелка назад + контекстный заголовок + колокол
            Surface(
                color = ThemeManager.parseColor(currentTheme.surfaceColor),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        if (selectedTicket != null) selectedTicket = null else onBack()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                            tint = ThemeManager.parseColor(currentTheme.textPrimaryColor)
                        )
                    }
                    Text(
                        text = if (selectedTicket != null) "Заявка #${selectedTicket!!.id}" else "Заявки",
                        modifier = Modifier.weight(1f),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = ThemeManager.parseColor(currentTheme.textPrimaryColor)
                    )
                    if (selectedTicket == null) {
                        IconButton(onClick = { showNotifDialog = true }) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = "Уведомления",
                                tint = ThemeManager.parseColor(currentTheme.accentColor)
                            )
                        }
                    }
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ThemeManager.parseColor(currentTheme.accentColor))
                }
            } else if (selectedTicket != null) {
                TicketDetailsView(
                    ticket = selectedTicket!!,
                    support = support,
                    theme = currentTheme,
                    onUpdate = {
                        scope.launch {
                            val updated = support.getTicketDetails(selectedTicket!!.id)
                            selectedTicket = updated?.copy(title = selectedTicket!!.title)
                        }
                    }
                )
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    TicketsListView(
                        tickets = filteredTickets,
                        theme = currentTheme,
                        searchQuery = searchQuery,
                        onSearchChange = { searchQuery = it },
                        statusFilter = statusFilter,
                        onStatusFilterChange = { statusFilter = it },
                        sortOrder = sortOrder,
                        onSortOrderChange = { sortOrder = it },
                        onTicketClick = { ticket ->
                            scope.launch {
                                val details = support.getTicketDetails(ticket.id)
                                selectedTicket = details?.copy(title = ticket.title)
                            }
                        }
                    )
                    // FABs — обновить и создать в правом нижнем углу
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                            .navigationBarsPadding(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        SmallFloatingActionButton(
                            onClick = {
                                scope.launch {
                                    isLoading = true
                                    allTickets = support.getTicketsList()
                                    applyFilters()
                                    isLoading = false
                                }
                            },
                            containerColor = ThemeManager.parseColor(currentTheme.surfaceColor),
                            contentColor = ThemeManager.parseColor(currentTheme.accentColor)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                        }
                        FloatingActionButton(
                            onClick = { showCreateDialog = true },
                            containerColor = ThemeManager.parseColor(currentTheme.accentColor),
                            contentColor = Color.White
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Создать заявку")
                        }
                    }
                }
            }
        }

        if (showCreateDialog) {
            CreateTicketDialog(
                support = support,
                theme = currentTheme,
                onDismiss = {
                    showCreateDialog = false
                    errorMessage = null
                },
                onCreated = { newTicketId ->
                    showCreateDialog = false
                    errorMessage = null
                    scope.launch {
                        allTickets = support.getTicketsList()
                        applyFilters()
                        val newTicket = allTickets.find { it.id == newTicketId }
                        if (newTicket != null) {
                            val details = support.getTicketDetails(newTicketId)
                            selectedTicket = details?.copy(title = newTicket.title)
                        }
                    }
                },
                errorMessage = errorMessage,
                onErrorDismiss = { errorMessage = null },
                onError = { error -> errorMessage = error }
            )
        }

        if (showNotifDialog) {
            NotificationSettingsDialog(
                support = support,
                theme = currentTheme,
                onDismiss = { showNotifDialog = false }
            )
        }
    }
}

@Composable
fun TicketsListView(
    tickets: List<SupportTicket>,
    theme: AppTheme,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    statusFilter: String,
    onStatusFilterChange: (String) -> Unit,
    sortOrder: String,
    onSortOrderChange: (String) -> Unit,
    onTicketClick: (SupportTicket) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    "Поиск по заявкам...",
                    color = ThemeManager.parseColor(theme.textSecondaryColor)
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Поиск",
                    tint = ThemeManager.parseColor(theme.textSecondaryColor)
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor),
                unfocusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor),
                focusedBorderColor = ThemeManager.parseColor(theme.accentColor),
                unfocusedBorderColor = ThemeManager.parseColor(theme.textSecondaryColor).copy(alpha = 0.3f),
                cursorColor = ThemeManager.parseColor(theme.accentColor)
            ),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Статус",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = ThemeManager.parseColor(theme.textPrimaryColor)
                )
                Spacer(modifier = Modifier.height(4.dp))
                var expandedStatus by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(
                        onClick = { expandedStatus = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = ThemeManager.parseColor(theme.textPrimaryColor)
                        )
                    ) {
                        Text(
                            when (statusFilter) {
                                "all" -> "Все"
                                "active" -> "Актуальные"
                                "solved" -> "Закрытые"
                                else -> "Все"
                            },
                            fontSize = 14.sp
                        )
                    }
                    DropdownMenu(
                        expanded = expandedStatus,
                        onDismissRequest = { expandedStatus = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Все") },
                            onClick = {
                                onStatusFilterChange("all")
                                expandedStatus = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Актуальные") },
                            onClick = {
                                onStatusFilterChange("active")
                                expandedStatus = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Закрытые") },
                            onClick = {
                                onStatusFilterChange("solved")
                                expandedStatus = false
                            }
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Сортировка",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = ThemeManager.parseColor(theme.textPrimaryColor)
                )
                Spacer(modifier = Modifier.height(4.dp))
                var expandedSort by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(
                        onClick = { expandedSort = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = ThemeManager.parseColor(theme.textPrimaryColor)
                        )
                    ) {
                        Text(
                            when (sortOrder) {
                                "newest_first" -> "Сначала новые"
                                "oldest_first" -> "Сначала старые"
                                "last_answered" -> "Последние отвеченные"
                                else -> "Сортировка"
                            },
                            fontSize = 14.sp
                        )
                    }
                    DropdownMenu(
                        expanded = expandedSort,
                        onDismissRequest = { expandedSort = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Сначала новые") },
                            onClick = {
                                onSortOrderChange("newest_first")
                                expandedSort = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Сначала старые") },
                            onClick = {
                                onSortOrderChange("oldest_first")
                                expandedSort = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Последние отвеченные") },
                            onClick = {
                                onSortOrderChange("last_answered")
                                expandedSort = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "Мои заявки (${tickets.size})",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = ThemeManager.parseColor(theme.textPrimaryColor)
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (tickets.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Нет заявок",
                    color = ThemeManager.parseColor(theme.textSecondaryColor),
                    fontSize = 16.sp
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(tickets) { ticket ->
                    TicketCard(ticket, theme, onClick = { onTicketClick(ticket) })
                }
            }
        }
    }
}

@Composable
fun TicketCard(ticket: SupportTicket, theme: AppTheme, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = ThemeManager.parseColor(theme.surfaceColor).copy(alpha = theme.containerOpacity)
        ),
        shape = RoundedCornerShape(theme.borderRadius.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Заявка #${ticket.id}",
                    fontSize = 14.sp,
                    color = ThemeManager.parseColor(theme.accentColor),
                    fontWeight = FontWeight.Bold
                )

                val sc = when (ticket.status) {
                    "Открыт" -> Color(0xFF4CAF50)
                    "В ожидании" -> Color(0xFFFFC107)
                    "Решена" -> Color(0xFF4CAF50)
                    else -> ThemeManager.parseColor(theme.textSecondaryColor).copy(alpha = 0.7f)
                }
                Surface(
                    color = sc.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        ticket.status,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 11.sp,
                        color = sc,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                ticket.title,
                fontSize = 15.sp,
                color = ThemeManager.parseColor(theme.textPrimaryColor),
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                ticket.lastUpdate,
                fontSize = 12.sp,
                color = ThemeManager.parseColor(theme.textSecondaryColor)
            )
        }
    }
}

@Composable
fun TicketDetailsView(
    ticket: TicketDetails,
    support: FunPaySupport,
    theme: AppTheme,
    onUpdate: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isSending by remember { mutableStateOf(false) }
    var showInfoPanel by remember { mutableStateOf(false) }
    var isClosing by remember { mutableStateOf(false) }
    var isUploadingAttachment by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) } // Текст ошибки от сервера
    var pendingAttachmentIds by remember { mutableStateOf<List<String>>(emptyList()) }
    val context = androidx.compose.ui.platform.LocalContext.current

    val fileLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isUploadingAttachment = true
                actionError = null
                try {
                    val stream = context.contentResolver.openInputStream(uri)
                    val bytes = stream?.readBytes()
                    stream?.close()
                    val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                    val fileName = context.contentResolver.query(
                        uri,
                        arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                        null, null, null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    } ?: run {
                        val ext = android.webkit.MimeTypeMap.getSingleton()
                            .getExtensionFromMimeType(mimeType) ?: "bin"
                        "file.$ext"
                    }
                    if (bytes != null) {
                        val id = support.uploadAttachment(ticket.id, fileName, mimeType, bytes)
                        if (id != null) pendingAttachmentIds = pendingAttachmentIds + id
                    }
                } catch (e: Exception) {
                    actionError = "Вложение: ${e.message}"
                }
                isUploadingAttachment = false
            }
        }
    }

    // Окно сообщения об ошибке
    if (actionError != null) {
        AlertDialog(
            onDismissRequest = { actionError = null },
            title = { Text("Ошибка", color = Color.Red, fontWeight = FontWeight.Bold) },
            text = { Text(actionError!!, color = ThemeManager.parseColor(theme.textPrimaryColor)) },
            confirmButton = {
                TextButton(onClick = { actionError = null }) {
                    Text("Понятно", color = ThemeManager.parseColor(theme.accentColor))
                }
            },
            containerColor = ThemeManager.parseColor(theme.surfaceColor),
            shape = RoundedCornerShape(16.dp)
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().imePadding()) {

            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = ThemeManager.parseColor(theme.surfaceColor).copy(alpha = theme.containerOpacity)
                ),
                shape = RoundedCornerShape(theme.borderRadius.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "#${ticket.id}",
                                    fontSize = 16.sp,
                                    color = ThemeManager.parseColor(theme.accentColor),
                                    fontWeight = FontWeight.Bold
                                )

                                val sc = when (ticket.status) {
                                    "Открыт" -> Color(0xFF4CAF50)
                                    "В ожидании" -> Color(0xFFFFC107)
                                    "Решена" -> Color(0xFF4CAF50)
                                    "Закрыт" -> ThemeManager.parseColor(theme.textSecondaryColor).copy(alpha = 0.6f)
                                    else -> ThemeManager.parseColor(theme.textSecondaryColor).copy(alpha = 0.6f)
                                }
                                Surface(color = sc.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                                    Text(
                                        ticket.status,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        fontSize = 12.sp, color = sc, fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                ticket.title,
                                fontSize = 18.sp,
                                color = ThemeManager.parseColor(theme.textPrimaryColor),
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 24.sp
                            )
                        }
                        IconButton(onClick = { showInfoPanel = !showInfoPanel }) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = ThemeManager.parseColor(theme.accentColor))
                        }
                    }

                    if (ticket.canReply) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isClosing = true
                                    try {
                                        if (support.closeTicket(ticket.id)) onUpdate()
                                    } catch (e: Exception) { actionError = e.message }
                                    isClosing = false
                                }
                            },
                            enabled = !isClosing,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF5350)),
                            border = BorderStroke(1.dp, Color(0xFFEF5350).copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isClosing) "Закрытие..." else "Закрыть заявку")
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(ticket.comments) { comment ->
                    CommentBubble(comment, theme)
                }
            }

            if (ticket.canReply) {
                MessageInputWithFormatting(
                    theme = theme,
                    isSending = isSending,
                    isUploadingAttachment = isUploadingAttachment,
                    pendingAttachmentCount = pendingAttachmentIds.size,
                    onSend = { text ->
                        scope.launch {
                            isSending = true
                            actionError = null
                            try {
                                val ids = pendingAttachmentIds
                                if (support.addComment(ticket.id, text, ids)) {
                                    pendingAttachmentIds = emptyList()
                                    onUpdate()
                                }
                            } catch (e: Exception) {
                                // ПОЙМАЛИ ОШИБКУ ОТ FunPay
                                actionError = e.message
                            } finally {
                                isSending = false
                            }
                        }
                    },
                    onAttach = { fileLauncher.launch("*/*") }
                )
            }
        }

        if (showInfoPanel) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
                    .clickable(onClick = { showInfoPanel = false })
            ) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .width(280.dp)
                        .clickable(enabled = false) { },
                    colors = CardDefaults.cardColors(containerColor = ThemeManager.parseColor(theme.surfaceColor)),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Заявка #${ticket.id}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ThemeManager.parseColor(theme.textPrimaryColor), modifier = Modifier.weight(1f))
                            IconButton(onClick = { showInfoPanel = false }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, null, tint = ThemeManager.parseColor(theme.textSecondaryColor), modifier = Modifier.size(18.dp))
                            }
                        }
                        HorizontalDivider(color = ThemeManager.parseColor(theme.textSecondaryColor).copy(alpha = 0.15f))
                        if (ticket.createdAt.isNotEmpty()) CompactInfoRow("Создана", ticket.createdAt, theme)
                        if (ticket.responsible.isNotEmpty()) CompactInfoRow("Ответственный", ticket.responsible, theme)
                        CompactInfoRow("Комментариев", ticket.comments.size.toString(), theme)
                        if (ticket.additionalFields.isNotEmpty()) {
                            HorizontalDivider(color = ThemeManager.parseColor(theme.textSecondaryColor).copy(alpha = 0.15f))
                            ticket.additionalFields.forEach { (key, value) -> CompactInfoRow(key, value, theme) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CompactInfoRow(label: String, value: String, theme: AppTheme, highlighted: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            fontSize = 11.sp,
            color = ThemeManager.parseColor(theme.textSecondaryColor),
            fontWeight = FontWeight.Medium,
            modifier = Modifier.widthIn(min = 60.dp, max = 100.dp),
            softWrap = true
        )
        SelectionContainer {
            Text(
                value,
                fontSize = 13.sp,
                color = if (highlighted)
                    ThemeManager.parseColor(theme.accentColor)
                else
                    ThemeManager.parseColor(theme.textPrimaryColor),
                fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(0.6f)
            )
        }
    }
}

@Composable
fun CommentBubble(comment: TicketComment, theme: AppTheme) {
    val uriHandler = LocalUriHandler.current
    val textColor = if (comment.isMyComment) Color.White else ThemeManager.parseColor(theme.textPrimaryColor)
    val secondaryColor = if (comment.isMyComment) Color.White.copy(alpha = 0.7f) else ThemeManager.parseColor(theme.textSecondaryColor)

    val initials = comment.author.trim().split(" ").take(2).joinToString("") { it.take(1) }.uppercase().ifEmpty { "?" }
    val avatarColor = ThemeManager.parseColor(theme.accentColor).copy(alpha = 0.7f)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (comment.isMyComment) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!comment.isMyComment) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(avatarColor, androidx.compose.foundation.shape.CircleShape)
                    .clip(androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (comment.avatarUrl.isNotEmpty()) {
                    coil.compose.AsyncImage(
                        model = comment.avatarUrl,
                        contentDescription = comment.author,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(androidx.compose.foundation.shape.CircleShape),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Text(initials, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
        }
        Card(
            modifier = Modifier.widthIn(max = 400.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (comment.isMyComment)
                    ThemeManager.parseColor(theme.accentColor)
                else
                    ThemeManager.parseColor(theme.surfaceColor).copy(alpha = 0.7f)
            ),
            shape = RoundedCornerShape(
                topStart = if (comment.isMyComment) 12.dp else 4.dp,
                topEnd = if (comment.isMyComment) 4.dp else 12.dp,
                bottomStart = 12.dp,
                bottomEnd = 12.dp
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (!comment.isMyComment) {
                    Text(
                        comment.author,
                        fontSize = 12.sp,
                        color = ThemeManager.parseColor(theme.accentColor),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                SelectionContainer {
                    val annotatedText = buildAnnotatedString {
                        // Нормализуем HTML: убираем лишние переносы перед парсингом
                        val rawHtml = comment.htmlText.ifEmpty { comment.text }
                        val htmlSource = rawHtml
                            // <p><br></p> и <p></p> — пустые параграфы
                            .replace(Regex("<p>\\s*(<br\\s*/?>)?\\s*</p>"), "")
                            // Пробельные символы между тегами — whitespace TextNodes
                            .replace(Regex(">\\s+<"), "><")
                            // Несколько br подряд → один
                            .replace(Regex("(<br\\s*/?>){2,}"), "<br>")
                            .trim()
                        val linkColor = if (comment.isMyComment) Color.White else ThemeManager.parseColor(theme.accentColor)

                        fun appendNode(node: org.jsoup.nodes.Node, bold: Boolean = false, italic: Boolean = false, underline: Boolean = false) {
                            when (node) {
                                is org.jsoup.nodes.TextNode -> {
                                    val txt = node.wholeText
                                    if (txt.isBlank()) return
                                    withStyle(SpanStyle(
                                        color = textColor,
                                        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                                        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
                                        textDecoration = if (underline) TextDecoration.Underline else null
                                    )) { append(txt) }
                                }
                                is org.jsoup.nodes.Element -> {
                                    val tag = node.tagName().lowercase()
                                    val nb = bold || tag in listOf("b", "strong")
                                    val ni = italic || tag in listOf("i", "em")
                                    val nu = underline || tag == "u"
                                    when (tag) {
                                        "br" -> append("\n")
                                        "p", "div" -> {
                                            // Только если перед нами уже что-то есть — разделяем одним переносом
                                            if (length > 0 && toString().last() != '\n') append("\n")
                                            node.childNodes().forEach { appendNode(it, nb, ni, nu) }
                                        }
                                        "li" -> {
                                            append("• ")
                                            node.childNodes().forEach { appendNode(it, nb, ni, nu) }
                                            if (length > 0 && toString().last() != '\n') append("\n")
                                        }
                                        "a" -> {
                                            val href = node.attr("abs:href").ifEmpty { node.attr("href") }
                                            if (href.isNotEmpty()) {
                                                pushStringAnnotation("URL", href)
                                                withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                                                    node.childNodes().forEach { appendNode(it, nb, ni, nu) }
                                                }
                                                pop()
                                            } else node.childNodes().forEach { appendNode(it, nb, ni, nu) }
                                        }
                                        else -> node.childNodes().forEach { appendNode(it, nb, ni, nu) }
                                    }
                                }
                            }
                        }

                        JsoupParser.parseBodyFragment(htmlSource).body().childNodes().forEach { appendNode(it) }
                    }
                    Text(
                        text = annotatedText,
                        fontSize = 14.sp,
                        color = textColor,
                        lineHeight = 20.sp,
                        modifier = Modifier.clickable {
                            annotatedText.getStringAnnotations("URL", 0, annotatedText.length)
                                .firstOrNull()?.let { uriHandler.openUri(it.item) }
                        }
                    )
                }

                if (comment.attachments.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    comment.attachments.forEach { att ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { uriHandler.openUri(att.url) }
                                .background(Color.Black.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                if (att.isImage) Icons.Default.Image else Icons.Default.AttachFile,
                                contentDescription = null,
                                tint = secondaryColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                att.name,
                                fontSize = 12.sp,
                                color = secondaryColor,
                                textDecoration = TextDecoration.Underline,
                                maxLines = 1
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(comment.timestamp, fontSize = 10.sp, color = secondaryColor)
            }
        }
    }
}

@Composable
fun MessageInputWithFormatting(
    theme: AppTheme,
    isSending: Boolean,
    isUploadingAttachment: Boolean = false,
    pendingAttachmentCount: Int = 0,
    onSend: (String) -> Unit,
    onAttach: (() -> Unit)? = null
) {
    var text by remember { mutableStateOf("") }
    val accentColor = ThemeManager.parseColor(theme.accentColor)
    val textPrimary = ThemeManager.parseColor(theme.textPrimaryColor)
    val textSecondary = ThemeManager.parseColor(theme.textSecondaryColor)
    val surface = ThemeManager.parseColor(theme.surfaceColor)
    val hasText = text.isNotBlank()
    val canSend = (hasText || pendingAttachmentCount > 0) && !isSending && !isUploadingAttachment

    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        // Индикатор загруженных вложений
        if (pendingAttachmentCount > 0 || isUploadingAttachment) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isUploadingAttachment) {
                    CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp, color = accentColor)
                    Text("Загрузка...", fontSize = 12.sp, color = textSecondary)
                } else {
                    Icon(Icons.Default.AttachFile, null, tint = accentColor, modifier = Modifier.size(13.dp))
                    Text("$pendingAttachmentCount файл(а) прикреплено", fontSize = 12.sp, color = accentColor)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Кнопка вложений
            if (onAttach != null) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .align(Alignment.Bottom)
                        .clip(CircleShape)
                        .clickable(enabled = !isSending && !isUploadingAttachment, onClick = onAttach),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.AttachFile,
                        contentDescription = "Прикрепить",
                        tint = textSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Поле ввода в пузыре
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                enabled = !isSending,
                placeholder = { Text("Написать ответ...", color = textSecondary.copy(alpha = 0.6f), fontSize = 15.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = textPrimary,
                    unfocusedTextColor = textPrimary,
                    cursorColor = accentColor,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = surface,
                    unfocusedContainerColor = surface,
                    disabledBorderColor = Color.Transparent,
                    disabledContainerColor = surface
                ),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp),
                shape = RoundedCornerShape(24.dp),
                maxLines = 6,
                modifier = Modifier.weight(1f)
            )

            // Кнопка отправки — иконка в круге
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .align(Alignment.Bottom)
                    .clip(CircleShape)
                    .background(if (canSend) accentColor else textSecondary.copy(alpha = 0.25f))
                    .clickable(enabled = canSend) {
                        onSend(text.trim())
                        text = ""
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "Отправить",
                        tint = if (canSend) Color.White else textSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        } // end Row
    } // end Column
}

@Composable
fun NotificationSettingsDialog(support: FunPaySupport, theme: AppTheme, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var emailEnabled by remember { mutableStateOf(false) }
    var csrfToken by remember { mutableStateOf<String?>(null) }
    var statusMsg by remember { mutableStateOf<Pair<Boolean, String>?>(null) } // true=ok, false=err

    LaunchedEffect(Unit) {
        val (checked, token) = support.getNotificationSettings()
        emailEnabled = checked
        csrfToken = token
        isLoading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ThemeManager.parseColor(theme.surfaceColor),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Notifications, null, tint = ThemeManager.parseColor(theme.accentColor), modifier = Modifier.size(22.dp))
                Text("Уведомления", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = ThemeManager.parseColor(theme.textPrimaryColor))
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    color = ThemeManager.parseColor(theme.accentColor).copy(alpha = 0.08f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Email, null, tint = ThemeManager.parseColor(theme.accentColor), modifier = Modifier.size(15.dp))
                        Text(
                            "Уведомления отправляются на электронную почту, а не в это приложение.",
                            fontSize = 12.sp, color = ThemeManager.parseColor(theme.textSecondaryColor), lineHeight = 17.sp
                        )
                    }
                }
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), color = ThemeManager.parseColor(theme.accentColor), strokeWidth = 2.dp)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { emailEnabled = !emailEnabled }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Checkbox(
                            checked = emailEnabled,
                            onCheckedChange = { emailEnabled = it },
                            colors = CheckboxDefaults.colors(checkedColor = ThemeManager.parseColor(theme.accentColor))
                        )
                        Text("Получен ответ на заявку", fontSize = 14.sp, color = ThemeManager.parseColor(theme.textPrimaryColor))
                    }
                }
                statusMsg?.let { (ok, msg) ->
                    Text(msg, fontSize = 12.sp, color = if (ok) Color(0xFF4CAF50) else Color(0xFFEF5350))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val token = csrfToken ?: return@Button
                    scope.launch {
                        isSaving = true
                        statusMsg = null
                        val ok = support.saveNotificationSettings(emailEnabled, token)
                        statusMsg = if (ok) Pair(true, "✓ Настройки сохранены") else Pair(false, "Не удалось сохранить")
                        isSaving = false
                    }
                },
                enabled = !isLoading && !isSaving && csrfToken != null,
                colors = ButtonDefaults.buttonColors(containerColor = ThemeManager.parseColor(theme.accentColor))
            ) { Text(if (isSaving) "Сохранение..." else "Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть", color = ThemeManager.parseColor(theme.textSecondaryColor)) }
        }
    )
}

@Composable
fun CreateTicketDialog(
    support: FunPaySupport,
    theme: AppTheme,
    onDismiss: () -> Unit,
    onCreated: (String) -> Unit,
    errorMessage: String?,
    onErrorDismiss: () -> Unit,
    onError: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var categories by remember { mutableStateOf<List<TicketCategory>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf<TicketCategory?>(null) }
    var categoryFields by remember { mutableStateOf<List<TicketField>>(emptyList()) }
    var fieldValues by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var message by remember { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }
    var isLoadingFields by remember { mutableStateOf(false) }
    var isLoadingCategories by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        isLoadingCategories = true
        categories = support.getCategories()
        isLoadingCategories = false
    }

    LaunchedEffect(selectedCategory) {
        if (selectedCategory != null) {
            isLoadingFields = true
            val fields = support.getCategoryFields(selectedCategory!!.id)
            categoryFields = fields

            fieldValues = fields.associate { it.id to it.defaultValue }
            isLoadingFields = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Новая заявка",
                color = ThemeManager.parseColor(theme.textPrimaryColor)
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                errorMessage?.let { error ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFF5252)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                error,
                                modifier = Modifier.weight(1f),
                                fontSize = 13.sp,
                                color = Color.White,
                                lineHeight = 18.sp
                            )
                            IconButton(
                                onClick = onErrorDismiss,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Text("×", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = if (errorMessage != null) 400.dp else 500.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text("Выберите категорию", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }

                    item {
                        if (isLoadingCategories) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    color = ThemeManager.parseColor(theme.accentColor)
                                )
                            }
                        } else if (categories.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Не удалось загрузить категории",
                                    fontSize = 12.sp,
                                    color = ThemeManager.parseColor(theme.textSecondaryColor)
                                )
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                categories.forEach { category ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedCategory = category },
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (selectedCategory?.id == category.id)
                                                ThemeManager.parseColor(theme.accentColor).copy(alpha = 0.2f)
                                            else
                                                ThemeManager.parseColor(theme.surfaceColor).copy(alpha = 0.5f)
                                        )
                                    ) {
                                        Text(
                                            category.name,
                                            modifier = Modifier.padding(12.dp),
                                            color = ThemeManager.parseColor(theme.textPrimaryColor)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (selectedCategory != null) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Заполните поля", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }

                        if (isLoadingFields) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(32.dp),
                                        color = ThemeManager.parseColor(theme.accentColor)
                                    )
                                }
                            }
                        } else {
                            items(categoryFields) { field ->
                                val shouldShow = if (field.condition != null) {
                                    try {
                                        val conditionJson = JSONObject(field.condition)
                                        val conditionType = conditionJson.optString("type")
                                        val targetFieldId = conditionJson.optInt("fieldId")
                                        val expectedValue = conditionJson.optInt("value")

                                        val targetFieldName = "ticket[fields][$targetFieldId]"
                                        val currentValue = fieldValues[targetFieldName]

                                        when (conditionType) {
                                            "equals" -> {
                                                currentValue == expectedValue.toString() ||
                                                        currentValue?.toIntOrNull() == expectedValue
                                            }
                                            else -> false
                                        }
                                    } catch (e: Exception) {
                                        LogManager.addLog("⚠️ Failed to parse condition: ${field.condition}")
                                        false
                                    }
                                } else {
                                    true
                                }

                                if (shouldShow) {
                                    TicketFieldInput(field, fieldValues, theme) { newValue ->
                                        fieldValues = fieldValues + (field.id to newValue)
                                    }
                                }
                            }
                        }

                        item {
                            Column {
                                Text(
                                    "Сообщение *",
                                    fontSize = 12.sp,
                                    color = ThemeManager.parseColor(theme.textSecondaryColor)
                                )
                                OutlinedTextField(
                                    value = message,
                                    onValueChange = { message = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 3,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor),
                                        unfocusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor),
                                        focusedBorderColor = ThemeManager.parseColor(theme.accentColor),
                                        unfocusedBorderColor = ThemeManager.parseColor(theme.textSecondaryColor).copy(alpha = 0.3f),
                                        cursorColor = ThemeManager.parseColor(theme.accentColor)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        try {
                            isCreating = true
                            val ticketId = support.createTicket(
                                selectedCategory!!.id,
                                fieldValues,
                                message
                            )
                            isCreating = false
                            if (ticketId != null) {
                                onCreated(ticketId)
                            }
                        } catch (e: Exception) {
                            isCreating = false
                            onError(e.message ?: "Неизвестная ошибка")
                        }
                    }
                },
                enabled = selectedCategory != null && message.isNotBlank() && !isCreating && !isLoadingCategories,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ThemeManager.parseColor(theme.accentColor)
                )
            ) {
                Text(if (isCreating) "Создание..." else "Создать")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "Отмена",
                    color = ThemeManager.parseColor(theme.textSecondaryColor)
                )
            }
        },
        containerColor = ThemeManager.parseColor(theme.surfaceColor)
    )
}

@Composable
fun TicketFieldInput(
    field: TicketField,
    fieldValues: Map<String, String>,
    theme: AppTheme,
    onValueChange: (String) -> Unit
) {
    Column {
        Text(
            field.name + if (field.required) " *" else "",
            fontSize = 12.sp,
            color = ThemeManager.parseColor(theme.textSecondaryColor)
        )

        when {
            field.type == "select" && field.options.isNotEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            ThemeManager.parseColor(theme.textSecondaryColor).copy(alpha = 0.3f),
                            RoundedCornerShape(4.dp)
                        )
                ) {
                    field.options.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onValueChange(option.value) }
                                .background(
                                    if (fieldValues[field.id] == option.value)
                                        ThemeManager.parseColor(theme.accentColor).copy(alpha = 0.2f)
                                    else
                                        Color.Transparent
                                )
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = fieldValues[field.id] == option.value,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = ThemeManager.parseColor(theme.accentColor)
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                option.text,
                                color = ThemeManager.parseColor(theme.textPrimaryColor)
                            )
                        }
                    }
                }
            }
            field.type == "radio" && field.options.isNotEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            ThemeManager.parseColor(theme.textSecondaryColor).copy(alpha = 0.3f),
                            RoundedCornerShape(4.dp)
                        )
                ) {
                    field.options.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onValueChange(option.value) }
                                .background(
                                    if (fieldValues[field.id] == option.value)
                                        ThemeManager.parseColor(theme.accentColor).copy(alpha = 0.2f)
                                    else
                                        Color.Transparent
                                )
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = fieldValues[field.id] == option.value,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = ThemeManager.parseColor(theme.accentColor)
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                option.text,
                                color = ThemeManager.parseColor(theme.textPrimaryColor)
                            )
                        }
                    }
                }
            }
            else -> {
                OutlinedTextField(
                    value = fieldValues[field.id] ?: "",
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = if (field.type == "textarea") 3 else 1,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor),
                        unfocusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor),
                        focusedBorderColor = ThemeManager.parseColor(theme.accentColor),
                        unfocusedBorderColor = ThemeManager.parseColor(theme.textSecondaryColor).copy(alpha = 0.3f),
                        cursorColor = ThemeManager.parseColor(theme.accentColor)
                    )
                )
            }
        }
    }
}