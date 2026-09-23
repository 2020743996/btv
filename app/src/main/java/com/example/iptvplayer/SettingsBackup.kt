package com.example.iptvplayer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal data class SettingsBackup(
    val m3uUrls: List<String>,
    val epgUrl: String?,
    val favorites: List<String>,
    val recent: List<String>,
    val elderMode: Boolean,
    val fontSize: Int,
    val startupMode: StartupMode,
    val pictureMode: PictureMode
)

internal fun encodeBackup(data: SettingsBackup): String = JSONObject().apply {
    put("version", 1)
    put("m3uUrls", JSONArray(data.m3uUrls))
    put("epgUrl", data.epgUrl ?: JSONObject.NULL)
    put("favorites", JSONArray(data.favorites))
    put("recent", JSONArray(data.recent))
    put("elderMode", data.elderMode)
    put("fontSize", data.fontSize)
    put("startupMode", data.startupMode.name)
    put("pictureMode", data.pictureMode.name)
}.toString(2)

internal fun decodeBackup(text: String): SettingsBackup {
    val json = JSONObject(text)
    require(json.getInt("version") == 1) { "不支持的备份版本" }
    fun strings(key: String): List<String> {
        val array = json.getJSONArray(key)
        require(array.length() <= 10_000) { "备份内容过多" }
        return (0 until array.length()).map { array.getString(it) }
    }
    val urls = strings("m3uUrls")
    require(urls.all(::isSupportedM3uUrl)) { "备份包含无效的频道源地址" }
    val epg = json.optString("epgUrl").takeUnless { it.isBlank() || it == "null" }
    require(epg == null || isSupportedM3uUrl(epg)) { "备份包含无效的节目单地址" }
    val fontSize = json.getInt("fontSize")
    require(fontSize in 0..2) { "备份中的字体设置无效" }
    return SettingsBackup(
        normalizeOrderedStringList(urls), epg,
        strings("favorites").distinct(), strings("recent").distinct(),
        json.getBoolean("elderMode"), fontSize,
        StartupMode.valueOf(json.getString("startupMode")),
        PictureMode.valueOf(json.getString("pictureMode"))
    )
}

internal fun readBackup(context: Context): SettingsBackup = SettingsBackup(
    getM3uUrls(context), getEpgUrl(context), getFavorites(context).toList(),
    getRecentChannels(context), isElderMode(context), getFontSize(context),
    getStartupMode(context), getPictureMode(context)
)

internal fun restoreBackup(context: Context, data: SettingsBackup): Boolean {
    val preferences = context.getSharedPreferences("iptv_settings", Context.MODE_PRIVATE)
    val saved = preferences.edit()
        .putString("m3u_urls_ordered", encodeOrderedStringList(data.m3uUrls))
        .putStringSet("m3u_urls", data.m3uUrls.toSet())
        .putString("epg_url", data.epgUrl)
        .putStringSet("favorite_channels", data.favorites.toSet())
        .putString("recent_channels", JSONArray(data.recent).toString())
        .putBoolean("elder_mode", data.elderMode)
        .putInt("font_size", data.fontSize)
        .putString("startup_mode", data.startupMode.name)
        .putString("picture_mode", data.pictureMode.name)
        .commit()
    if (saved) {
        ChannelCache.invalidate()
        EpgCache.configureSources(emptySet())
    }
    return saved
}
