/*
 * Copyright (c) 2026 XaviersDev (AlliSighs). All rights reserved.
 * FunPay Tools Plugin System Core (ULTIMATE DETAILS EDITION)
 */

package ru.allisighs.funpaytools

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.content.ClipData
import android.content.ClipboardManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Vibrator
import android.os.VibrationEffect
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.File
import java.util.UUID

data class PluginMeta(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val author: String,
    val version: String,
    val description: String,
    val bannerUrl: String? = null,
    val sourceUrl: String? = null,
    val hasUpdate: Boolean = false,
    val newSourceCode: String? = null,
    val isEnabled: Boolean = false,
    val sourceCode: String = "",
    val securityWarnings: List<String> = emptyList()
)

object PluginScanner {
    fun parseMeta(code: String, sourceUrl: String? = null): PluginMeta {
        val name = Regex("""//\s*@name\s+(.+)""").find(code)?.groupValues?.get(1)?.trim() ?: "Unnamed Plugin"
        val author = Regex("""//\s*@author\s+(.+)""").find(code)?.groupValues?.get(1)?.trim() ?: "Unknown"
        val version = Regex("""//\s*@version\s+(.+)""").find(code)?.groupValues?.get(1)?.trim() ?: "1.0"
        val desc = Regex("""//\s*@description\s+(.+)""").find(code)?.groupValues?.get(1)?.trim() ?: ""
        val banner = Regex("""//\s*@banner\s+(.+)""").find(code)?.groupValues?.get(1)?.trim()

        return PluginMeta(
            name = name, author = author, version = version, description = desc,
            bannerUrl = banner, sourceUrl = sourceUrl, sourceCode = code
        )
    }

    fun scan(code: String): List<String> {
        val warnings = mutableListOf<String>()
        val codeLower = code.lowercase()
        if (codeLower.contains("fpt.accounts")) warnings.add("КРИТИЧЕСКИ: Плагин имеет доступ к управлению вашими аккаунтами.")
        if (codeLower.contains("fpt.network")) warnings.add("ВНИМАНИЕ: Делает скрытые сетевые запросы в обход браузера.")
        if (codeLower.contains("fpt.orders.refund")) warnings.add("ОПАСНО: Доступ к автоматическому возврату средств покупателям.")
        if (codeLower.contains("fpt.lots.change") || codeLower.contains("fpt.lots.delete")) warnings.add("ИНФО: Плагин может удалять лоты или менять цены.")
        if (codeLower.contains("fpt.autodelivery")) warnings.add("ИНФО: Плагин имеет доступ к базе файлов автовыдачи.")
        if (codeLower.contains("eval(")) warnings.add("ПОДОЗРИТЕЛЬНО: Использует eval() для скрытого выполнения кода.")
        return warnings
    }
}

@SuppressLint("StaticFieldLeak")
object PluginEngine {
    private var webView: WebView? = null
    private val gson = Gson()
    private val _plugins = MutableStateFlow<List<PluginMeta>>(emptyList())
    val plugins = _plugins.asStateFlow()
    val uiSlots = mutableStateMapOf<String, String>()

    private var isInitialized = false
    private var isRebooting = false

    fun init(context: Context, repository: FunPayRepository) {
        if (isInitialized) return
        isInitialized = true

        val prefs = context.getSharedPreferences("plugins_db", Context.MODE_PRIVATE)
        val saved = prefs.getString("list", "[]")
        val type = object : TypeToken<List<PluginMeta>>() {}.type
        val loaded: List<PluginMeta> = try { gson.fromJson(saved, type) } catch (e: Exception) { emptyList() }
        _plugins.value = loaded

        rebootEngine(context, repository)

        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                delay(10000)
                var hasNewUpdates = false
                val currentList = _plugins.value.toMutableList()

                for (i in currentList.indices) {
                    val p = currentList[i]
                    if (p.sourceUrl != null && !p.hasUpdate) {
                        try {
                            val client = OkHttpClient()
                            val req = Request.Builder().url(p.sourceUrl).build()
                            val resp = client.newCall(req).execute()
                            if (resp.isSuccessful) {
                                val newCode = resp.body?.string() ?: ""
                                if (newCode.trim() != p.sourceCode.trim()) {
                                    currentList[i] = p.copy(hasUpdate = true, newSourceCode = newCode)
                                    hasNewUpdates = true

                                    val nm = context.getSystemService(NotificationManager::class.java)
                                    val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        Notification.Builder(context, "fp_service")
                                    } else {
                                        @Suppress("DEPRECATION") Notification.Builder(context)
                                    }
                                    nm.notify(p.id.hashCode(), b.setSmallIcon(android.R.drawable.stat_notify_sync)
                                        .setContentTitle("Обновление плагина")
                                        .setContentText("Плагин ${p.name} получил обновление от автора.")
                                        .setAutoCancel(true).build())
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
                if (hasNewUpdates) {
                    _plugins.value = currentList
                    prefs.edit().putString("list", gson.toJson(currentList)).apply()
                }

                val updateIntervalHours = prefs.getFloat("auto_update_interval", 4f)
                delay((updateIntervalHours * 3600 * 1000).toLong().coerceAtLeast(60_000L))
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun rebootEngine(context: Context, repository: FunPayRepository) {
        if (isRebooting) return
        isRebooting = true

        CoroutineScope(Dispatchers.Main).launch {
            webView?.evaluateJavascript("window.fpt = null; Object.keys(window).forEach(function(key) { if(typeof window[key] === 'function') { try { window[key] = null; } catch(e){} } });", null)
            webView?.loadUrl("about:blank")
            webView?.clearHistory()
            webView?.removeAllViews()
            webView?.destroy()
            webView = null
            uiSlots.clear()

            val newWebView = WebView(context.applicationContext).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                addJavascriptInterface(FPTJsBridge(context, repository), "fptNative")
            }

            var pageFinishedFired = false

            newWebView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    if (url == "about:blank") return
                    if (pageFinishedFired) return
                    pageFinishedFired = true

                    view?.evaluateJavascript("""
                        window.fpt = {
                            _listeners: {},
                            on: function(event, callback) {
                                if(!this._listeners[event]) this._listeners[event] = [];
                                this._listeners[event].push(callback);
                            },
                            emit: function(event, data) {
                                if(this._listeners[event]) {
                                    var parsedData = (typeof data === 'string') ? JSON.parse(data || '{}') : data;
                                    this._listeners[event].forEach(function(cb) { cb(parsedData); });
                                }
                            },
                            create: function(userId, text) { return fptNative.createChat(userId, text); },
                            chat: {
                                getList: function() { return JSON.parse(fptNative.getChats() || '[]'); },
                                getHistory: function(id) { return JSON.parse(fptNative.getChatHistory(id) || '[]'); },
                                getInfo: function(id) { return JSON.parse(fptNative.getChatInfo(id) || 'null'); },
                                resolveUserId: function(nodeId) { return fptNative.resolveUserId(nodeId); },
                                send: function(id, text) { return fptNative.sendMessage(id, text); },
                                sendWithImage: function(id, text, imgUri, imgFirst) { return fptNative.sendWithImage(id, text, imgUri || "", imgFirst || false); },
                                markRead: function(id) { return fptNative.markChatAsRead(id); }
                            },
                            orders: {
                                getDetails: function(id) { return JSON.parse(fptNative.getOrderDetails(id) || 'null'); },
                                confirm: function(id) { return fptNative.confirmOrder(id); },
                                refund: function(id) { return fptNative.refundOrder(id); },
                                review: {
                                    reply: function(id, text, stars) { return fptNative.replyReview(id, text, stars); },
                                    write: function(id, text, stars) { return fptNative.writeReview(id, text, stars); }
                                }
                            },
                            lots: {
                                getMy: function() { return JSON.parse(fptNative.getMyLots() || '[]'); },
                                getFields: function(id) { return JSON.parse(fptNative.getLotFields(id) || 'null'); },
                                raiseAll: function() { return fptNative.raiseAllLots(); },
                                toggle: function(id, active) { return fptNative.toggleLot(id, active); },
                                delete: function(id) { return fptNative.deleteLot(id); },
                                changePrice: function(id, newPrice) { return fptNative.changeLotPrice(id, newPrice); },
                                copy: function(id, targetNodeId) { return JSON.parse(fptNative.copyLot(id, targetNodeId || "") || 'null'); }
                            },
                            users: {
                                getProfile: function() { return JSON.parse(fptNative.getProfile() || 'null'); },
                                getRmtHub: function(username) { return JSON.parse(fptNative.getRmtHub(username) || 'null'); },
                                getSales: function() { return JSON.parse(fptNative.getSales() || '[]'); },
                                getOrdersWith: function(username, isSales) { return JSON.parse(fptNative.getOrdersWith(username, isSales) || '[]'); },
                                setAvatar: function(base64Image) { return fptNative.setAvatar(base64Image); }
                            },
                            autodelivery: {
                                getSettings: function() { return JSON.parse(fptNative.getAutoDeliverySettings() || '{}'); },
                                saveSettings: function(jsonStr) { return fptNative.saveAutoDeliverySettings(jsonStr); },
                                getFileCount: function(fileName) { return fptNative.getAdFileCount(fileName); },
                                readFile: function(fileName) { return fptNative.readAdFile(fileName); },
                                saveFile: function(fileName, content) { return fptNative.saveAdFile(fileName, content); }
                            },
                            dumper: {
                                getSettings: function() { return JSON.parse(fptNative.getDumperSettings() || '{}'); },
                                saveSettings: function(jsonStr) { return fptNative.saveDumperSettings(jsonStr); },
                                runCycle: function() { return fptNative.runDumperCycle(); }
                            },
                            support: {
                                getTickets: function() { return JSON.parse(fptNative.getTickets() || '[]'); },
                                getDetails: function(id) { return JSON.parse(fptNative.getTicketDetails(id) || 'null'); },
                                create: function(catId, fieldsJson, msg) { return fptNative.createTicket(catId, fieldsJson, msg); },
                                reply: function(id, msg) { return fptNative.replyTicket(id, msg); }
                            },
                            settings: {
                                getFolders: function() { return JSON.parse(fptNative.getFolders() || '[]'); },
                                saveFolders: function(jsonStr) { return fptNative.saveFolders(jsonStr); },
                                getLabels: function() { return JSON.parse(fptNative.getLabels() || '[]'); },
                                saveLabels: function(jsonStr) { return fptNative.saveLabels(jsonStr); },
                                getChatLabels: function() { return JSON.parse(fptNative.getChatLabels() || '{}'); },
                                saveChatLabels: function(jsonStr) { return fptNative.saveChatLabels(jsonStr); },
                                getBusyMode: function() { return JSON.parse(fptNative.getBusyMode() || '{}'); },
                                saveBusyMode: function(jsonStr) { return fptNative.saveBusyMode(jsonStr); },
                                getCommands: function() { return JSON.parse(fptNative.getCommands() || '[]'); },
                                saveCommands: function(jsonStr) { return fptNative.saveCommands(jsonStr); },
                                getTemplates: function() { return JSON.parse(fptNative.getTemplates() || '[]'); },
                                saveTemplates: function(jsonStr) { return fptNative.saveTemplates(jsonStr); },
                                getReminders: function() { return JSON.parse(fptNative.getReminders() || '[]'); },
                                saveReminders: function(jsonStr) { return fptNative.saveReminders(jsonStr); }
                            },
                            accounts: {
                                getAll: function() { return JSON.parse(fptNative.getAllAccounts() || '[]'); },
                                getActive: function() { return JSON.parse(fptNative.getActiveAccount() || 'null'); },
                                switch: function(id) { return fptNative.switchAccount(id); }
                            },
                            network: {
                                get: function(url, headersJson) { return JSON.parse(fptNative.httpGet(url, headersJson || '{}') || 'null'); },
                                post: function(url, body, headersJson) { return JSON.parse(fptNative.httpPost(url, body, headersJson || '{}') || 'null'); },
                                fetchImageBase64: function(url) { return fptNative.fetchImageBase64(url); }
                            },
                            app: {
                                toast: function(msg) { return fptNative.showToast(msg); },
                                notify: function(title, msg) { return fptNative.showNotification(title, msg); },
                                vibrate: function(ms) { return fptNative.vibrate(ms); },
                                log: function(msg) { return fptNative.log(msg); },
                                updateWidgets: function() { return fptNative.updateWidgets(); },
                                saveBase64Image: function(b64) { return fptNative.base64ToLocalUri(b64); }
                            },
                            ui: {
                                setSlot: function(slotName, uiJson) { return fptNative.addUiSlot(slotName, JSON.stringify(uiJson)); },
                                removeSlot: function(slotName) { return fptNative.removeUiSlot(slotName); },
                                getState: function(key) { return fptNative.uiGetState(key); },
                                setState: function(key, value) { return fptNative.uiSetState(key, value); }
                            },
                            storage: {
                                get: function(key) { return fptNative.storageGet(key); },
                                set: function(key, val) { return fptNative.storageSet(key, val); }
                            },
                            ai: {
                                ask: function(prompt) { return fptNative.aiAsk(prompt); },
                                rewrite: function(text, context) { return fptNative.aiRewrite(text, context); },
                                translate: function(text) { return fptNative.aiTranslate(text); }
                            }
                        };
                    """.trimIndent(), null)

                    val active = _plugins.value.filter { it.isEnabled }
                    active.forEach { p ->
                        view?.evaluateJavascript("""
                            try {
                                (function() {
                                    var PLUGIN_ID = '${p.id}';
                                    var PLUGIN_SLOT_KEY = 'settings_${p.id}';
                                    ${p.sourceCode}
                                })();
                            } catch(e) {
                                fptNative.log("Plugin Error [${p.name}]: " + e.message);
                            }
                        """.trimIndent(), null)
                    }

                    isRebooting = false
                }
            }

            webView = newWebView
            newWebView.loadDataWithBaseURL(
                "https://funpay.tools/plugins",
                "<html><body>FPT JS Engine Ready</body></html>",
                "text/html", "UTF-8", null
            )
        }
    }

    fun dispatchEvent(eventName: String, jsonPayload: String) {
        CoroutineScope(Dispatchers.Main).launch {
            val safePayload = jsonPayload
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
            webView?.evaluateJavascript("if(window.fpt) window.fpt.emit('$eventName', '$safePayload');", null)
        }
    }

    fun evaluateJs(js: String, callback: android.webkit.ValueCallback<String>? = null) {
        CoroutineScope(Dispatchers.Main).launch {
            webView?.evaluateJavascript(js, callback)
        }
    }

    fun savePlugin(context: Context, repository: FunPayRepository, meta: PluginMeta) {
        val list = _plugins.value.toMutableList()
        val idx = list.indexOfFirst { it.id == meta.id }
        if (idx >= 0) list[idx] = meta else list.add(meta)
        _plugins.value = list
        context.getSharedPreferences("plugins_db", Context.MODE_PRIVATE).edit().putString("list", gson.toJson(list)).apply()
        rebootEngine(context, repository)
    }

    fun deletePlugin(context: Context, repository: FunPayRepository, id: String) {
        val list = _plugins.value.filter { it.id != id }
        _plugins.value = list
        context.getSharedPreferences("plugins_db", Context.MODE_PRIVATE).edit().putString("list", gson.toJson(list)).apply()
        rebootEngine(context, repository)
    }
}

class FPTJsBridge(private val context: Context, private val repository: FunPayRepository) {
    private val gson = Gson()
    private val uiStateMap = java.util.concurrent.ConcurrentHashMap<String, String>()

    @JavascriptInterface fun getChats() = runBlocking(Dispatchers.IO) { gson.toJson(repository.getChats()) }
    @JavascriptInterface fun getChatHistory(id: String) = runBlocking(Dispatchers.IO) { gson.toJson(repository.getChatHistory(id)) }
    @JavascriptInterface fun getChatInfo(id: String) = runBlocking(Dispatchers.IO) { gson.toJson(repository.getChatInfo(id)) }
    @JavascriptInterface fun resolveUserId(nodeId: String) = runBlocking(Dispatchers.IO) { repository.resolveUserIdForPlugin(nodeId) }
    @JavascriptInterface fun sendMessage(id: String, text: String) = runBlocking(Dispatchers.IO) { repository.sendMessage(id, text) }
    @JavascriptInterface fun sendWithImage(id: String, text: String, imgUri: String, imgFirst: Boolean) = runBlocking(Dispatchers.IO) { repository.sendWithOptionalImage(id, text, imgUri.ifBlank { null }, imgFirst) }
    @JavascriptInterface fun markChatAsRead(id: String) = runBlocking(Dispatchers.IO) { repository.markChatAsRead(id) }
    @JavascriptInterface fun createChat(targetUserId: String, text: String): Boolean = runBlocking(Dispatchers.IO) { val myId = repository.getMyUserId(); if (myId.isBlank() || targetUserId.isBlank()) return@runBlocking false; val chatId = "users-$myId-$targetUserId"; repository.sendMessage(chatId, text) }
    @JavascriptInterface fun setAvatar(base64Image: String): Boolean = runBlocking(Dispatchers.IO) { try { val decodedString = android.util.Base64.decode(base64Image, android.util.Base64.DEFAULT); val bitmap = android.graphics.BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size); DynamicAvatarManager.uploadAvatar(repository, bitmap); true } catch (e: Exception) { false } }

    @JavascriptInterface fun getOrderDetails(id: String) = runBlocking(Dispatchers.IO) { gson.toJson(repository.getOrderDetails(id)) }
    @JavascriptInterface fun confirmOrder(id: String) = runBlocking(Dispatchers.IO) { repository.confirmOrder(id) }
    @JavascriptInterface fun refundOrder(id: String) = runBlocking(Dispatchers.IO) { repository.refundOrder(id) }
    @JavascriptInterface fun replyReview(id: String, text: String, stars: Int) = runBlocking(Dispatchers.IO) { repository.replyToReview(id, text, stars) }
    @JavascriptInterface fun writeReview(id: String, text: String, stars: Int) = runBlocking(Dispatchers.IO) { repository.writeReview(id, text, stars) }

    @JavascriptInterface fun getMyLots() = runBlocking(Dispatchers.IO) { gson.toJson(repository.getMyLots()) }
    @JavascriptInterface fun getLotFields(id: String) = runBlocking(Dispatchers.IO) { gson.toJson(repository.getLotFields(id)) }
    @JavascriptInterface fun raiseAllLots() = runBlocking(Dispatchers.IO) { repository.raiseAllLots() }
    @JavascriptInterface fun toggleLot(id: String, active: Boolean) = runBlocking(Dispatchers.IO) { repository.toggleLotStatus(id, active).first }
    @JavascriptInterface fun deleteLot(id: String) = runBlocking(Dispatchers.IO) { repository.deleteLot(id) }
    @JavascriptInterface fun changeLotPrice(id: String, newPrice: Double): Boolean = runBlocking(Dispatchers.IO) {
        try {
            val data = repository.getLotFields(id)
            val fields = data.fields.mapValues { it.value.value }.toMutableMap()
            fields["price"] = newPrice.toString()
            repository.saveLot(id, fields, data.csrfToken, data.activeCookies).first
        } catch (e: Exception) { false }
    }
    @JavascriptInterface fun copyLot(id: String, targetNodeId: String) = runBlocking(Dispatchers.IO) { gson.toJson(repository.copyLot(id, targetNodeId.ifEmpty { null })) }

    @JavascriptInterface fun getProfile() = runBlocking(Dispatchers.IO) { gson.toJson(repository.getSelfProfileUpdated()) }
    @JavascriptInterface fun getRmtHub(username: String) = runBlocking(Dispatchers.IO) { gson.toJson(fetchRMTHubStats(username).first) }
    @JavascriptInterface fun getSales() = runBlocking(Dispatchers.IO) { gson.toJson(repository.fetchSalesPage(null).first) }
    @JavascriptInterface fun getOrdersWith(username: String, isSales: Boolean) = runBlocking(Dispatchers.IO) { gson.toJson(repository.getOrdersWithBuyer(username, isSales)) }

    @JavascriptInterface fun getAutoDeliverySettings() = gson.toJson(AutoDeliveryManager.getSettings(context))
    @JavascriptInterface fun saveAutoDeliverySettings(json: String) { try { AutoDeliveryManager.saveSettings(context, gson.fromJson(json, AutoDeliverySettings::class.java)) } catch (e:Exception){} }
    @JavascriptInterface fun getAdFileCount(fileName: String) = AutoDeliveryManager.getProductsCount(context, fileName)
    @JavascriptInterface fun readAdFile(fileName: String) = AutoDeliveryManager.readProductsContent(context, fileName)
    @JavascriptInterface fun saveAdFile(fileName: String, content: String) = AutoDeliveryManager.saveProductsContent(context, fileName, content)

    @JavascriptInterface fun getDumperSettings() = gson.toJson(repository.getDumperSettings())
    @JavascriptInterface fun saveDumperSettings(json: String) { try { repository.saveDumperSettings(gson.fromJson(json, DumperSettings::class.java)) } catch (e:Exception){} }
    @JavascriptInterface fun runDumperCycle() = runBlocking(Dispatchers.IO) { repository.runDumperCycle() }

    @JavascriptInterface fun getTickets() = runBlocking(Dispatchers.IO) { gson.toJson(FunPaySupport(repository).getTicketsList()) }
    @JavascriptInterface fun getTicketDetails(id: String) = runBlocking(Dispatchers.IO) { gson.toJson(FunPaySupport(repository).getTicketDetails(id)) }
    @JavascriptInterface fun createTicket(catId: String, fieldsJson: String, msg: String) = runBlocking(Dispatchers.IO) {
        try { val mapType = object : TypeToken<Map<String, String>>() {}.type; val fields: Map<String, String> = gson.fromJson(fieldsJson, mapType); val sup = FunPaySupport(repository); sup.init(); sup.createTicket(catId, fields, msg) ?: "" } catch(e:Exception){""}
    }
    @JavascriptInterface fun replyTicket(id: String, msg: String) = runBlocking(Dispatchers.IO) { try { FunPaySupport(repository).addComment(id, msg) } catch(e:Exception){false} }

    @JavascriptInterface fun getFolders() = gson.toJson(ChatFolderManager.getFolders(context))
    @JavascriptInterface fun saveFolders(json: String) { try { ChatFolderManager.saveFolders(context, gson.fromJson(json, object : TypeToken<List<ChatFolder>>(){}.type)) } catch (e:Exception){} }
    @JavascriptInterface fun getLabels() = gson.toJson(ChatFolderManager.getLabels(context))
    @JavascriptInterface fun saveLabels(json: String) { try { ChatFolderManager.saveLabels(context, gson.fromJson(json, object : TypeToken<List<ChatLabel>>(){}.type)) } catch (e:Exception){} }
    @JavascriptInterface fun getChatLabels() = gson.toJson(ChatFolderManager.getChatLabels(context))
    @JavascriptInterface fun saveChatLabels(json: String) { try { ChatFolderManager.saveChatLabels(context, gson.fromJson(json, object : TypeToken<Map<String, List<String>>>(){}.type)) } catch (e:Exception){} }
    @JavascriptInterface fun getBusyMode() = gson.toJson(ChatFolderManager.getBusyMode(context))
    @JavascriptInterface fun saveBusyMode(json: String) { try { ChatFolderManager.saveBusyMode(context, gson.fromJson(json, BusyModeSettings::class.java)) } catch (e:Exception){} }
    @JavascriptInterface fun getCommands() = gson.toJson(repository.getCommands())
    @JavascriptInterface fun saveCommands(json: String) { try { repository.saveCommands(gson.fromJson(json, object : TypeToken<List<AutoResponseCommand>>(){}.type)) } catch (e:Exception){} }
    @JavascriptInterface fun getTemplates() = gson.toJson(repository.getMessageTemplates())
    @JavascriptInterface fun saveTemplates(json: String) { try { repository.saveMessageTemplates(gson.fromJson(json, object : TypeToken<List<MessageTemplate>>(){}.type)) } catch (e:Exception){} }
    @JavascriptInterface fun getReminders() = gson.toJson(repository.getPendingReminders())
    @JavascriptInterface fun saveReminders(json: String) { try { repository.savePendingReminders(gson.fromJson(json, object : TypeToken<List<PendingOrderReminder>>(){}.type)) } catch (e:Exception){} }

    @JavascriptInterface fun getAllAccounts() = gson.toJson(repository.getAllAccounts())
    @JavascriptInterface fun getActiveAccount() = gson.toJson(repository.getActiveAccount())
    @JavascriptInterface fun switchAccount(id: String) { CoroutineScope(Dispatchers.Main).launch { repository.setActiveAccount(id) } }

    @JavascriptInterface fun httpGet(url: String, headersJson: String): String = runBlocking(Dispatchers.IO) {
        try {
            val headersType = object : TypeToken<Map<String, String>>() {}.type
            val headersMap: Map<String, String> = gson.fromJson(headersJson, headersType) ?: emptyMap()
            val reqBuilder = Request.Builder().url(url)
            headersMap.forEach { (k, v) -> reqBuilder.addHeader(k, v) }
            if (url.contains("funpay.com")) reqBuilder.addHeader("Cookie", repository.getCookieString())
            val resp = repository.repoClient.newCall(reqBuilder.build()).execute()
            JSONObject().put("code", resp.code).put("body", resp.body?.string() ?: "").toString()
        } catch (e: Exception) { JSONObject().put("error", e.message).toString() }
    }

    @JavascriptInterface fun httpPost(url: String, bodyStr: String, headersJson: String): String = runBlocking(Dispatchers.IO) {
        try {
            val headersType = object : TypeToken<Map<String, String>>() {}.type
            val headersMap: Map<String, String> = gson.fromJson(headersJson, headersType) ?: emptyMap()
            val cType = headersMap["Content-Type"] ?: "application/x-www-form-urlencoded"
            val reqBody = bodyStr.toRequestBody(cType.toMediaTypeOrNull())
            val reqBuilder = Request.Builder().url(url).post(reqBody)
            headersMap.forEach { (k, v) -> reqBuilder.addHeader(k, v) }
            if (url.contains("funpay.com")) reqBuilder.addHeader("Cookie", repository.getCookieString())
            val resp = repository.repoClient.newCall(reqBuilder.build()).execute()
            JSONObject().put("code", resp.code).put("body", resp.body?.string() ?: "").toString()
        } catch (e: Exception) { JSONObject().put("error", e.message).toString() }
    }

    @JavascriptInterface fun fetchImageBase64(url: String): String = runBlocking(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0")
                .header("Cookie", repository.getCookieString())
                .build()
            val resp = repository.repoClient.newCall(req).execute()
            val bytes = resp.body?.bytes() ?: return@runBlocking ""
            val mime = resp.header("Content-Type") ?: "image/jpeg"
            "data:$mime;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        } catch (e: Exception) { "" }
    }

    @JavascriptInterface fun showToast(msg: String) { CoroutineScope(Dispatchers.Main).launch { Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() } }
    @JavascriptInterface fun showNotification(title: String, msg: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { nm.createNotificationChannel(NotificationChannel("plugin_ch", "Plugins", NotificationManager.IMPORTANCE_DEFAULT)) }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(context, "plugin_ch") else Notification.Builder(context)
        nm.notify(msg.hashCode(), builder.setSmallIcon(android.R.drawable.stat_notify_chat).setContentTitle(title).setContentText(msg).build())
    }
    @JavascriptInterface fun vibrate(ms: Long) {
        val v = context.getSystemService(Vibrator::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)) else v.vibrate(ms)
    }
    @JavascriptInterface fun log(msg: String) = LogManager.addLog("🧩 [ПЛАГИН]: $msg")
    @JavascriptInterface fun updateWidgets() = CoroutineScope(Dispatchers.Main).launch { WidgetManager.updateAllWidgets(context) }

    @JavascriptInterface fun base64ToLocalUri(dataUrl: String): String {
        return try {
            val cleanBase64 = if (dataUrl.contains(",")) dataUrl.substringAfter(",") else dataUrl
            val decodedBytes = android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT)
            val tempFile = File(context.cacheDir, "plugin_canvas_${System.currentTimeMillis()}.png")
            tempFile.writeBytes(decodedBytes)
            Uri.fromFile(tempFile).toString()
        } catch (e: Exception) {
            LogManager.addLog("❌ Плагин: Ошибка сохранения картинки (неверный base64)")
            ""
        }
    }

    @JavascriptInterface fun addUiSlot(slotName: String, uiJson: String) { CoroutineScope(Dispatchers.Main).launch { PluginEngine.uiSlots[slotName] = uiJson } }
    @JavascriptInterface fun removeUiSlot(slotName: String) { CoroutineScope(Dispatchers.Main).launch { PluginEngine.uiSlots.remove(slotName) } }
    @JavascriptInterface fun uiGetState(key: String): String = uiStateMap[key] ?: ""
    @JavascriptInterface fun uiSetState(key: String, value: String) { uiStateMap[key] = value }

    @JavascriptInterface fun storageGet(key: String) = context.getSharedPreferences("plugins_storage", Context.MODE_PRIVATE).getString(key, "") ?: ""
    @JavascriptInterface fun storageSet(key: String, value: String) = context.getSharedPreferences("plugins_storage", Context.MODE_PRIVATE).edit().putString(key, value).apply()
    @JavascriptInterface fun aiAsk(prompt: String): String = runBlocking(Dispatchers.IO) {
        try {
            val payload = JSONObject().put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
            val req = Request.Builder().url("https://fptools.onrender.com/api/ai").header("Authorization", "Bearer fptoolsdim").post(payload.toString().toRequestBody("application/json".toMediaTypeOrNull())).build()
            JSONObject(repository.repoClient.newCall(req).execute().body?.string() ?: "{}").optString("response", "ERROR")
        } catch (e: Exception) { "ERROR: ${e.message}" }
    }
    @JavascriptInterface fun aiRewrite(text: String, context: String): String = runBlocking(Dispatchers.IO) { repository.rewriteMessage(text, context) ?: "ERROR" }
    @JavascriptInterface fun aiTranslate(text: String): String = runBlocking(Dispatchers.IO) { repository.translateLotDescriptionRuToEn(text) ?: "ERROR" }
}

suspend fun FunPayRepository.resolveUserIdForPlugin(nodeId: String): String {
    return withContext(Dispatchers.IO) {
        try {
            val cookie = "golden_key=${getGoldenKey()}; PHPSESSID=${getPhpSessionId()}"
            val req = Request.Builder().url("https://funpay.com/chat/?node=$nodeId")
                .header("Cookie", cookie).header("User-Agent", "Mozilla/5.0").build()
            val html = repoClient.newCall(req).execute().body?.string() ?: return@withContext ""
            val doc = Jsoup.parse(html)
            val chatEl = doc.select("[data-name][data-user]").firstOrNull() ?: doc.select("[data-name]").firstOrNull()
            val dataName = chatEl?.attr("data-name") ?: ""
            val myUserId = chatEl?.attr("data-user")?.trim() ?: ""
            val ids = Regex("""\d+""").findAll(dataName).map { it.value }.toList()
            ids.firstOrNull { it != myUserId && it.isNotEmpty() } ?: ""
        } catch (e: Exception) { "" }
    }
}

@Composable
fun RenderPluginUiSlot(slotName: String, theme: AppTheme) {
    val uiJsonStr = PluginEngine.uiSlots[slotName]
    if (uiJsonStr.isNullOrBlank()) {
        Text(
            text = "UI отсутствует",
            color = ThemeManager.parseColor(theme.textSecondaryColor).copy(alpha = 0.5f),
            fontSize = 11.sp,
            modifier = Modifier.padding(8.dp)
        )
        return
    }

    key(uiJsonStr) {
        val parsedPair = remember {
            try { JSONObject(uiJsonStr) to null } catch (e: Exception) { null to e.message }
        }
        val rootNode = parsedPair.first
        val errorMsg = parsedPair.second

        if (errorMsg != null) {
            Text("UI Error: $errorMsg", color = Color.Red, fontSize = 10.sp)
        } else if (rootNode != null) {
            RenderNode(rootNode, theme)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RenderNode(node: JSONObject, theme: AppTheme) {
    val type = node.optString("type", "Text")
    when (type) {
        "Column" -> {
            Column(modifier = Modifier.padding(8.dp)) {
                val children = node.optJSONArray("children") ?: JSONArray()
                for (i in 0 until children.length()) RenderNode(children.getJSONObject(i), theme)
            }
        }
        "Row" -> {
            Row(modifier = Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val children = node.optJSONArray("children") ?: JSONArray()
                for (i in 0 until children.length()) RenderNode(children.getJSONObject(i), theme)
            }
        }
        "Text" -> {
            val colorStr = node.optString("color", "")
            Text(
                text = node.optString("text", ""),
                color = if (colorStr.isNotEmpty()) ThemeManager.parseColor(colorStr) else ThemeManager.parseColor(theme.textPrimaryColor),
                fontWeight = if (node.optBoolean("bold", false)) FontWeight.Bold else FontWeight.Normal,
                fontSize = node.optDouble("fontSize", 14.0).toFloat().sp
            )
        }
        "Button" -> {
            Button(
                onClick = { triggerJsCallback(node.optString("onClick", "")) },
                colors = ButtonDefaults.buttonColors(containerColor = ThemeManager.parseColor(theme.accentColor))
            ) { Text(node.optString("text", "Button"), color = Color.White) }
        }
        "Switch" -> {
            val stateKey = node.optString("stateKey", "")
            var checked by remember { mutableStateOf(false) }

            if (stateKey.isNotEmpty()) {
                LaunchedEffect(stateKey) {
                    PluginEngine.evaluateJs("fptNative.uiGetState('$stateKey')") { res ->
                        val clean = res?.replace("\"", "")
                        checked = (clean == "true")
                    }
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = { v ->
                    checked = v
                    if (stateKey.isNotEmpty()) triggerJsCallback("fptNative.uiSetState('$stateKey', '$v'); " + node.optString("onChange", ""))
                },
                colors = SwitchDefaults.colors(checkedThumbColor = ThemeManager.parseColor(theme.accentColor))
            )
        }
        "Card" -> {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = ThemeManager.parseColor(theme.surfaceColor)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    val children = node.optJSONArray("children") ?: JSONArray()
                    for (i in 0 until children.length()) RenderNode(children.getJSONObject(i), theme)
                }
            }
        }
        "Input" -> {
            val stateKey = node.optString("stateKey", "")
            var text by remember { mutableStateOf("") }

            if (stateKey.isNotEmpty()) {
                LaunchedEffect(stateKey) {
                    PluginEngine.evaluateJs("fptNative.uiGetState('$stateKey')") { res ->
                        val clean = res?.removePrefix("\"")?.removeSuffix("\"") ?: ""
                        if (clean != "null") text = clean
                    }
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { v ->
                    text = v
                    if (stateKey.isNotEmpty()) {
                        val safeValue = v.replace("\\", "\\\\")
                            .replace("\"", "\\\"")
                            .replace("'", "\\'")
                            .replace("\n", "\\n")
                            .replace("\r", "\\r")
                        triggerJsCallback("fptNative.uiSetState('$stateKey', '$safeValue'); " + node.optString("onChange", ""))
                    }
                },
                label = { Text(node.optString("label", ""), color = ThemeManager.parseColor(theme.textSecondaryColor)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = node.optBoolean("singleLine", true),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor),
                    unfocusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor),
                    focusedBorderColor = ThemeManager.parseColor(theme.accentColor)
                )
            )
        }
        "Checkbox" -> {
            val stateKey = node.optString("stateKey", "")
            var checked by remember { mutableStateOf(false) }

            if (stateKey.isNotEmpty()) {
                LaunchedEffect(stateKey) {
                    PluginEngine.evaluateJs("fptNative.uiGetState('$stateKey')") { res ->
                        val clean = res?.replace("\"", "")
                        checked = (clean == "true")
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { v ->
                        checked = v
                        if (stateKey.isNotEmpty()) triggerJsCallback("fptNative.uiSetState('$stateKey', '$v'); " + node.optString("onChange", ""))
                    },
                    colors = CheckboxDefaults.colors(checkedColor = ThemeManager.parseColor(theme.accentColor))
                )
                Text(node.optString("text", ""), color = ThemeManager.parseColor(theme.textPrimaryColor), fontSize = 14.sp)
            }
        }
        "Spacer" -> Spacer(Modifier.size(node.optInt("size", 8).dp))
        "Divider" -> HorizontalDivider(color = ThemeManager.parseColor(theme.textSecondaryColor).copy(0.2f), modifier = Modifier.padding(vertical = node.optInt("padding", 8).dp))
        "Image" -> {
            AsyncImage(
                model = node.optString("url", ""),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(node.optInt("height", 150).dp).clip(RoundedCornerShape(node.optInt("radius", 8).dp)),
                contentScale = ContentScale.Crop
            )
        }
        "Slider" -> {
            val stateKey = node.optString("stateKey", "")
            var sVal by remember { mutableFloatStateOf(0f) }

            if (stateKey.isNotEmpty()) {
                LaunchedEffect(stateKey) {
                    PluginEngine.evaluateJs("fptNative.uiGetState('$stateKey')") { res ->
                        val clean = res?.replace("\"", "") ?: "0"
                        sVal = clean.toFloatOrNull() ?: 0f
                    }
                }
            }
            Slider(
                value = sVal,
                onValueChange = { v ->
                    sVal = v
                    if (stateKey.isNotEmpty()) triggerJsCallback("fptNative.uiSetState('$stateKey', '$v'); " + node.optString("onChange", ""))
                },
                valueRange = node.optDouble("min", 0.0).toFloat()..node.optDouble("max", 100.0).toFloat()
            )
        }
    }
}

private fun triggerJsCallback(js: String) {
    if (js.isBlank()) return
    PluginEngine.evaluateJs(js)
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PluginsMainScreen(navController: NavController, repository: FunPayRepository, theme: AppTheme, isTab: Boolean = false) {
    val context = LocalContext.current
    val plugins by PluginEngine.plugins.collectAsState()

    var showDevInfoDialog by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var showCreateCodeDialog by remember { mutableStateOf(false) }
    var showInstallDialog by remember { mutableStateOf<Uri?>(null) }
    var showSecretSettings by remember { mutableStateOf(false) }
    var autoUpdateInterval by remember { mutableFloatStateOf(context.getSharedPreferences("plugins_db", Context.MODE_PRIVATE).getFloat("auto_update_interval", 4f)) }

    LaunchedEffect(Unit) { PluginEngine.init(context, repository) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) showInstallDialog = uri
    }

    if (showInstallDialog != null) PluginInstallFlow(uri = showInstallDialog!!, theme = theme, context = context, repository = repository, onDismiss = { showInstallDialog = null })
    if (showDevInfoDialog) PluginDevInfoDialog(theme = theme, onDismiss = { showDevInfoDialog = false })
    if (showUrlDialog) UrlPluginInstallDialog(theme = theme, onDismiss = { showUrlDialog = false }) { uri ->
        showUrlDialog = false
        showInstallDialog = uri
    }
    if (showCreateCodeDialog) PluginCreateCodeDialog(theme = theme, context = context, repository = repository, onDismiss = { showCreateCodeDialog = false })

    if (showSecretSettings) {
        AlertDialog(
            onDismissRequest = { showSecretSettings = false },
            title = { Text("Секретные настройки", color = ThemeManager.parseColor(theme.textPrimaryColor)) },
            text = {
                Column {
                    Text("Интервал проверки обновлений (часы):", color = ThemeManager.parseColor(theme.textSecondaryColor), fontSize = 12.sp)
                    Slider(value = autoUpdateInterval, onValueChange = { autoUpdateInterval = it }, valueRange = 0.01f..24f)
                    Text(String.format(java.util.Locale.US, "%.2f ч.", autoUpdateInterval), color = ThemeManager.parseColor(theme.textPrimaryColor))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    context.getSharedPreferences("plugins_db", Context.MODE_PRIVATE).edit().putFloat("auto_update_interval", autoUpdateInterval).apply()
                    showSecretSettings = false
                }) { Text("Сохранить") }
            },
            containerColor = ThemeManager.parseColor(theme.surfaceColor)
        )
    }

    Scaffold(
        containerColor = if (isTab) Color.Transparent else ThemeManager.parseColor(theme.backgroundColor),
        topBar = {
            if (!isTab) {
                TopAppBar(
                    title = { Text("Плагины", color = ThemeManager.parseColor(theme.textPrimaryColor), fontWeight = FontWeight.Bold) },
                    navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = ThemeManager.parseColor(theme.textPrimaryColor)) } },
                    actions = { IconButton(onClick = { showDevInfoDialog = true }) { Icon(Icons.Default.Code, "Документация API", tint = ThemeManager.parseColor(theme.accentColor)) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = ThemeManager.parseColor(theme.surfaceColor))
                )
            }
        }
    ) { padding ->
        val topPadding = if (isTab) 12.dp else padding.calculateTopPadding()

        Column(modifier = Modifier.fillMaxSize().padding(top = topPadding, start = 16.dp, end = 16.dp, bottom = 16.dp)) {

            if (isTab) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Плагины",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = ThemeManager.parseColor(theme.textPrimaryColor),
                        modifier = Modifier.combinedClickable(
                            onClick = { },
                            onLongClick = { showSecretSettings = true }
                        )
                    )
                    IconButton(onClick = { showDevInfoDialog = true }) {
                        Icon(Icons.Default.Code, "Документация API", tint = ThemeManager.parseColor(theme.accentColor))
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth().clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://funpay.tools/catalog"))) },
                colors = CardDefaults.cardColors(containerColor = ThemeManager.parseColor(theme.accentColor).copy(0.15f)),
                shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, ThemeManager.parseColor(theme.accentColor).copy(0.4f))
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Store, null, tint = ThemeManager.parseColor(theme.accentColor), modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Каталог плагинов", fontWeight = FontWeight.Bold, color = ThemeManager.parseColor(theme.textPrimaryColor))
                        Text("Скачивайте желательные плагины в 1 клик", fontSize = 12.sp, color = ThemeManager.parseColor(theme.textSecondaryColor), lineHeight = 14.sp)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { showUrlDialog = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ThemeManager.parseColor(theme.accentColor)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Link, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Установить по ссылке", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { filePicker.launch("*/*") },
                    modifier = Modifier.weight(1f).height(40.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ThemeManager.parseColor(theme.textSecondaryColor)),
                    border = BorderStroke(1.dp, ThemeManager.parseColor(theme.textSecondaryColor).copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.Folder, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Из файла .js", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = { showCreateCodeDialog = true },
                    modifier = Modifier.weight(1f).height(40.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ThemeManager.parseColor(theme.textSecondaryColor)),
                    border = BorderStroke(1.dp, ThemeManager.parseColor(theme.textSecondaryColor).copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.Code, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Вставить код", fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("Установленные плагины", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ThemeManager.parseColor(theme.textPrimaryColor))
            Spacer(Modifier.height(8.dp))

            if (plugins.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Плагинов пока нет", color = ThemeManager.parseColor(theme.textSecondaryColor)) }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(plugins) { plugin -> PluginCard(plugin, theme, context, repository) }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
fun PluginCreateCodeDialog(theme: AppTheme, context: Context, repository: FunPayRepository, onDismiss: () -> Unit) {
    var code by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.9f),
            colors = CardDefaults.cardColors(containerColor = ThemeManager.parseColor(theme.backgroundColor))
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Text("Создать плагин из кода", color = ThemeManager.parseColor(theme.textPrimaryColor), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    placeholder = { Text("// @name Мой Плагин\n// @version 1.0\n...", color = ThemeManager.parseColor(theme.textSecondaryColor)) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFFE0E0E0), unfocusedTextColor = Color(0xFFE0E0E0),
                        focusedContainerColor = Color(0xFF1E1E1E), unfocusedContainerColor = Color(0xFF1E1E1E),
                        focusedBorderColor = ThemeManager.parseColor(theme.accentColor)
                    )
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onDismiss, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = ThemeManager.parseColor(theme.surfaceColor))) {
                        Text("Отмена", color = ThemeManager.parseColor(theme.textPrimaryColor))
                    }
                    Button(
                        onClick = {
                            if (code.isBlank()) { Toast.makeText(context, "Код пуст!", Toast.LENGTH_SHORT).show(); return@Button }
                            val meta = PluginScanner.parseMeta(code, null)
                            val warnings = PluginScanner.scan(code)
                            PluginEngine.savePlugin(context, repository, meta.copy(sourceCode = code, securityWarnings = warnings))
                            Toast.makeText(context, "Плагин установлен!", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = ThemeManager.parseColor(theme.accentColor))
                    ) { Text("Установить") }
                }
            }
        }
    }
}

@Composable
fun UrlPluginInstallDialog(theme: AppTheme, onDismiss: () -> Unit, onInstall: (Uri) -> Unit) {
    var url by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ThemeManager.parseColor(theme.surfaceColor)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Link, null, tint = ThemeManager.parseColor(theme.accentColor))
                    Spacer(Modifier.width(8.dp))
                    Text("Установка по ссылке", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = ThemeManager.parseColor(theme.textPrimaryColor))
                }
                Spacer(Modifier.height(8.dp))
                Text("Вставьте прямую ссылку на .js файл", fontSize = 12.sp, color = ThemeManager.parseColor(theme.textSecondaryColor), lineHeight = 16.sp)

                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; errorMsg = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("https://...") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ThemeManager.parseColor(theme.accentColor),
                        focusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor),
                        unfocusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor)
                    )
                )

                if (errorMsg != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(errorMsg!!, color = Color.Red, fontSize = 12.sp)
                }

                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Отмена", color = ThemeManager.parseColor(theme.textSecondaryColor)) }
                    Button(
                        onClick = {
                            if (url.isBlank() || !url.startsWith("http")) { errorMsg = "Введите корректную ссылку"; return@Button }
                            isLoading = true
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val client = okhttp3.OkHttpClient()
                                    val request = okhttp3.Request.Builder().url(url).build()
                                    val response = client.newCall(request).execute()
                                    if (!response.isSuccessful) throw Exception("Сервер вернул ошибку ${response.code}")
                                    val code = response.body?.string() ?: throw Exception("Пустой файл")
                                    val tempFile = File(context.cacheDir, "plugin_download_${System.currentTimeMillis()}.js")
                                    tempFile.writeText(code)
                                    withContext(Dispatchers.Main) {
                                        context.getSharedPreferences("plugins_temp", Context.MODE_PRIVATE).edit().putString("last_downloaded_url", url).apply()
                                        onInstall(Uri.fromFile(tempFile))
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) { errorMsg = "Ошибка скачивания: ${e.message}"; isLoading = false }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading && url.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = ThemeManager.parseColor(theme.accentColor))
                    ) {
                        if (isLoading) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White)
                        else Text("Скачать", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun PluginCard(plugin: PluginMeta, theme: AppTheme, context: Context, repository: FunPayRepository) {
    var showEditor by remember { mutableStateOf(false) }
    var showUpdateDiff by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showWarningsDialog by remember { mutableStateOf(false) }

    if (showWarningsDialog) {
        AlertDialog(
            onDismissRequest = { showWarningsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = Color(0xFFFF5252), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Предупреждения безопасности", color = ThemeManager.parseColor(theme.textPrimaryColor), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    plugin.securityWarnings.forEach { warning ->
                        val color = when {
                            warning.startsWith("КРИТИЧЕСКИ") -> Color(0xFFFF5252)
                            warning.startsWith("ОПАСНО") -> Color(0xFFFF7043)
                            warning.startsWith("ВНИМАНИЕ") -> Color(0xFFFFCA28)
                            warning.startsWith("ПОДОЗРИТЕЛЬНО") -> Color(0xFFFFCA28)
                            else -> Color(0xFF80CBC4)
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = warning,
                                color = color,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWarningsDialog = false }) {
                    Text("Закрыть", color = ThemeManager.parseColor(theme.accentColor))
                }
            },
            containerColor = ThemeManager.parseColor(theme.surfaceColor),
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showUpdateDiff && plugin.newSourceCode != null) {
        val newWarnings = remember { PluginScanner.scan(plugin.newSourceCode) }
        Dialog(onDismissRequest = { showUpdateDiff = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Card(
                modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.9f),
                colors = CardDefaults.cardColors(containerColor = ThemeManager.parseColor(theme.backgroundColor))
            ) {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Text("Обновление: ${plugin.name}", color = ThemeManager.parseColor(theme.textPrimaryColor), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(8.dp))
                    if (newWarnings.isNotEmpty()) {
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF3E1212)), shape = RoundedCornerShape(8.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Text("⚠️ В новом коде найдены угрозы безопасности!", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Spacer(Modifier.height(4.dp))
                                newWarnings.forEach { w -> Text("• $w", color = Color(0xFFFFCDD2), fontSize = 11.sp, lineHeight = 14.sp) }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Text("Новый код плагина:", color = ThemeManager.parseColor(theme.accentColor), fontSize = 12.sp)
                    OutlinedTextField(
                        value = plugin.newSourceCode, onValueChange = {}, readOnly = true, modifier = Modifier.weight(1f).fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color(0xFFE0E0E0), unfocusedTextColor = Color(0xFFE0E0E0), focusedContainerColor = Color(0xFF1E1E1E), unfocusedContainerColor = Color(0xFF1E1E1E))
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { showUpdateDiff = false }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = ThemeManager.parseColor(theme.surfaceColor))) {
                            Text("Отклонить", color = ThemeManager.parseColor(theme.textPrimaryColor))
                        }
                        Button(
                            onClick = {
                                val meta = PluginScanner.parseMeta(plugin.newSourceCode, plugin.sourceUrl)
                                val updated = plugin.copy(sourceCode = plugin.newSourceCode, version = meta.version, hasUpdate = false, newSourceCode = null, securityWarnings = newWarnings)
                                val safeUpdated = if (newWarnings.any { it.contains("КРИТИЧЕСКИ") }) updated.copy(isEnabled = false) else updated
                                PluginEngine.savePlugin(context, repository, safeUpdated)
                                showUpdateDiff = false
                                Toast.makeText(context, "Плагин обновлен!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = if (newWarnings.any { it.contains("КРИТИЧЕСКИ") }) Color.Red else Color(0xFF00C853))
                        ) { Text(if (newWarnings.any { it.contains("КРИТИЧЕСКИ") }) "Установить и Выключить" else "Обновить") }
                    }
                }
            }
        }
    }

    if (showEditor) {
        Dialog(onDismissRequest = { showEditor = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            var editedCode by remember { mutableStateOf(plugin.sourceCode) }
            Card(
                modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.9f),
                colors = CardDefaults.cardColors(containerColor = ThemeManager.parseColor(theme.backgroundColor))
            ) {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Text("Редактор: ${plugin.name}", color = ThemeManager.parseColor(theme.textPrimaryColor), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editedCode, onValueChange = { editedCode = it }, modifier = Modifier.weight(1f).fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color(0xFFE0E0E0), unfocusedTextColor = Color(0xFFE0E0E0), focusedContainerColor = Color(0xFF1E1E1E), unfocusedContainerColor = Color(0xFF1E1E1E), focusedBorderColor = ThemeManager.parseColor(theme.accentColor))
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { showEditor = false }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = ThemeManager.parseColor(theme.surfaceColor))) { Text("Отмена", color = ThemeManager.parseColor(theme.textPrimaryColor)) }
                        Button(
                            onClick = {
                                val updatedMeta = plugin.copy(sourceCode = editedCode, securityWarnings = PluginScanner.scan(editedCode), hasUpdate = false, newSourceCode = null)
                                PluginEngine.savePlugin(context, repository, updatedMeta)
                                showEditor = false
                                Toast.makeText(context, "Код сохранён", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = ThemeManager.parseColor(theme.accentColor))
                        ) { Text("Сохранить") }
                    }
                }
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ThemeManager.parseColor(theme.surfaceColor)),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (plugin.hasUpdate) Color(0xFF00E676) else Color.Transparent)
    ) {
        Column {
            if (plugin.bannerUrl != null && plugin.bannerUrl.isNotBlank()) {
                AsyncImage(model = plugin.bannerUrl, contentDescription = null, modifier = Modifier.fillMaxWidth().height(120.dp), contentScale = ContentScale.Crop)
            }
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(plugin.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ThemeManager.parseColor(theme.textPrimaryColor), modifier = Modifier.weight(1f))
                    Switch(
                        checked = plugin.isEnabled,
                        onCheckedChange = {
                            if (it && plugin.securityWarnings.isNotEmpty()) Toast.makeText(context, "ВНИМАНИЕ! Плагин имеет предупреждения!", Toast.LENGTH_LONG).show()
                            PluginEngine.savePlugin(context, repository, plugin.copy(isEnabled = it))
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = ThemeManager.parseColor(theme.accentColor))
                    )
                }
                Text("v${plugin.version} • Автор: ${plugin.author}", fontSize = 11.sp, color = ThemeManager.parseColor(theme.accentColor))
                Spacer(Modifier.height(8.dp))
                Text(plugin.description, fontSize = 13.sp, color = ThemeManager.parseColor(theme.textSecondaryColor), maxLines = 3, overflow = TextOverflow.Ellipsis)

                if (plugin.securityWarnings.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        onClick = { showWarningsDialog = true },
                        color = Color(0xFFFF5252).copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, null, tint = Color(0xFFFF5252), modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "⚠️ ${plugin.securityWarnings.size} предупреждений — нажмите для просмотра",
                                fontSize = 11.sp,
                                color = Color(0xFFFF5252),
                                modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Default.ChevronRight, null, tint = Color(0xFFFF5252), modifier = Modifier.size(14.dp))
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { showMenu = !showMenu },
                    modifier = Modifier.fillMaxWidth().height(32.dp),
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, ThemeManager.parseColor(theme.textSecondaryColor).copy(0.2f))
                ) {
                    Text(if (showMenu) "Скрыть меню" else "Меню", fontSize = 12.sp, color = ThemeManager.parseColor(theme.textPrimaryColor))
                }

                if (showMenu) {
                    if (plugin.isEnabled) {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = ThemeManager.parseColor(theme.textSecondaryColor).copy(0.2f))
                        val slotKey = "settings_${plugin.id}"
                        val hasSlot = PluginEngine.uiSlots.containsKey(slotKey)
                        if (!hasSlot) {
                            LaunchedEffect(slotKey) {
                                delay(800)
                            }
                        }
                        RenderPluginUiSlot(slotKey, theme)
                    } else {
                        Text(
                            text = "Плагин выключен. Включите его, чтобы использовать интерфейс.",
                            color = ThemeManager.parseColor(theme.textSecondaryColor), fontSize = 11.sp,
                            modifier = Modifier.padding(top = 8.dp).fillMaxWidth(), textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Кнопка редактирования (Иконка карандаша)
                    IconButton(onClick = { showEditor = true }) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Редактировать",
                            tint = ThemeManager.parseColor(theme.textSecondaryColor)
                        )
                    }

                    // Отступ, который прижимает следующие элементы к правому краю
                    Spacer(Modifier.weight(1f))

                    // Кнопка обновления (Без изменений)
                    if (plugin.hasUpdate) {
                        Button(
                            onClick = { showUpdateDiff = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text("Обновить", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(8.dp))
                    }

                    // Кнопка удаления (Иконка корзины, доступна ВСЕГДА)
                    IconButton(onClick = { PluginEngine.deletePlugin(context, repository, plugin.id) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Удалить",
                            tint = Color(0xFFEF5350)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PluginInstallFlow(uri: Uri, theme: AppTheme, context: Context, repository: FunPayRepository, onDismiss: () -> Unit) {
    var isLoading by remember { mutableStateOf(true) }
    var meta by remember { mutableStateOf(PluginMeta(name = "Loading...", author = "", version = "", description = "")) }
    var sizeError by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition()
    val gradientOffset by infiniteTransition.animateFloat(initialValue = 0f, targetValue = 1000f, animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart))

    LaunchedEffect(uri) {
        withContext(Dispatchers.IO) {
            try {
                val stream = context.contentResolver.openInputStream(uri) ?: throw Exception("Stream null")
                val code = stream.readBytes().toString(Charsets.UTF_8)
                stream.close()

                if (code.length > 100 * 1024 * 1024) { sizeError = true; isLoading = false; return@withContext }

                val sourceUrl = context.getSharedPreferences("plugins_temp", Context.MODE_PRIVATE).getString("last_downloaded_url", null)
                meta = PluginScanner.parseMeta(code, sourceUrl).copy(sourceCode = code, securityWarnings = PluginScanner.scan(code))
            } catch (e: Exception) { e.printStackTrace() }
            isLoading = false
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier.fillMaxWidth(0.9f).padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = ThemeManager.parseColor(theme.surfaceColor)),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.fillMaxWidth().height(100.dp).background(Brush.linearGradient(listOf(Color(0xFFCCFF00), Color(0xFF00FF66)), start = Offset(gradientOffset, 0f), end = Offset(gradientOffset + 500f, 500f))), contentAlignment = Alignment.Center) {
                    if (meta.bannerUrl != null) AsyncImage(model = meta.bannerUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Icon(Icons.Default.Extension, null, tint = Color.Black, modifier = Modifier.size(48.dp))
                }

                Column(Modifier.padding(20.dp)) {
                    if (isLoading) {
                        CircularProgressIndicator(color = ThemeManager.parseColor(theme.accentColor), modifier = Modifier.align(Alignment.CenterHorizontally))
                    } else if (sizeError) {
                        Text("Файл слишком большой (> 100МБ). Возможен вредоносный код.", color = Color.Red)
                        Button(onClick = onDismiss) { Text("Закрыть") }
                    } else {
                        Text(meta.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ThemeManager.parseColor(theme.textPrimaryColor))
                        Text("v${meta.version} by ${meta.author}", fontSize = 12.sp, color = ThemeManager.parseColor(theme.textSecondaryColor))
                        Spacer(Modifier.height(8.dp))
                        Text(meta.description, fontSize = 14.sp, color = ThemeManager.parseColor(theme.textPrimaryColor))

                        if (meta.securityWarnings.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF3E1212)), shape = RoundedCornerShape(8.dp)) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("⚠️ Отчет безопасности сканера", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Spacer(Modifier.height(4.dp))
                                    meta.securityWarnings.forEach { w -> Text("• $w", color = Color(0xFFFFCDD2), fontSize = 11.sp, lineHeight = 14.sp) }
                                }
                            }
                        }

                        Spacer(Modifier.height(24.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Отмена") }
                            Button(
                                onClick = { PluginEngine.savePlugin(context, repository, meta); onDismiss() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853))
                            ) { Text("Установить") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PluginDevInfoDialog(theme: AppTheme, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(modifier = Modifier.fillMaxWidth(0.95f), colors = CardDefaults.cardColors(containerColor = ThemeManager.parseColor(theme.surfaceColor)), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text(text = "Разработка плагинов", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ThemeManager.parseColor(theme.textPrimaryColor))
                Spacer(Modifier.height(8.dp))
                Text(text = "Вы можете написать плагин самостоятельно или поручить это дело современным нейросетям. Вы также можете сконвертировать плагин из других продуктов для FunPay в наш плагин, основываясь на документации и JavaScript. Спасибо.", fontSize = 13.sp, color = ThemeManager.parseColor(theme.textSecondaryColor), lineHeight = 16.sp)
                Spacer(Modifier.height(16.dp))
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = ThemeManager.parseColor(theme.accentColor).copy(alpha = 0.1f)), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, ThemeManager.parseColor(theme.accentColor).copy(alpha = 0.3f))) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lightbulb, contentDescription = null, tint = ThemeManager.parseColor(theme.accentColor), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Совет для нейросетей", color = ThemeManager.parseColor(theme.textPrimaryColor), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Просто скопируйте ссылку ниже и отправьте её Claude вместе с вашей идеей. Модель сама перейдёт по ссылке, изучит API FunPay Tools и выдаст вам готовый код. Если у вас другая нейросеть, которая не может прочитать документацию по ссылке - просто перейдите по ней сами и скопируйте содержимое в чат.", color = ThemeManager.parseColor(theme.textSecondaryColor), fontSize = 12.sp, lineHeight = 16.sp)
                        Spacer(Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("funpay.tools/DAI.md", color = ThemeManager.parseColor(theme.accentColor), fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("docs_url", "https://funpay.tools/DAI.md"))
                                Toast.makeText(context, "Ссылка скопирована!", Toast.LENGTH_SHORT).show()
                            }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.ContentCopy, contentDescription = "Копировать", tint = ThemeManager.parseColor(theme.accentColor)) }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = ThemeManager.parseColor(theme.accentColor)), shape = RoundedCornerShape(12.dp)) {
                    Text("Понятно", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}