package com.example.trackly.core.util

/** Parses a persisted enum name, falling back to [default] for unknown or corrupted values. */
inline fun <reified T : Enum<T>> String?.toEnumOrDefault(default: T): T =
    if (this == null) default else enumValues<T>().firstOrNull { it.name == this } ?: default
