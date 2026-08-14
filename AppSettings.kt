package com.billiards.analyzer

import android.content.Context

object AppSettings {
    private const val PREFS = "billiards_settings"
    private const val KEY_AUTO_DETECT = "auto_detect"
    private const val KEY_DEFAULT_POWER = "default_power"
    private const val KEY_SHOW_GUIDES = "show_guides"

    fun autoDetectEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_AUTO_DETECT, true)

    fun setAutoDetectEnabled(ctx: Context, value: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_AUTO_DETECT, value).apply()
    }

    fun defaultPower(ctx: Context): Int =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_DEFAULT_POWER, 65)

    fun setDefaultPower(ctx: Context, value: Int) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_DEFAULT_POWER, value).apply()
    }

    fun showGuides(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SHOW_GUIDES, true)

    fun setShowGuides(ctx: Context, value: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_SHOW_GUIDES, value).apply()
    }
}
