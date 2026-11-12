package com.keymanage.crypto

internal actual fun normalizeNfc(value: String): String = value.asDynamic().normalize("NFC") as String
