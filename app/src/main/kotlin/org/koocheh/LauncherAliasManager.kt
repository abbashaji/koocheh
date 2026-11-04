package org.koocheh

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

enum class LauncherAlias(val className: String) {
    PRIMARY(".AliasPrimary"),
    NOTES(".AliasNotes"),
    CALC(".AliasCalc"),
}

/** Enables exactly one launcher alias and disables the others. */
class LauncherAliasManager(private val context: Context) {

    fun select(alias: LauncherAlias) {
        val pm = context.packageManager
        // Enable the chosen alias first so a launcher entry always exists.
        LauncherAlias.entries.sortedByDescending { it == alias }.forEach { entry ->
            val state = if (entry == alias) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            pm.setComponentEnabledSetting(
                ComponentName(context.packageName, "org.koocheh${entry.className}"),
                state,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}
