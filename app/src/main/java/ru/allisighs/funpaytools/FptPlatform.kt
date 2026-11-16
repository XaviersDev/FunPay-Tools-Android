package ru.allisighs.funpaytools

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Мелкие помощники для кода, общего с версией для iPhone (FptAccount, FptDecor, EmployeeMode):
 * там те же имена реализованы под iOS, здесь под Android.
 */
@SuppressLint("StaticFieldLeak")
lateinit var AppContext: Context

val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

object PlatformBridge {
    val filesDir: String get() = AppContext.filesDir.absolutePath

    fun openUrl(url: String): Boolean = try {
        AppContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        false
    }

    fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else Handler(Looper.getMainLooper()).post(block)
    }
}
