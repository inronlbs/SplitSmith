package com.splitsmith.app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

object LocalContactCache {

    private const val PREFS_NAME = "splitsmith_contact_cache"
    private const val KEY_PROFILES = "cached_profiles_json"

    private val memoryCache = ConcurrentHashMap<String, UserProfile>()
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        loadFromDisk()
    }

    fun get(uid: String): UserProfile? {
        if (uid.isBlank()) return null
        return memoryCache[uid]
    }

    fun getAll(): Map<String, UserProfile> {
        return memoryCache.toMap()
    }

    fun put(profile: UserProfile, context: Context? = null) {
        if (profile.uid.isBlank()) return
        memoryCache[profile.uid] = profile
        val ctx = context?.applicationContext ?: appContext
        if (ctx != null) {
            persistAsync(ctx)
        }
    }

    fun putAll(profiles: Collection<UserProfile>, context: Context? = null) {
        var changed = false
        for (p in profiles) {
            if (p.uid.isNotBlank()) {
                memoryCache[p.uid] = p
                changed = true
            }
        }
        if (changed) {
            val ctx = context?.applicationContext ?: appContext
            if (ctx != null) {
                persistAsync(ctx)
            }
        }
    }

    private fun loadFromDisk() {
        val ctx = appContext ?: return
        try {
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonStr = prefs.getString(KEY_PROFILES, null) ?: return
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue
                val uid = obj.optString("uid", "")
                if (uid.isNotBlank()) {
                    val profile = UserProfile(
                        uid = uid,
                        displayName = obj.optString("displayName", ""),
                        email = obj.optString("email", ""),
                        avatarUrl = obj.optString("avatarUrl", ""),
                        upiId = obj.optString("upiId", ""),
                        shortCode = obj.optString("shortCode", "")
                    )
                    memoryCache[uid] = profile
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("LocalContactCache", "Failed to load profiles from disk: ${e.message}")
        }
    }

    private fun persistAsync(context: Context) {
        val snapshot = memoryCache.values.toList()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).run {
            try {
                val jsonArray = JSONArray()
                for (p in snapshot) {
                    val obj = JSONObject().apply {
                        put("uid", p.uid)
                        put("displayName", p.displayName)
                        put("email", p.email)
                        put("avatarUrl", p.avatarUrl)
                        put("upiId", p.upiId)
                        put("shortCode", p.shortCode)
                    }
                    jsonArray.put(obj)
                }
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putString(KEY_PROFILES, jsonArray.toString()).apply()
            } catch (e: Exception) {
                android.util.Log.w("LocalContactCache", "Failed to persist profiles: ${e.message}")
            }
        }
    }
}
