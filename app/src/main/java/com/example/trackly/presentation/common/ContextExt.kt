package com.example.trackly.presentation.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** Finds the hosting [Activity] of a (possibly wrapped) context. */
fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
