package org.koocheh.ui.lock

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/** Local seam over org.koocheh.security.SecureWindowPolicy.apply(window). */
fun interface SecureWindowApplier {
    fun apply(window: Window)
}

/** Wrap every screen in this cell so SecureWindowPolicy is applied. */
@Composable
fun SecureScreen(applier: SecureWindowApplier, content: @Composable () -> Unit) {
    val view = LocalView.current
    DisposableEffect(view) {
        view.context.findActivity()?.window?.let(applier::apply)
        onDispose { }
    }
    content()
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
