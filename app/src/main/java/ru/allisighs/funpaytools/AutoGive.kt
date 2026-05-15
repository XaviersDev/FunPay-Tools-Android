/*
 * Copyright (c) 2026 XaviersDev (AlliSighs). All rights reserved.
 */

package ru.allisighs.funpaytools

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID



data class AutoDeliverySettings(
    @SerializedName("enabled") val enabled: Boolean = false,
    @SerializedName("multiDelivery") val multiDelivery: Boolean = true,
    @SerializedName("autoRestore") val autoRestore: Boolean = false,
    @SerializedName("autoDisable") val autoDisable: Boolean = true,
    @SerializedName("lots") val lots: List<AutoDeliveryLot> = emptyList()
)

data class AutoDeliveryLot(
    @SerializedName("id") val id: String = UUID.randomUUID().toString(),
    @SerializedName("lotName") val lotName: String = "", 
    @SerializedName("responseText") val responseText: String = "Спасибо за покупку, \$username!\n\nВаш товар:\n\$product",
    @SerializedName("productsFileName") val productsFileName: String? = null,
    @SerializedName("disabled") val disabled: Boolean = false,
    @SerializedName("disableMultiDelivery") val disableMultiDelivery: Boolean = false
)



object AutoDeliveryManager {
    private const val PREFS_NAME = "auto_delivery_prefs"
    private val gson = Gson()

    fun getSettings(context: Context): AutoDeliverySettings {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString("settings", null)
        return try { gson.fromJson(json, AutoDeliverySettings::class.java) ?: AutoDeliverySettings() }
        catch (e: Exception) { AutoDeliverySettings() }
    }

    fun saveSettings(context: Context, settings: AutoDeliverySettings) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString("settings", gson.toJson(settings)).apply()
    }

    fun getProductsCount(context: Context, fileName: String): Int {
        val file = File(context.filesDir, fileName)
        if (!file.exists()) return 0
        return file.readLines().filter { it.isNotBlank() }.size
    }

    fun readProductsContent(context: Context, fileName: String): String {
        val file = File(context.filesDir, fileName)
        if (!file.exists()) return ""
        return file.readText()
    }

    fun saveProductsContent(context: Context, fileName: String, content: String) {
        val file = File(context.filesDir, fileName)
        val cleanedLines = content.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        file.writeText(cleanedLines.joinToString("\n"))
    }

    private fun takeProducts(context: Context, fileName: String, amount: Int): List<String>? {
        val file = File(context.filesDir, fileName)
        if (!file.exists()) return null

        val lines = file.readLines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.size < amount) return null 

        val taken = lines.take(amount)
        val remaining = lines.drop(amount)
        file.writeText(remaining.joinToString("\n"))
        return taken
    }

    
    suspend fun checkAutoDelivery(chat: ChatItem, repo: FunPayRepository, context: Context): Boolean {
        val settings = getSettings(context)
        if (!settings.enabled) return false

        val msgLower = chat.lastMessage.lowercase()
        
        if (!msgLower.contains("оплатил заказ") && !msgLower.contains("paid for order")) return false

        val orderIdMatch = Regex("#([A-Za-z0-9]+)").find(chat.lastMessage)
        val orderId = orderIdMatch?.groupValues?.get(1) ?: return false

        
        val processedPrefs = context.getSharedPreferences("autodelivery_processed", Context.MODE_PRIVATE)
        if (processedPrefs.getBoolean(orderId, false)) return false

        LogManager.addLog("⚡ Автовыдача: Обнаружена оплата заказа #$orderId от ${chat.username}")

        
        val orderDetails = repo.getOrderDetails(orderId) ?: return false
        val purchasedLotName = orderDetails.shortDesc.trim()
        val amountStr = orderDetails.params.entries.find { it.key.contains("Количество", true) || it.key.contains("Amount", true) }?.value ?: "1"
        val amount = amountStr.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 1

        
        val lotConfig = settings.lots.find {
            !it.disabled && (purchasedLotName.equals(it.lotName, ignoreCase = true) || purchasedLotName.startsWith(it.lotName, ignoreCase = true))
        }

        if (lotConfig == null) {
            LogManager.addLogDebug("Автовыдача: Лот '$purchasedLotName' не найден в настройках.")
            return false
        }

        var finalText = lotConfig.responseText
        var takenProducts: List<String>? = null

        
        if (!lotConfig.productsFileName.isNullOrBlank()) {
            val isMulti = settings.multiDelivery && !lotConfig.disableMultiDelivery
            val neededAmount = if (isMulti) amount else 1

            takenProducts = takeProducts(context, lotConfig.productsFileName, neededAmount)

            if (takenProducts == null) {
                LogManager.addLog("❌ Автовыдача: Недостаточно товара в файле ${lotConfig.productsFileName} для заказа #$orderId!")
                repo.sendMessage(chat.id, "Здравствуйте! К сожалению, товар закончился в базе автовыдачи. Пожалуйста, подождите, я выдам его вручную как только смогу, или сделаю возврат.")

                
                if (settings.autoDisable) {
                    orderDetails.lotId?.let { repo.toggleLotState(it, false) }
                    LogManager.addLog("🛑 Автовыдача: Лот '$purchasedLotName' деактивирован из-за нехватки товара.")
                }

                processedPrefs.edit().putBoolean(orderId, true).apply()
                return false
            }

            val productsString = takenProducts.joinToString("\n")
            finalText = finalText.replace("\$product", productsString)
        }

        
        finalText = finalText
            .replace("\$username", chat.username)
            .replace("\$order_id", orderId)
            .replace("\$lot_name", purchasedLotName)

        
        val success = repo.sendMessage(chat.id, finalText)
        if (success) {
            LogManager.addLog("✅ Автовыдача: Заказ #$orderId успешно выдан!")
            processedPrefs.edit().putBoolean(orderId, true).apply()

            if (repo.getReadMarkSettings().markAfterAutoReply) repo.markChatAsRead(chat.id)

            
            if (settings.autoDisable && !lotConfig.productsFileName.isNullOrBlank()) {
                val remaining = getProductsCount(context, lotConfig.productsFileName)
                if (remaining == 0) {
                    orderDetails.lotId?.let { repo.toggleLotState(it, false) }
                    LogManager.addLog("🛑 Автовыдача: Лот '$purchasedLotName' деактивирован (товар полностью закончился).")
                }
            }
            return true
        } else {
            LogManager.addLog("❌ Автовыдача: Ошибка отправки сообщения в чат для заказа #$orderId")
            
            takenProducts?.let {
                val current = readProductsContent(context, lotConfig.productsFileName!!)
                saveProductsContent(context, lotConfig.productsFileName, it.joinToString("\n") + "\n" + current)
            }
            return false
        }
    }
}



@Composable
fun AutoDeliveryCard(
    settings: AutoDeliverySettings,
    theme: AppTheme,
    onToggle: () -> Unit,
    onConfigure: () -> Unit
) {
    val accent = ThemeManager.parseColor(theme.accentColor)
    val cardColor by animateColorAsState(
        if (settings.enabled) accent.copy(alpha = 0.15f) else ThemeManager.parseColor(theme.surfaceColor).copy(alpha = theme.containerOpacity),
        label = "bc"
    )
    val borderColor by animateColorAsState(if (settings.enabled) accent else Color.Transparent, label = "bb")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(if (settings.enabled) 1.5.dp else 0.dp, borderColor, RoundedCornerShape(theme.borderRadius.dp)),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(theme.borderRadius.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FlashOn, null, tint = if (settings.enabled) accent else ThemeManager.parseColor(theme.textSecondaryColor), modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Автовыдача 24/7", fontWeight = FontWeight.Bold, color = ThemeManager.parseColor(theme.textPrimaryColor), fontSize = 15.sp)
                    Text(
                        if (settings.enabled) "Активна · Лотов: ${settings.lots.size}" else "Выключена",
                        color = if (settings.enabled) accent else ThemeManager.parseColor(theme.textSecondaryColor),
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = settings.enabled,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.4f))
                )
            }

            AnimatedVisibility(visible = settings.enabled) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onConfigure,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = ThemeManager.parseColor(theme.surfaceColor))
                    ) {
                        Icon(Icons.Default.Tune, null, tint = ThemeManager.parseColor(theme.textPrimaryColor), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Настроить товары и лоты", color = ThemeManager.parseColor(theme.textPrimaryColor))
                    }
                }
            }
        }
    }
}

@Composable
fun AutoDeliverySettingsDialog(
    theme: AppTheme,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(AutoDeliveryManager.getSettings(context)) }

    var editingLot by remember { mutableStateOf<AutoDeliveryLot?>(null) }

    fun save(newSettings: AutoDeliverySettings) {
        settings = newSettings
        AutoDeliveryManager.saveSettings(context, newSettings)
    }

    if (editingLot != null) {
        AutoDeliveryLotEditor(
            lot = editingLot!!,
            theme = theme,
            onSave = { updated ->
                val newLots = if (settings.lots.any { it.id == updated.id }) {
                    settings.lots.map { if (it.id == updated.id) updated else it }
                } else {
                    settings.lots + updated
                }
                save(settings.copy(lots = newLots))
                editingLot = null
            },
            onDelete = {
                save(settings.copy(lots = settings.lots.filter { it.id != editingLot!!.id }))
                editingLot = null
            },
            onDismiss = { editingLot = null }
        )
        return
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.9f),
            colors = CardDefaults.cardColors(containerColor = ThemeManager.parseColor(theme.surfaceColor)),
            shape = RoundedCornerShape(theme.borderRadius.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FlashOn, null, tint = ThemeManager.parseColor(theme.accentColor), modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Настройки Автовыдачи", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ThemeManager.parseColor(theme.textPrimaryColor), modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null, tint = ThemeManager.parseColor(theme.textPrimaryColor)) }
                }

                Spacer(Modifier.height(8.dp))

                LazyColumn(modifier = Modifier.weight(1f)) {
                    item {
                        Text("ГЛОБАЛЬНЫЕ НАСТРОЙКИ", color = ThemeManager.parseColor(theme.accentColor), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))

                        GlobalSwitchRow("Мульти-выдача", "Выдавать несколько строк из файла, если покупатель купил > 1 шт.", settings.multiDelivery, theme) {
                            save(settings.copy(multiDelivery = it))
                        }
                        GlobalSwitchRow("Авто-отключение лота", "Скрывать лот на FunPay, если файл с товаром опустел", settings.autoDisable, theme) {
                            save(settings.copy(autoDisable = it))
                        }

                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(color = Color.White.copy(0.1f))
                        Spacer(Modifier.height(16.dp))

                        Text("ПРИВЯЗАННЫЕ ЛОТЫ", color = ThemeManager.parseColor(theme.accentColor), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = { editingLot = AutoDeliveryLot() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ThemeManager.parseColor(theme.accentColor)),
                            border = BorderStroke(1.dp, ThemeManager.parseColor(theme.accentColor))
                        ) {
                            Icon(Icons.Default.Add, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Добавить лот для автовыдачи")
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    items(settings.lots) { lot ->
                        val count = if (lot.productsFileName != null) AutoDeliveryManager.getProductsCount(context, lot.productsFileName) else 0

                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable { editingLot = lot }.alpha(if(lot.disabled) 0.5f else 1f),
                            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.2f)),
                            border = BorderStroke(1.dp, Color.White.copy(0.05f))
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(lot.lotName.ifBlank { "Без названия" }, color = ThemeManager.parseColor(theme.textPrimaryColor), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.height(4.dp))
                                    if (lot.productsFileName != null) {
                                        Text("Файл: ${lot.productsFileName} ($count шт.)", color = ThemeManager.parseColor(theme.accentColor), fontSize = 11.sp)
                                    } else {
                                        Text("Только текст (без файла)", color = ThemeManager.parseColor(theme.textSecondaryColor), fontSize = 11.sp)
                                    }
                                }
                                Icon(Icons.Default.Edit, null, tint = ThemeManager.parseColor(theme.textSecondaryColor), modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GlobalSwitchRow(title: String, subtitle: String, checked: Boolean, theme: AppTheme, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = ThemeManager.parseColor(theme.textPrimaryColor), fontSize = 14.sp)
            Text(subtitle, color = ThemeManager.parseColor(theme.textSecondaryColor), fontSize = 10.sp, lineHeight = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = ThemeManager.parseColor(theme.accentColor), checkedTrackColor = ThemeManager.parseColor(theme.accentColor).copy(0.5f))
        )
    }
}

@Composable
fun AutoDeliveryLotEditor(
    lot: AutoDeliveryLot,
    theme: AppTheme,
    onSave: (AutoDeliveryLot) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var lotName by remember { mutableStateOf(lot.lotName) }
    var responseText by remember { mutableStateOf(lot.responseText) }
    var disabled by remember { mutableStateOf(lot.disabled) }
    var disableMulti by remember { mutableStateOf(lot.disableMultiDelivery) }

    var productsFileName by remember { mutableStateOf(lot.productsFileName) }
    var showProductsEditor by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val accent = ThemeManager.parseColor(theme.accentColor)

    if (showProductsEditor) {
        ProductsEditorDialog(
            fileName = productsFileName ?: "goods_${System.currentTimeMillis()}.txt",
            theme = theme,
            onSave = { newFileName ->
                productsFileName = newFileName
                showProductsEditor = false
            },
            onDismiss = { showProductsEditor = false }
        )
        return
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.95f),
            colors = CardDefaults.cardColors(containerColor = ThemeManager.parseColor(theme.surfaceColor)),
            shape = RoundedCornerShape(theme.borderRadius.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text("Редактирование лота", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ThemeManager.parseColor(theme.textPrimaryColor))
                Spacer(Modifier.height(16.dp))

                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        OutlinedTextField(
                            value = lotName, onValueChange = { lotName = it },
                            label = { Text("Точное название лота (Триггер)") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent, focusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor), unfocusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor))
                        )
                        Text("Бот ищет это название в заказе. Можно ввести только начало названия.", color = ThemeManager.parseColor(theme.textSecondaryColor), fontSize = 10.sp)
                    }

                    item {
                        OutlinedTextField(
                            value = responseText, onValueChange = { responseText = it },
                            label = { Text("Текст выдачи") },
                            modifier = Modifier.fillMaxWidth().height(150.dp),
                            maxLines = 10,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent, focusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor), unfocusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor))
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("Доступные переменные:\n\$username, \$order_id, \$lot_name, \$product", color = ThemeManager.parseColor(theme.textSecondaryColor), fontSize = 11.sp, lineHeight = 14.sp)
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(0.2f)),
                            border = BorderStroke(1.dp, accent.copy(0.3f))
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text("База товаров (Файл)", color = accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(Modifier.height(4.dp))
                                if (productsFileName != null) {
                                    val cnt = AutoDeliveryManager.getProductsCount(context, productsFileName!!)
                                    Text("Файл: $productsFileName", color = ThemeManager.parseColor(theme.textPrimaryColor))
                                    Text("Остаток: $cnt шт.", color = if(cnt > 0) Color.Green else Color.Red)
                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = { showProductsEditor = true }, colors = ButtonDefaults.buttonColors(containerColor = accent), modifier = Modifier.weight(1f)) {
                                            Text("Редактировать базу")
                                        }
                                        OutlinedButton(onClick = { productsFileName = null }, border = BorderStroke(1.dp, Color.Red), modifier = Modifier.weight(1f)) {
                                            Text("Отвязать", color = Color.Red)
                                        }
                                    }
                                } else {
                                    Text("Файл не привязан. Бот будет отправлять только статический текст.", color = ThemeManager.parseColor(theme.textSecondaryColor), fontSize = 12.sp)
                                    Spacer(Modifier.height(8.dp))
                                    Button(onClick = { showProductsEditor = true }, colors = ButtonDefaults.buttonColors(containerColor = accent), modifier = Modifier.fillMaxWidth()) {
                                        Text("Создать/Привязать базу товаров")
                                    }
                                }
                            }
                        }
                    }

                    item {
                        GlobalSwitchRow("Приостановить этот лот", "Автовыдача для него работать не будет", disabled, theme) { disabled = it }
                        if (productsFileName != null) {
                            GlobalSwitchRow("Запретить мульти-выдачу", "Выдавать строго 1 строку, даже если купили 5 шт.", disableMulti, theme) { disableMulti = it }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    OutlinedButton(onClick = onDelete, border = BorderStroke(1.dp, Color.Red), modifier = Modifier.weight(1f)) {
                        Text("Удалить", color = Color.Red)
                    }
                    Button(
                        onClick = {
                            onSave(lot.copy(
                                lotName = lotName.trim(),
                                responseText = responseText,
                                disabled = disabled,
                                disableMultiDelivery = disableMulti,
                                productsFileName = productsFileName
                            ))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Сохранить")
                    }
                }
            }
        }
    }
}

@Composable
fun ProductsEditorDialog(
    fileName: String,
    theme: AppTheme,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var content by remember { mutableStateOf(AutoDeliveryManager.readProductsContent(context, fileName)) }
    val accent = ThemeManager.parseColor(theme.accentColor)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.95f),
            colors = CardDefaults.cardColors(containerColor = ThemeManager.parseColor(theme.surfaceColor)),
            shape = RoundedCornerShape(theme.borderRadius.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text("Редактор товаров", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ThemeManager.parseColor(theme.textPrimaryColor))
                Text("Каждая строка = 1 товар", fontSize = 12.sp, color = ThemeManager.parseColor(theme.textSecondaryColor))
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    placeholder = { Text("account1:pass1\naccount2:pass2\n...", color = ThemeManager.parseColor(theme.textSecondaryColor)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accent,
                        unfocusedBorderColor = accent.copy(0.3f),
                        focusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor),
                        unfocusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor)
                    )
                )

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Отмена", color = ThemeManager.parseColor(theme.textPrimaryColor))
                    }
                    Button(
                        onClick = {
                            AutoDeliveryManager.saveProductsContent(context, fileName, content)
                            onSave(fileName)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Сохранить")
                    }
                }
            }
        }
    }
}