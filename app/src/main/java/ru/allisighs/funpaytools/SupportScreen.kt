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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun SupportScreenView(repository: FunPayRepository, currentTheme: AppTheme, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val support = remember { FunPaySupport(repository) }

    var allTickets by remember { mutableStateOf<List<SupportTicket>>(emptyList()) }
    var filteredTickets by remember { mutableStateOf<List<SupportTicket>>(emptyList()) }
    var selectedTicket by remember { mutableStateOf<TicketDetails?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showCreateDialog by remember { mutableStateOf(false) }
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
            "active" -> result = result.filter { it.status == "Открыт" }
            "solved" -> result = result.filter { it.status == "Закрыт" }
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

    Box(modifier = Modifier.fillMaxSize().background(AppGradient)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                color = ThemeManager.parseColor(currentTheme.surfaceColor),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        if (selectedTicket != null) {
                            selectedTicket = null
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                            tint = ThemeManager.parseColor(currentTheme.textPrimaryColor)
                        )
                    }

                    Text(
                        "Тех. поддержка",
                        modifier = Modifier.weight(1f),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = ThemeManager.parseColor(currentTheme.textPrimaryColor)
                    )

                    if (selectedTicket == null) {
                        IconButton(onClick = {
                            scope.launch {
                                isLoading = true
                                allTickets = support.getTicketsList()
                                applyFilters()
                                isLoading = false
                            }
                        }) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Обновить",
                                tint = ThemeManager.parseColor(currentTheme.accentColor)
                            )
                        }

                        IconButton(onClick = { showCreateDialog = true }) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Создать заявку",
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
                            // Используем название из списка, т.к. оно уже корректно распарсено
                            selectedTicket = details?.copy(title = ticket.title)
                        }
                    }
                )
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
                onError = { error ->
                    errorMessage = error
                }
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

                Text(
                    ticket.status,
                    fontSize = 12.sp,
                    color = when (ticket.status) {
                        "Открыт" -> Color(0xFFFF5252)
                        "Закрыт" -> Color(0xFF00E676)
                        else -> ThemeManager.parseColor(theme.textSecondaryColor)
                    },
                    fontWeight = FontWeight.Medium
                )
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
    var newMessage by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var showInfoPanel by remember { mutableStateOf(false) }
    var isClosing by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Шапка тикета
            Card(
                modifier = Modifier.fillMaxWidth(),
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

                                Surface(
                                    color = when (ticket.status) {
                                        "Открыт" -> Color(0xFFFF5252)
                                        else -> Color(0xFF00E676)
                                    }.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        ticket.status,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        fontSize = 12.sp,
                                        color = when (ticket.status) {
                                            "Открыт" -> Color(0xFFFF5252)
                                            else -> Color(0xFF00E676)
                                        },
                                        fontWeight = FontWeight.Medium
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

                        IconButton(
                            onClick = { showInfoPanel = !showInfoPanel },
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = ThemeManager.parseColor(theme.accentColor)
                            )
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = if (showInfoPanel) "Скрыть инфо" else "Показать инфо"
                            )
                        }
                    }

                    if (ticket.status == "Открыт") {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    isClosing = true
                                    val success = support.closeTicket(ticket.id)
                                    if (success) onUpdate()
                                    isClosing = false
                                }
                            },
                            enabled = !isClosing,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFF5252)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (isClosing) "Закрытие..." else "Закрыть заявку")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Комментарии
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(ticket.comments) { comment ->
                    CommentBubble(comment, theme)
                }
            }

            // Форма ответа
            if (ticket.status == "Открыт") {
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newMessage,
                        onValueChange = { newMessage = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                "Написать ответ...",
                                color = ThemeManager.parseColor(theme.textSecondaryColor)
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor),
                            unfocusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor),
                            focusedBorderColor = ThemeManager.parseColor(theme.accentColor),
                            unfocusedBorderColor = ThemeManager.parseColor(theme.textSecondaryColor).copy(alpha = 0.3f),
                            cursorColor = ThemeManager.parseColor(theme.accentColor)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isSending
                    )

                    Button(
                        onClick = {
                            if (newMessage.isNotBlank()) {
                                scope.launch {
                                    isSending = true
                                    val success = support.addComment(ticket.id, newMessage)
                                    if (success) {
                                        newMessage = ""
                                        onUpdate()
                                    }
                                    isSending = false
                                }
                            }
                        },
                        enabled = newMessage.isNotBlank() && !isSending,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ThemeManager.parseColor(theme.accentColor)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (isSending) "..." else "→", fontSize = 20.sp)
                    }
                }
            }
        }

        // Компактное всплывающее окно с инфо
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
                        .clickable(enabled = false) { }, // Предотвращаем закрытие при клике на карту
                    colors = CardDefaults.cardColors(
                        containerColor = ThemeManager.parseColor(theme.surfaceColor)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Информация",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = ThemeManager.parseColor(theme.textPrimaryColor)
                            )

                            IconButton(
                                onClick = { showInfoPanel = false },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = "Закрыть",
                                    tint = ThemeManager.parseColor(theme.accentColor),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        HorizontalDivider(
                            color = ThemeManager.parseColor(theme.textSecondaryColor).copy(alpha = 0.2f)
                        )

                        // ID заявки
                        CompactInfoRow(
                            label = "ID",
                            value = "#${ticket.id}",
                            theme = theme,
                            highlighted = true
                        )

                        // Комментарии
                        CompactInfoRow(
                            label = "Комментариев",
                            value = ticket.comments.size.toString(),
                            theme = theme
                        )

                        // Дополнительные поля
                        if (ticket.additionalFields.isNotEmpty()) {
                            HorizontalDivider(
                                color = ThemeManager.parseColor(theme.textSecondaryColor).copy(alpha = 0.2f)
                            )

                            ticket.additionalFields.forEach { (key, value) ->
                                CompactInfoRow(
                                    label = key,
                                    value = value,
                                    theme = theme
                                )
                            }
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
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            fontSize = 11.sp,
            color = ThemeManager.parseColor(theme.textSecondaryColor),
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.4f)
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

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (comment.isMyComment) Arrangement.End else Arrangement.Start
    ) {
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
                        val urlPattern = Regex("https?://[^\\s]+")
                        val matches = urlPattern.findAll(comment.text)
                        var lastIndex = 0

                        matches.forEach { match ->
                            append(comment.text.substring(lastIndex, match.range.first))

                            pushStringAnnotation(
                                tag = "URL",
                                annotation = match.value
                            )
                            withStyle(
                                style = SpanStyle(
                                    color = if (comment.isMyComment) Color.White else ThemeManager.parseColor(theme.accentColor),
                                    textDecoration = TextDecoration.Underline
                                )
                            ) {
                                append(match.value)
                            }
                            pop()

                            lastIndex = match.range.last + 1
                        }

                        append(comment.text.substring(lastIndex))
                    }

                    Text(
                        text = annotatedText,
                        fontSize = 14.sp,
                        color = if (comment.isMyComment)
                            Color.White
                        else
                            ThemeManager.parseColor(theme.textPrimaryColor),
                        modifier = Modifier.clickable {
                            annotatedText.getStringAnnotations(tag = "URL", start = 0, end = annotatedText.length)
                                .firstOrNull()?.let { annotation ->
                                    uriHandler.openUri(annotation.item)
                                }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    comment.timestamp,
                    fontSize = 10.sp,
                    color = if (comment.isMyComment)
                        Color.White.copy(0.7f)
                    else
                        ThemeManager.parseColor(theme.textSecondaryColor)
                )
            }
        }
    }
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
            categoryFields = support.getCategoryFields(selectedCategory!!.id)
            fieldValues = categoryFields.associate { it.id to "" }
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
                // Показываем ошибку вверху диалога
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