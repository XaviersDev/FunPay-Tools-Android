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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.File
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

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

/**
 * Plugin persistence layer.
 */
internal object PluginStorage {
    private const val PREFS_NAME = "plugins_db"
    private const val LEGACY_KEY = "list"
    private const val META_KEY = "meta_list_v2"
    private const val MIGRATION_FLAG = "migrated_to_v2"
    private const val PLUGINS_DIR = "plugins"

    private val gson = Gson()
    private val mutex = Mutex()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun pluginsDir(context: Context): File {
        val dir = File(context.applicationContext.filesDir, PLUGINS_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun safeId(id: String): String =
        id.replace(Regex("[^A-Za-z0-9_-]"), "_").take(80)

    private fun sourceFile(context: Context, id: String) =
        File(pluginsDir(context), "${safeId(id)}.js")

    private fun pendingFile(context: Context, id: String) =
        File(pluginsDir(context), "${safeId(id)}.pending.js")

    suspend fun loadAll(context: Context): List<PluginMeta> = mutex.withLock {
        val p = prefs(context)
        if (!p.getBoolean(MIGRATION_FLAG, false)) {
            try { migrateLegacy(context, p) } catch (e: Exception) {
                LogManager.addLog("⚠️ Plugins: migration failed: ${e.message}")
            }
        }
        val raw = p.getString(META_KEY, "[]") ?: "[]"
        val type = object : TypeToken<List<PluginMeta>>() {}.type
        val metas: List<PluginMeta> = try { gson.fromJson(raw, type) ?: emptyList() } catch (_: Exception) { emptyList() }
        metas.map { meta ->
            val src = readFile(sourceFile(context, meta.id))
            val pend = readFile(pendingFile(context, meta.id))
            meta.copy(sourceCode = src ?: "", newSourceCode = pend)
        }
    }

    suspend fun upsert(context: Context, meta: PluginMeta): Unit = mutex.withLock {
        val p = prefs(context)
        val list = readMetaListLocked(p).toMutableList()
        val idx = list.indexOfFirst { it.id == meta.id }
        val stripped = meta.copy(sourceCode = "", newSourceCode = null)
        if (idx >= 0) list[idx] = stripped else list.add(stripped)
        writeMetaListLocked(p, list)

        writeFile(sourceFile(context, meta.id), meta.sourceCode)
        val pend = meta.newSourceCode
        if (pend != null) writeFile(pendingFile(context, meta.id), pend)
        else pendingFile(context, meta.id).delete()
    }

    suspend fun remove(context: Context, id: String): Unit = mutex.withLock {
        val p = prefs(context)
        val list = readMetaListLocked(p).filter { it.id != id }
        writeMetaListLocked(p, list)
        sourceFile(context, id).delete()
        pendingFile(context, id).delete()
    }

    suspend fun updateMetaOnly(
        context: Context,
        id: String,
        transform: (PluginMeta) -> PluginMeta
    ): PluginMeta? = mutex.withLock {
        val p = prefs(context)
        val list = readMetaListLocked(p).toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx < 0) return@withLock null
        val updated = transform(list[idx])
        list[idx] = updated.copy(sourceCode = "", newSourceCode = null)
        writeMetaListLocked(p, list)
        updated
    }

    suspend fun writePendingUpdate(context: Context, id: String, newCode: String): Unit = mutex.withLock {
        writeFile(pendingFile(context, id), newCode)
    }

    suspend fun clearPendingUpdate(context: Context, id: String): Unit = mutex.withLock {
        pendingFile(context, id).delete()
    }

    private fun readMetaListLocked(p: android.content.SharedPreferences): List<PluginMeta> {
        val raw = p.getString(META_KEY, "[]") ?: "[]"
        val type = object : TypeToken<List<PluginMeta>>() {}.type
        return try { gson.fromJson(raw, type) ?: emptyList() } catch (_: Exception) { emptyList() }
    }

    private fun writeMetaListLocked(p: android.content.SharedPreferences, list: List<PluginMeta>) {
        p.edit().putString(META_KEY, gson.toJson(list)).apply()
    }

    private fun readFile(file: File): String? = try {
        if (file.exists()) file.readText(Charsets.UTF_8) else null
    } catch (_: Exception) { null }

    private fun writeFile(file: File, content: String) {
        try {
            val parent = file.parentFile
            if (parent != null && !parent.exists()) parent.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(content, Charsets.UTF_8)
            if (file.exists()) file.delete()
            tmp.renameTo(file)
        } catch (e: Exception) {
            LogManager.addLog("⚠️ Plugins: write failed for ${file.name}: ${e.message}")
        }
    }

    private fun migrateLegacy(context: Context, p: android.content.SharedPreferences) {
        val legacy = p.getString(LEGACY_KEY, null) ?: run {
            p.edit().putBoolean(MIGRATION_FLAG, true).apply()
            return
        }
        val type = object : TypeToken<List<PluginMeta>>() {}.type
        val list: List<PluginMeta> = try { gson.fromJson(legacy, type) ?: emptyList() } catch (_: Exception) { emptyList() }
        val stripped = list.map {
            writeFile(sourceFile(context, it.id), it.sourceCode)
            it.newSourceCode?.let { code -> writeFile(pendingFile(context, it.id), code) }
            it.copy(sourceCode = "", newSourceCode = null)
        }
        p.edit()
            .putString(META_KEY, gson.toJson(stripped))
            .putBoolean(MIGRATION_FLAG, true)
            .remove(LEGACY_KEY)
            .apply()
        LogManager.addLog("✅ Plugins: миграция в файловое хранилище завершена (${stripped.size} плагин(ов))")
    }
}

@SuppressLint("StaticFieldLeak")
object PluginEngine {
    private val webViewRef = AtomicReference<WebView?>(null)
    private val webViewReady = AtomicBoolean(false)
    private val initialized = AtomicBoolean(false)

    private val gson = Gson()

    private val _plugins = MutableStateFlow<List<PluginMeta>>(emptyList())
    val plugins = _plugins.asStateFlow()
    val uiSlots = mutableStateMapOf<String, String>()

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e ->
        LogManager.addLog("⚠️ PluginEngine: uncaught ${e::class.java.simpleName}: ${e.message}")
    })
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val reloadMutex = Mutex()
    private val reloadRequest = AtomicBoolean(false)
    private var autoUpdateJob: Job? = null
    private val initialLoadDone = kotlinx.coroutines.CompletableDeferred<Unit>()

    private var appContextRef: Context? = null
    private var repositoryRef: FunPayRepository? = null

    private val updateClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .build()
    }

    fun init(context: Context, repository: FunPayRepository) {
        if (!initialized.compareAndSet(false, true)) return
        appContextRef = context.applicationContext
        repositoryRef = repository

        engineScope.launch {
            try {
                val loaded = PluginStorage.loadAll(context)
                _plugins.value = loaded
            } catch (e: Exception) {
                LogManager.addLog("⚠️ PluginEngine: load failed: ${e.message}")
                _plugins.value = emptyList()
            } finally {
                initialLoadDone.complete(Unit)
            }
            requestReload()
        }

        autoUpdateJob?.cancel()
        autoUpdateJob = engineScope.launch { autoUpdateLoop(context) }
    }

    fun shutdown() {
        autoUpdateJob?.cancel()
        autoUpdateJob = null
        mainHandler.post { destroyWebViewLocked() }
    }

    private suspend fun autoUpdateLoop(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences("plugins_db", Context.MODE_PRIVATE)
        delay(10_000L)
        while (currentCoroutineContext().isActive) {
            try {
                val snapshot = _plugins.value
                for (p in snapshot) {
                    if (p.sourceUrl.isNullOrBlank() || p.hasUpdate) continue
                    // FIX #1: заменено coroutineContext на currentCoroutineContext()
                    if (!currentCoroutineContext().isActive) break
                    try {
                        val req = Request.Builder().url(p.sourceUrl).build()
                        updateClient.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) return@use
                            val body = resp.body ?: return@use
                            val limited = body.byteStream().readNBytesSafe(MAX_PLUGIN_SIZE + 1)
                            if (limited.size > MAX_PLUGIN_SIZE) {
                                LogManager.addLog("⚠️ Plugin update '${p.name}' превышает лимит ${MAX_PLUGIN_SIZE} байт, пропущен")
                                return@use
                            }
                            val newCode = String(limited, Charsets.UTF_8)
                            if (newCode.trim() != p.sourceCode.trim()) {
                                PluginStorage.writePendingUpdate(context, p.id, newCode)
                                val updated = PluginStorage.updateMetaOnly(context, p.id) {
                                    it.copy(hasUpdate = true)
                                }
                                if (updated != null) {
                                    updateInMemory(p.id) { it.copy(hasUpdate = true, newSourceCode = newCode) }
                                    notifyUpdate(context, p)
                                }
                            }
                        }
                    } catch (_: CancellationException) { throw CancellationException() } catch (_: Exception) {}
                }
            } catch (_: CancellationException) { throw CancellationException() } catch (_: Exception) {}

            val updateIntervalHours = prefs.getFloat("auto_update_interval", 4f)
            val sleepMs = (updateIntervalHours * 3600f * 1000f).toLong().coerceAtLeast(60_000L)
            delay(sleepMs)
        }
    }

    private fun notifyUpdate(context: Context, p: PluginMeta) {
        try {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(context, "fp_service")
            } else {
                @Suppress("DEPRECATION") Notification.Builder(context)
            }
            nm.notify(
                p.id.hashCode(), b.setSmallIcon(android.R.drawable.stat_notify_sync)
                    .setContentTitle("Обновление плагина")
                    .setContentText("Плагин ${p.name} получил обновление от автора.")
                    .setAutoCancel(true).build()
            )
        } catch (_: Exception) {}
    }

    fun requestReload() {
        if (!reloadRequest.compareAndSet(false, true)) return
        mainHandler.post {
            mainHandler.postDelayed({
                if (reloadRequest.compareAndSet(true, false)) {
                    engineScope.launch { reloadLocked() }
                }
            }, 120L)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun reloadLocked() = reloadMutex.withLock {
        val ctx = appContextRef ?: return@withLock
        val repo = repositoryRef ?: return@withLock
        val activePlugins = _plugins.value.filter { it.isEnabled }

        withContext(Dispatchers.Main) {
            destroyWebViewLocked()
            val newWebView = createWebViewLocked(ctx, repo)
            webViewRef.set(newWebView)
            webViewReady.set(false)
            uiSlots.clear()

            val client = object : WebViewClient() {
                private val fired = AtomicBoolean(false)
                override fun onPageFinished(view: WebView?, url: String?) {
                    if (url == "about:blank") return
                    if (!fired.compareAndSet(false, true)) return
                    if (view !== webViewRef.get()) return
                    bootstrapApi(view)
                    activePlugins.forEach { p -> injectPlugin(view, p) }
                    webViewReady.set(true)
                }
            }
            newWebView.webViewClient = client
            newWebView.loadDataWithBaseURL(
                "https://funpay.tools/plugins",
                "<!DOCTYPE html><html><head><meta charset=\"utf-8\"></head><body>FPT JS Engine</body></html>",
                "text/html", "UTF-8", null
            )
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebViewLocked(ctx: Context, repo: FunPayRepository): WebView {
        return WebView(ctx).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            @Suppress("DEPRECATION")
            settings.allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            settings.allowUniversalAccessFromFileURLs = false
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
            addJavascriptInterface(FPTJsBridge(ctx.applicationContext, repo), "fptNative")
        }
    }

    private fun destroyWebViewLocked() {
        val old = webViewRef.getAndSet(null) ?: return
        webViewReady.set(false)
        try {
            old.removeJavascriptInterface("fptNative")
            old.stopLoading()
            old.webViewClient = WebViewClient()
            old.loadUrl("about:blank")
            old.clearHistory()
            (old.parent as? android.view.ViewGroup)?.removeView(old)
            old.destroy()
        } catch (_: Exception) {}
    }

    private fun bootstrapApi(view: WebView?) {
        view?.evaluateJavascript(API_BOOTSTRAP_JS, null)
    }

    private fun injectPlugin(view: WebView?, p: PluginMeta) {
        val v = view ?: return
        val idLiteral = JSONObject.quote(p.id)
        val slotLiteral = JSONObject.quote("settings_${p.id}")
        val nameLiteral = JSONObject.quote(p.name)
        val srcLiteral = JSONObject.quote(p.sourceCode)
        val js = """
            try {
                (function(){
                    var PLUGIN_ID = $idLiteral;
                    var PLUGIN_SLOT_KEY = $slotLiteral;
                    var PLUGIN_NAME = $nameLiteral;
                    var __code = $srcLiteral;
                    var __fn = new Function('PLUGIN_ID','PLUGIN_SLOT_KEY','PLUGIN_NAME', __code);
                    __fn(PLUGIN_ID, PLUGIN_SLOT_KEY, PLUGIN_NAME);
                })();
            } catch(e) {
                try { fptNative.log("Plugin Error [" + $nameLiteral + "]: " + (e && e.message ? e.message : e)); } catch(_) {}
            }
        """.trimIndent()
        v.evaluateJavascript(js, null)
    }

    fun dispatchEvent(eventName: String, jsonPayload: String) {
        val name = JSONObject.quote(eventName)
        val payload = JSONObject.quote(jsonPayload)
        val js = "if(window.fpt && window.fpt.emit){ try { window.fpt.emit($name, $payload); } catch(e){} }"
        evaluateJs(js)
    }

    fun evaluateJs(js: String, callback: android.webkit.ValueCallback<String>? = null) {
        if (!webViewReady.get()) return
        mainHandler.post {
            val wv = webViewRef.get() ?: return@post
            try { wv.evaluateJavascript(js, callback) } catch (_: Exception) {}
        }
    }

    private fun updateInMemory(id: String, transform: (PluginMeta) -> PluginMeta) {
        val list = _plugins.value.toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx >= 0) {
            list[idx] = transform(list[idx])
            _plugins.value = list
        }
    }

    fun savePlugin(context: Context, repository: FunPayRepository, meta: PluginMeta) {
        appContextRef = appContextRef ?: context.applicationContext
        repositoryRef = repositoryRef ?: repository
        engineScope.launch {
            initialLoadDone.await()
            PluginStorage.upsert(context, meta)
            val list = _plugins.value.toMutableList()
            val idx = list.indexOfFirst { it.id == meta.id }
            if (idx >= 0) list[idx] = meta else list.add(meta)
            _plugins.value = list
            requestReload()
        }
    }

    fun updatePluginMeta(context: Context, repository: FunPayRepository, id: String, transform: (PluginMeta) -> PluginMeta) {
        appContextRef = appContextRef ?: context.applicationContext
        repositoryRef = repositoryRef ?: repository
        engineScope.launch {
            initialLoadDone.await()
            PluginStorage.updateMetaOnly(context, id, transform)
            updateInMemory(id, transform)
            requestReload()
        }
    }

    fun deletePlugin(context: Context, repository: FunPayRepository, id: String) {
        appContextRef = appContextRef ?: context.applicationContext
        repositoryRef = repositoryRef ?: repository
        engineScope.launch {
            initialLoadDone.await()
            PluginStorage.remove(context, id)
            _plugins.value = _plugins.value.filter { it.id != id }
            requestReload()
        }
    }

    private const val MAX_PLUGIN_SIZE = 5L * 1024L * 1024L

    private fun java.io.InputStream.readNBytesSafe(max: Int): ByteArray {
        val buf = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        var total = 0
        while (total < max) {
            val n = this.read(chunk, 0, minOf(chunk.size, max - total))
            if (n <= 0) break
            buf.write(chunk, 0, n)
            total += n
        }
        return buf.toByteArray()
    }

    private fun java.io.InputStream.readNBytesSafe(max: Long): ByteArray {
        return readNBytesSafe(max.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    }

    private val API_BOOTSTRAP_JS = """
        (function(){
            window.fpt = {
                _listeners: {},
                on: function(event, callback) {
                    if(!this._listeners[event]) this._listeners[event] = [];
                    this._listeners[event].push(callback);
                },
                emit: function(event, data) {
                    if(this._listeners[event]) {
                        var parsedData;
                        try { parsedData = (typeof data === 'string') ? JSON.parse(data || '{}') : data; }
                        catch(_) { parsedData = data; }
                        this._listeners[event].forEach(function(cb) { try { cb(parsedData); } catch(e) { try { fptNative.log('Plugin event error: ' + (e && e.message ? e.message : e)); } catch(_){} } });
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
        })();
    """.trimIndent()
}

class FPTJsBridge(private val context: Context, private val repository: FunPayRepository) {
    private val gson = Gson()
    private val uiStateMap = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun ioBlocking(timeoutMs: Long = DEFAULT_TIMEOUT_MS, block: suspend () -> String): String {
        return runBlocking(Dispatchers.IO) {
            try { withTimeoutOrNull(timeoutMs) { block() } ?: "" }
            catch (e: Exception) { LogManager.addLog("⚠️ Plugin bridge: ${e::class.java.simpleName}: ${e.message}"); "" }
        }
    }

    private fun ioBlockingBool(timeoutMs: Long = DEFAULT_TIMEOUT_MS, block: suspend () -> Boolean): Boolean {
        return runBlocking(Dispatchers.IO) {
            try { withTimeoutOrNull(timeoutMs) { block() } ?: false }
            catch (e: Exception) { LogManager.addLog("⚠️ Plugin bridge: ${e::class.java.simpleName}: ${e.message}"); false }
        }
    }

    private fun ioBlockingUnit(timeoutMs: Long = DEFAULT_TIMEOUT_MS, block: suspend () -> Unit) {
        runBlocking(Dispatchers.IO) {
            try { withTimeoutOrNull(timeoutMs) { block() } }
            catch (e: Exception) { LogManager.addLog("⚠️ Plugin bridge: ${e::class.java.simpleName}: ${e.message}") }
        }
    }

    @JavascriptInterface fun getChats() = ioBlocking { gson.toJson(repository.getChats()) }
    @JavascriptInterface fun getChatHistory(id: String) = ioBlocking { gson.toJson(repository.getChatHistory(id)) }
    @JavascriptInterface fun getChatInfo(id: String) = ioBlocking { gson.toJson(repository.getChatInfo(id)) }
    @JavascriptInterface fun resolveUserId(nodeId: String) = ioBlocking { repository.resolveUserIdForPlugin(nodeId) }
    @JavascriptInterface fun sendMessage(id: String, text: String) = ioBlockingBool { repository.sendMessage(id, text) }
    @JavascriptInterface fun sendWithImage(id: String, text: String, imgUri: String, imgFirst: Boolean) = ioBlockingBool(timeoutMs = 60_000L) {
        repository.sendWithOptionalImage(id, text, imgUri.ifBlank { null }, imgFirst)
    }
    @JavascriptInterface fun markChatAsRead(id: String) = ioBlockingUnit { repository.markChatAsRead(id) }
    @JavascriptInterface fun createChat(targetUserId: String, text: String): Boolean = ioBlockingBool {
        val myId = repository.getMyUserId()
        if (myId.isBlank() || targetUserId.isBlank()) return@ioBlockingBool false
        repository.sendMessage("users-$myId-$targetUserId", text)
    }
    @JavascriptInterface fun setAvatar(base64Image: String): Boolean = ioBlockingBool(timeoutMs = 30_000L) {
        try {
            val raw = if (base64Image.contains(",")) base64Image.substringAfter(",") else base64Image
            val decoded = android.util.Base64.decode(raw, android.util.Base64.DEFAULT)
            if (decoded.size > MAX_AVATAR_BYTES) return@ioBlockingBool false
            val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 1 }
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(decoded, 0, decoded.size, opts)
                ?: return@ioBlockingBool false
            DynamicAvatarManager.uploadAvatar(repository, bitmap)
            true
        } catch (_: Exception) { false }
    }

    @JavascriptInterface fun getOrderDetails(id: String) = ioBlocking { gson.toJson(repository.getOrderDetails(id)) }
    @JavascriptInterface fun confirmOrder(id: String) = ioBlockingBool { repository.confirmOrder(id) }
    @JavascriptInterface fun refundOrder(id: String) = ioBlockingBool { repository.refundOrder(id) }
    @JavascriptInterface fun replyReview(id: String, text: String, stars: Int) = ioBlockingBool { repository.replyToReview(id, text, stars) }
    @JavascriptInterface fun writeReview(id: String, text: String, stars: Int) = ioBlockingBool { repository.writeReview(id, text, stars) }

    @JavascriptInterface fun getMyLots() = ioBlocking { gson.toJson(repository.getMyLots()) }
    @JavascriptInterface fun getLotFields(id: String) = ioBlocking { gson.toJson(repository.getLotFields(id)) }
    @JavascriptInterface fun raiseAllLots() = ioBlockingUnit(timeoutMs = 60_000L) { repository.raiseAllLots() }
    @JavascriptInterface fun toggleLot(id: String, active: Boolean) = ioBlockingBool { repository.toggleLotStatus(id, active).first }
    @JavascriptInterface fun deleteLot(id: String) = runBlocking(Dispatchers.IO) { repository.deleteLot(id) }
    @JavascriptInterface fun changeLotPrice(id: String, newPrice: Double): Boolean = ioBlockingBool {
        try {
            val data = repository.getLotFields(id)
            val fields = data.fields.mapValues { it.value.value }.toMutableMap()
            fields["price"] = newPrice.toString()
            repository.saveLot(id, fields, data.csrfToken, data.activeCookies).first
        } catch (_: Exception) { false }
    }
    @JavascriptInterface fun copyLot(id: String, targetNodeId: String) = ioBlocking { gson.toJson(repository.copyLot(id, targetNodeId.ifEmpty { null })) }

    @JavascriptInterface fun getProfile() = ioBlocking { gson.toJson(repository.getSelfProfileUpdated()) }
    @JavascriptInterface fun getRmtHub(username: String) = ioBlocking { gson.toJson(fetchRMTHubStats(username).first) }
    @JavascriptInterface fun getSales() = ioBlocking { gson.toJson(repository.fetchSalesPage(null).first) }
    @JavascriptInterface fun getOrdersWith(username: String, isSales: Boolean) = ioBlocking { gson.toJson(repository.getOrdersWithBuyer(username, isSales)) }

    @JavascriptInterface fun getAutoDeliverySettings(): String = try { gson.toJson(AutoDeliveryManager.getSettings(context)) } catch (_: Exception) { "{}" }
    @JavascriptInterface fun saveAutoDeliverySettings(json: String) { try { AutoDeliveryManager.saveSettings(context, gson.fromJson(json, AutoDeliverySettings::class.java)) } catch (_: Exception) {} }
    @JavascriptInterface fun getAdFileCount(fileName: String): Int = try { AutoDeliveryManager.getProductsCount(context, fileName) } catch (_: Exception) { 0 }
    @JavascriptInterface fun readAdFile(fileName: String): String = try { AutoDeliveryManager.readProductsContent(context, fileName) } catch (_: Exception) { "" }
    @JavascriptInterface fun saveAdFile(fileName: String, content: String) { try { AutoDeliveryManager.saveProductsContent(context, fileName, content) } catch (_: Exception) {} }

    @JavascriptInterface fun getDumperSettings(): String = try { gson.toJson(repository.getDumperSettings()) } catch (_: Exception) { "{}" }
    @JavascriptInterface fun saveDumperSettings(json: String) { try { repository.saveDumperSettings(gson.fromJson(json, DumperSettings::class.java)) } catch (_: Exception) {} }
    @JavascriptInterface fun runDumperCycle() = ioBlockingUnit(timeoutMs = 120_000L) { repository.runDumperCycle() }

    @JavascriptInterface fun getTickets() = ioBlocking { gson.toJson(FunPaySupport(repository).getTicketsList()) }
    @JavascriptInterface fun getTicketDetails(id: String) = ioBlocking { gson.toJson(FunPaySupport(repository).getTicketDetails(id)) }
    @JavascriptInterface fun createTicket(catId: String, fieldsJson: String, msg: String) = ioBlocking {
        try {
            val mapType = object : TypeToken<Map<String, String>>() {}.type
            val fields: Map<String, String> = gson.fromJson(fieldsJson, mapType)
            val sup = FunPaySupport(repository)
            sup.init()
            sup.createTicket(catId, fields, msg) ?: ""
        } catch (_: Exception) { "" }
    }
    @JavascriptInterface fun replyTicket(id: String, msg: String) = ioBlockingBool { try { FunPaySupport(repository).addComment(id, msg) } catch (_: Exception) { false } }

    @JavascriptInterface fun getFolders(): String = try { gson.toJson(ChatFolderManager.getFolders(context)) } catch (_: Exception) { "[]" }
    @JavascriptInterface fun saveFolders(json: String) { try { ChatFolderManager.saveFolders(context, gson.fromJson(json, object : TypeToken<List<ChatFolder>>(){}.type)) } catch (_: Exception) {} }
    @JavascriptInterface fun getLabels(): String = try { gson.toJson(ChatFolderManager.getLabels(context)) } catch (_: Exception) { "[]" }
    @JavascriptInterface fun saveLabels(json: String) { try { ChatFolderManager.saveLabels(context, gson.fromJson(json, object : TypeToken<List<ChatLabel>>(){}.type)) } catch (_: Exception) {} }
    @JavascriptInterface fun getChatLabels(): String = try { gson.toJson(ChatFolderManager.getChatLabels(context)) } catch (_: Exception) { "{}" }
    @JavascriptInterface fun saveChatLabels(json: String) { try { ChatFolderManager.saveChatLabels(context, gson.fromJson(json, object : TypeToken<Map<String, List<String>>>(){}.type)) } catch (_: Exception) {} }
    @JavascriptInterface fun getBusyMode(): String = try { gson.toJson(ChatFolderManager.getBusyMode(context)) } catch (_: Exception) { "{}" }
    @JavascriptInterface fun saveBusyMode(json: String) { try { ChatFolderManager.saveBusyMode(context, gson.fromJson(json, BusyModeSettings::class.java)) } catch (_: Exception) {} }
    @JavascriptInterface fun getCommands(): String = try { gson.toJson(repository.getCommands()) } catch (_: Exception) { "[]" }
    @JavascriptInterface fun saveCommands(json: String) { try { repository.saveCommands(gson.fromJson(json, object : TypeToken<List<AutoResponseCommand>>(){}.type)) } catch (_: Exception) {} }
    @JavascriptInterface fun getTemplates(): String = try { gson.toJson(repository.getMessageTemplates()) } catch (_: Exception) { "[]" }
    @JavascriptInterface fun saveTemplates(json: String) { try { repository.saveMessageTemplates(gson.fromJson(json, object : TypeToken<List<MessageTemplate>>(){}.type)) } catch (_: Exception) {} }
    @JavascriptInterface fun getReminders(): String = try { gson.toJson(repository.getPendingReminders()) } catch (_: Exception) { "[]" }
    @JavascriptInterface fun saveReminders(json: String) { try { repository.savePendingReminders(gson.fromJson(json, object : TypeToken<List<PendingOrderReminder>>(){}.type)) } catch (_: Exception) {} }

    @JavascriptInterface fun getAllAccounts(): String = try { gson.toJson(repository.getAllAccounts()) } catch (_: Exception) { "[]" }
    @JavascriptInterface fun getActiveAccount(): String = try { gson.toJson(repository.getActiveAccount()) } catch (_: Exception) { "null" }
    @JavascriptInterface fun switchAccount(id: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try { repository.setActiveAccount(id) } catch (_: Exception) {}
        }
    }

    @JavascriptInterface fun httpGet(url: String, headersJson: String): String = runBlocking(Dispatchers.IO) {
        try {
            withTimeoutOrNull(HTTP_TIMEOUT_MS) {
                if (!isHttpUrlAllowed(url)) return@withTimeoutOrNull JSONObject().put("error", "URL не разрешён").toString()
                val headersType = object : TypeToken<Map<String, String>>() {}.type
                val headersMap: Map<String, String> = try { gson.fromJson(headersJson, headersType) ?: emptyMap() } catch (_: Exception) { emptyMap() }
                val reqBuilder = Request.Builder().url(url)
                headersMap.forEach { (k, v) -> if (isSafeHeader(k)) reqBuilder.addHeader(k, v) }
                if (isFunpayHost(url)) reqBuilder.addHeader("Cookie", repository.getCookieString())
                repository.repoClient.newCall(reqBuilder.build()).execute().use { resp ->
                    val bytes = resp.body?.byteStream()?.let { it.readNBytesSafe(MAX_HTTP_RESPONSE_BYTES) } ?: ByteArray(0)
                    JSONObject().put("code", resp.code).put("body", String(bytes, Charsets.UTF_8)).toString()
                }
            } ?: JSONObject().put("error", "timeout").toString()
        } catch (e: Exception) { JSONObject().put("error", e.message ?: "error").toString() }
    }

    @JavascriptInterface fun httpPost(url: String, bodyStr: String, headersJson: String): String = runBlocking(Dispatchers.IO) {
        try {
            withTimeoutOrNull(HTTP_TIMEOUT_MS) {
                if (!isHttpUrlAllowed(url)) return@withTimeoutOrNull JSONObject().put("error", "URL не разрешён").toString()
                val headersType = object : TypeToken<Map<String, String>>() {}.type
                val headersMap: Map<String, String> = try { gson.fromJson(headersJson, headersType) ?: emptyMap() } catch (_: Exception) { emptyMap() }
                val cType = headersMap["Content-Type"] ?: "application/x-www-form-urlencoded"
                val reqBody = bodyStr.toRequestBody(cType.toMediaTypeOrNull())
                val reqBuilder = Request.Builder().url(url).post(reqBody)
                headersMap.forEach { (k, v) -> if (isSafeHeader(k)) reqBuilder.addHeader(k, v) }
                if (isFunpayHost(url)) reqBuilder.addHeader("Cookie", repository.getCookieString())
                repository.repoClient.newCall(reqBuilder.build()).execute().use { resp ->
                    val bytes = resp.body?.byteStream()?.let { it.readNBytesSafe(MAX_HTTP_RESPONSE_BYTES) } ?: ByteArray(0)
                    JSONObject().put("code", resp.code).put("body", String(bytes, Charsets.UTF_8)).toString()
                }
            } ?: JSONObject().put("error", "timeout").toString()
        } catch (e: Exception) { JSONObject().put("error", e.message ?: "error").toString() }
    }

    @JavascriptInterface fun fetchImageBase64(url: String): String = runBlocking(Dispatchers.IO) {
        try {
            withTimeoutOrNull(HTTP_TIMEOUT_MS) {
                if (!isHttpUrlAllowed(url)) return@withTimeoutOrNull ""
                val reqBuilder = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0")
                if (isFunpayHost(url)) reqBuilder.addHeader("Cookie", repository.getCookieString())
                repository.repoClient.newCall(reqBuilder.build()).execute().use { resp ->
                    val mime = resp.header("Content-Type") ?: "image/jpeg"
                    val bytes = resp.body?.byteStream()?.readNBytesSafe(MAX_IMAGE_BYTES.toInt()) ?: return@withTimeoutOrNull ""
                    if (bytes.size >= MAX_IMAGE_BYTES) return@withTimeoutOrNull ""
                    "data:$mime;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                }
            } ?: ""
        } catch (_: Exception) { "" }
    }

    @JavascriptInterface fun showToast(msg: String) {
        try {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, msg.take(500), Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {}
    }
    @JavascriptInterface fun showNotification(title: String, msg: String) {
        try {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(NotificationChannel("plugin_ch", "Plugins", NotificationManager.IMPORTANCE_DEFAULT))
            }
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(context, "plugin_ch") else @Suppress("DEPRECATION") Notification.Builder(context)
            val notifId = (title.hashCode() xor msg.hashCode())
            nm.notify(notifId, builder.setSmallIcon(android.R.drawable.stat_notify_chat).setContentTitle(title).setContentText(msg).setAutoCancel(true).build())
        } catch (_: Exception) {}
    }
    @JavascriptInterface fun vibrate(ms: Long) {
        try {
            val safeMs = ms.coerceIn(1L, 5_000L)
            val v = context.getSystemService(Vibrator::class.java) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) v.vibrate(VibrationEffect.createOneShot(safeMs, VibrationEffect.DEFAULT_AMPLITUDE)) else @Suppress("DEPRECATION") v.vibrate(safeMs)
        } catch (_: Exception) {}
    }
    @JavascriptInterface fun log(msg: String) = LogManager.addLog("🧩 [ПЛАГИН]: ${msg.take(2000)}")
    @JavascriptInterface fun updateWidgets() {
        try { CoroutineScope(Dispatchers.Main).launch { WidgetManager.updateAllWidgets(context) } } catch (_: Exception) {}
    }

    @JavascriptInterface fun base64ToLocalUri(dataUrl: String): String {
        return try {
            val cleanBase64 = if (dataUrl.contains(",")) dataUrl.substringAfter(",") else dataUrl
            val decodedBytes = android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT)
            if (decodedBytes.size > MAX_IMAGE_BYTES) {
                LogManager.addLog("❌ Плагин: картинка слишком большая (${decodedBytes.size} байт)")
                return ""
            }
            val dir = File(context.cacheDir, "plugin_canvas").apply { if (!exists()) mkdirs() }
            val cutoff = System.currentTimeMillis() - 24L * 3600L * 1000L
            try {
                dir.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
            } catch (_: Exception) {}
            val tempFile = File(dir, "canvas_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.png")
            tempFile.writeBytes(decodedBytes)
            Uri.fromFile(tempFile).toString()
        } catch (_: Exception) {
            LogManager.addLog("❌ Плагин: Ошибка сохранения картинки (неверный base64)")
            ""
        }
    }

    @JavascriptInterface fun addUiSlot(slotName: String, uiJson: String) {
        if (slotName.isBlank()) return
        if (uiJson.length > MAX_UI_SLOT_LEN) return
        try {
            CoroutineScope(Dispatchers.Main).launch { PluginEngine.uiSlots[slotName] = uiJson }
        } catch (_: Exception) {}
    }
    @JavascriptInterface fun removeUiSlot(slotName: String) {
        try { CoroutineScope(Dispatchers.Main).launch { PluginEngine.uiSlots.remove(slotName) } } catch (_: Exception) {}
    }
    @JavascriptInterface fun uiGetState(key: String): String = uiStateMap[key] ?: ""
    @JavascriptInterface fun uiSetState(key: String, value: String) {
        if (key.isBlank()) return
        val v = if (value.length > MAX_UI_STATE_VALUE_LEN) value.substring(0, MAX_UI_STATE_VALUE_LEN) else value
        uiStateMap[key] = v
    }

    @JavascriptInterface fun storageGet(key: String) = try {
        context.getSharedPreferences("plugins_storage", Context.MODE_PRIVATE).getString(key, "") ?: ""
    } catch (_: Exception) { "" }
    @JavascriptInterface fun storageSet(key: String, value: String) {
        try {
            if (value.length > MAX_STORAGE_VALUE_LEN) {
                LogManager.addLog("⚠️ Plugin storage: значение ключа '$key' превышает лимит ($MAX_STORAGE_VALUE_LEN), отбрасываем")
                return
            }
            context.getSharedPreferences("plugins_storage", Context.MODE_PRIVATE).edit().putString(key, value).apply()
        } catch (_: Exception) {}
    }

    @JavascriptInterface fun aiAsk(prompt: String): String = runBlocking(Dispatchers.IO) {
        try {
            withTimeoutOrNull(HTTP_TIMEOUT_MS) {
                val payload = JSONObject().put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
                val req = Request.Builder().url("https://fptools.onrender.com/api/ai")
                    .header("Authorization", "Bearer fptoolsdim")
                    .post(payload.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                    .build()
                repository.repoClient.newCall(req).execute().use { resp ->
                    JSONObject(resp.body?.string() ?: "{}").optString("response", "ERROR")
                }
            } ?: "ERROR: timeout"
        } catch (e: Exception) { "ERROR: ${e.message}" }
    }
    @JavascriptInterface fun aiRewrite(text: String, context: String): String = runBlocking(Dispatchers.IO) {
        try { withTimeoutOrNull(HTTP_TIMEOUT_MS) { repository.rewriteMessage(text, context) ?: "ERROR" } ?: "ERROR: timeout" } catch (_: Exception) { "ERROR" }
    }
    @JavascriptInterface fun aiTranslate(text: String): String = runBlocking(Dispatchers.IO) {
        try { withTimeoutOrNull(HTTP_TIMEOUT_MS) { repository.translateLotDescriptionRuToEn(text) ?: "ERROR" } ?: "ERROR: timeout" } catch (_: Exception) { "ERROR" }
    }

    private fun parseHost(url: String): String? = try {
        val uri = URI(url)
        uri.host?.lowercase()
    } catch (_: Exception) { null }

    private fun isFunpayHost(url: String): Boolean {
        val h = parseHost(url) ?: return false
        return h == "funpay.com" || h.endsWith(".funpay.com")
    }

    private fun isHttpUrlAllowed(url: String): Boolean {
        return try {
            val uri = URI(url)
            val scheme = uri.scheme?.lowercase() ?: return false
            if (scheme != "http" && scheme != "https") return false
            val host = uri.host
            !host.isNullOrBlank()
        } catch (_: Exception) { false }
    }

    private fun isSafeHeader(name: String): Boolean {
        val low = name.lowercase()
        return low != "cookie" && low != "host" && low != "content-length"
    }

    companion object {
        private const val DEFAULT_TIMEOUT_MS = 15_000L
        private const val HTTP_TIMEOUT_MS = 30_000L
        private const val MAX_HTTP_RESPONSE_BYTES = 8 * 1024 * 1024
        private const val MAX_IMAGE_BYTES = 8L * 1024L * 1024L
        private const val MAX_AVATAR_BYTES = 4 * 1024 * 1024
        private const val MAX_UI_SLOT_LEN = 256 * 1024
        private const val MAX_UI_STATE_VALUE_LEN = 64 * 1024
        private const val MAX_STORAGE_VALUE_LEN = 512 * 1024

        private fun java.io.InputStream.readNBytesSafe(max: Int): ByteArray {
            val buf = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            var total = 0
            while (total < max) {
                val n = this.read(chunk, 0, minOf(chunk.size, max - total))
                if (n <= 0) break
                buf.write(chunk, 0, n)
                total += n
            }
            return buf.toByteArray()
        }
    }
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

private fun jsLit(value: String): String = JSONObject.quote(value)

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
                    PluginEngine.evaluateJs("fptNative.uiGetState(${jsLit(stateKey)})") { res ->
                        val clean = res?.replace("\"", "")
                        checked = (clean == "true")
                    }
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = { v ->
                    checked = v
                    if (stateKey.isNotEmpty()) triggerJsCallback("fptNative.uiSetState(${jsLit(stateKey)}, ${jsLit(v.toString())}); " + node.optString("onChange", ""))
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
                    PluginEngine.evaluateJs("fptNative.uiGetState(${jsLit(stateKey)})") { res ->
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
                        triggerJsCallback("fptNative.uiSetState(${jsLit(stateKey)}, ${jsLit(v)}); " + node.optString("onChange", ""))
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
                    PluginEngine.evaluateJs("fptNative.uiGetState(${jsLit(stateKey)})") { res ->
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
                        if (stateKey.isNotEmpty()) triggerJsCallback("fptNative.uiSetState(${jsLit(stateKey)}, ${jsLit(v.toString())}); " + node.optString("onChange", ""))
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
                    PluginEngine.evaluateJs("fptNative.uiGetState(${jsLit(stateKey)})") { res ->
                        val clean = res?.replace("\"", "") ?: "0"
                        sVal = clean.toFloatOrNull() ?: 0f
                    }
                }
            }
            Slider(
                value = sVal,
                onValueChange = { v ->
                    sVal = v
                    if (stateKey.isNotEmpty()) triggerJsCallback("fptNative.uiSetState(${jsLit(stateKey)}, ${jsLit(v.toString())}); " + node.optString("onChange", ""))
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
                // FIX #2: восстановлена обрезанная строка placeholder
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = "// @name MyPlugin\n// @author Author\n// @version 1.0\n// @description Описание\n\n// Ваш код здесь...",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = Color(0xFF666666)
                        )
                    },
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
                            if (url.isBlank() || !(url.startsWith("http://") || url.startsWith("https://"))) {
                                errorMsg = "Введите корректную ссылку (http:// или https://)"
                                return@Button
                            }
                            isLoading = true
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val client = OkHttpClient.Builder()
                                        .connectTimeout(15, TimeUnit.SECONDS)
                                        .readTimeout(30, TimeUnit.SECONDS)
                                        .callTimeout(45, TimeUnit.SECONDS)
                                        .build()
                                    val request = Request.Builder().url(url).build()
                                    val response = client.newCall(request).execute()
                                    if (!response.isSuccessful) throw Exception("Сервер вернул ошибку ${response.code}")
                                    val stream = response.body?.byteStream() ?: throw Exception("Пустой файл")
                                    val buf = java.io.ByteArrayOutputStream()
                                    val chunk = ByteArray(8192)
                                    var total = 0
                                    val maxBytes = 5 * 1024 * 1024
                                    while (total < maxBytes + 1) {
                                        val n = stream.read(chunk)
                                        if (n <= 0) break
                                        buf.write(chunk, 0, n); total += n
                                    }
                                    if (total > maxBytes) throw Exception("Файл слишком большой (> 5 МБ)")
                                    val code = String(buf.toByteArray(), Charsets.UTF_8)
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
                            PluginEngine.updatePluginMeta(context, repository, plugin.id) { it2 -> it2.copy(isEnabled = it) }
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
                    IconButton(onClick = { showEditor = true }) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Редактировать",
                            tint = ThemeManager.parseColor(theme.textSecondaryColor)
                        )
                    }

                    Spacer(Modifier.weight(1f))

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

                if (code.length > 5 * 1024 * 1024) { sizeError = true; isLoading = false; return@withContext }

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
                        Text("Файл слишком большой (> 5 МБ). Возможен вредоносный код.", color = Color.Red)
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