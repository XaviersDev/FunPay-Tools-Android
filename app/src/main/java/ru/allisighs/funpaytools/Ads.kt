package ru.allisighs.funpaytools

import android.app.Activity
import android.content.Context
import android.os.Build
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import androidx.annotation.RequiresApi
import com.google.firebase.database.FirebaseDatabase
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK
import java.util.UUID

object Ads {
    
    private const val START_IO_APP_ID = "201738917"

    fun init(context: Context) {
        
        
        StartAppSDK.init(context, START_IO_APP_ID, false)

        
        
        StartAppSDK.setTestAdsEnabled(false)
    }

    fun showRewardedAd(activity: Activity, onComplete: (Boolean) -> Unit) {
        
        val ad = StartAppAd(activity)
        ad.loadAd(object : com.startapp.sdk.adsbase.adlisteners.AdEventListener {
            override fun onReceiveAd(p0: com.startapp.sdk.adsbase.Ad) {
                ad.showAd()
                onComplete(true)
            }

            override fun onFailedToReceiveAd(p0: com.startapp.sdk.adsbase.Ad?) {
                
                onComplete(false)
            }
        })
    }
}


object Stats {
    private val db = FirebaseDatabase.getInstance()
    private val statsRef = db.getReference("stats")
    private var deviceId: String = ""
    private var presenceListener: ValueEventListener? = null

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("stats", Context.MODE_PRIVATE)
        deviceId = prefs.getString("id", null) ?: run {
            val id = UUID.randomUUID().toString()
            prefs.edit().putString("id", id).apply()
            id
        }
    }

    fun setOnline() {
        if (deviceId.isEmpty() || presenceListener != null) return

        presenceListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.getValue(Boolean::class.java) != true) return
                val ref = statsRef.child("online").child(deviceId)
                
                ref.onDisconnect().removeValue().addOnCompleteListener {
                    ref.setValue(System.currentTimeMillis())
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }

        db.getReference(".info/connected").addValueEventListener(presenceListener!!)
    }

    fun setOffline() {
        presenceListener?.let {
            db.getReference(".info/connected").removeEventListener(it)
            presenceListener = null
        }
        if (deviceId.isNotEmpty()) {
            statsRef.child("online").child(deviceId).removeValue()
        }
    }

    fun getOnlineCount(callback: (Int) -> Unit) {
        statsRef.child("online").get()
            .addOnSuccessListener { callback(it.childrenCount.toInt()) }
        
    }
}