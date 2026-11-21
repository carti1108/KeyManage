package com.keymanage.securestore

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.posix.memcpy

// 사전이 값을 retain하므로, Kotlin 객체에서 만든 값은 넣은 직후 해제한다
@OptIn(ExperimentalForeignApi::class)
internal class KeychainQuery(val dict: CFMutableDictionaryRef?) {
    fun add(key: CFTypeRef?, value: CFTypeRef?) = CFDictionaryAddValue(dict, key, value)
    fun addObject(key: CFTypeRef?, value: Any) {
        val retained = CFBridgingRetain(value)
        CFDictionaryAddValue(dict, key, retained)
        CFRelease(retained)
    }
}

@OptIn(ExperimentalForeignApi::class)
internal inline fun <T> withKeychainQuery(
    service: String,
    account: String,
    extra: KeychainQuery.() -> Unit,
    block: (CFMutableDictionaryRef?) -> T,
): T {
    val dict = CFDictionaryCreateMutable(kCFAllocatorDefault, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
    try {
        KeychainQuery(dict).apply {
            add(kSecClass, kSecClassGenericPassword)
            addObject(kSecAttrService, service)
            addObject(kSecAttrAccount, account)
            extra()
        }
        return block(dict)
    } finally {
        CFRelease(dict)
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData() else usePinned { NSData.dataWithBytes(it.addressOf(0), size.toULong()) }

@OptIn(ExperimentalForeignApi::class)
internal fun NSData.toByteArray(): ByteArray {
    val bytes = ByteArray(length.toInt())
    if (bytes.isNotEmpty()) bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
    return bytes
}
