package app.lockin.android

import android.content.Context
import android.util.TypedValue

/** The colour themes. Hazard (yellow) is free; the rest need Pro. */
enum class LockTheme(val id: String, val label: String, val styleRes: Int, val colorRes: Int, val premium: Boolean) {
    HAZARD("hazard", "Hazard", R.style.Theme_LockIn, R.color.accent, premium = false),
    BLOOD("blood", "Blood", R.style.Theme_LockIn_Blood, R.color.theme_blood, premium = true),
    TOXIC("toxic", "Toxic", R.style.Theme_LockIn_Toxic, R.color.theme_toxic, premium = true),
    ICE("ice", "Ice", R.style.Theme_LockIn_Ice, R.color.theme_ice, premium = true),
    VENOM("venom", "Venom", R.style.Theme_LockIn_Venom, R.color.theme_venom, premium = true),
    GOLD("gold", "Gold", R.style.Theme_LockIn_Gold, R.color.theme_gold, premium = true),
    GHOST("ghost", "Ghost", R.style.Theme_LockIn_Ghost, R.color.theme_ghost, premium = true);

    companion object {
        fun byId(id: String?): LockTheme = entries.firstOrNull { it.id == id } ?: HAZARD

        /** The theme in use: the chosen one, or Hazard when it needs Pro and Pro is off. */
        fun current(prefs: LockPrefs): LockTheme {
            val chosen = byId(prefs.themeId)
            return if (chosen.premium && !prefs.pro) HAZARD else chosen
        }

        /** The accent colour of the theme [context] is styled with. */
        fun accent(context: Context): Int {
            val value = TypedValue()
            return if (context.theme.resolveAttribute(R.attr.lockinAccent, value, true)) value.data
            else context.getColor(R.color.accent)
        }
    }
}
