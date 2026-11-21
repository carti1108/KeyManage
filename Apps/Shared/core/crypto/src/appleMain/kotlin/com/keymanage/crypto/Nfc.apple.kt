package com.keymanage.crypto

import platform.Foundation.NSString
import platform.Foundation.precomposedStringWithCanonicalMapping

@Suppress("CAST_NEVER_SUCCEEDS")
internal actual fun normalizeNfc(value: String): String =
    (value as NSString).precomposedStringWithCanonicalMapping
