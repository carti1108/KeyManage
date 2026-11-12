package com.keymanage.securestore

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleWhenUnlockedThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.posix.memcpy

// Security 프레임워크의 SecItem API를 Kotlin/Native에서 직접 호출한다. Swift 코드 없음
@OptIn(ExperimentalForeignApi::class)
class KeychainSecureStore(private val service: String) : SecureStore {

    override fun save(account: String, value: ByteArray) {
        delete(account)
        val status = withQuery(account, {
            add(kSecValueData, value.toNSData())
            add(kSecAttrAccessible, kSecAttrAccessibleWhenUnlockedThisDeviceOnly)
        }) { SecItemAdd(it, null) }
        if (status != errSecSuccess) throw SecureStoreException(status, "저장")
    }

    override fun load(account: String): ByteArray? = memScoped {
        val result = alloc<CFTypeRefVar>()
        val status = withQuery(account, {
            add(kSecReturnData, kCFBooleanTrue)
            add(kSecMatchLimit, kSecMatchLimitOne)
        }) { SecItemCopyMatching(it, result.ptr) }
        when (status) {
            errSecSuccess -> (CFBridgingRelease(result.value) as NSData).toByteArray()
            errSecItemNotFound -> null
            else -> throw SecureStoreException(status, "조회")
        }
    }

    override fun delete(account: String) {
        val status = withQuery(account, {}) { SecItemDelete(it) }
        if (status != errSecSuccess && status != errSecItemNotFound) throw SecureStoreException(status, "삭제")
    }

    // 사전이 값을 retain하므로, Kotlin 객체에서 만든 값은 넣은 직후 해제한다
    private class QueryBuilder(val dict: CFMutableDictionaryRef?) {
        fun add(key: CFTypeRef?, value: CFTypeRef?) = CFDictionaryAddValue(dict, key, value)
        fun add(key: CFTypeRef?, value: Any) {
            val retained = CFBridgingRetain(value)
            CFDictionaryAddValue(dict, key, retained)
            CFRelease(retained)
        }
    }

    private inline fun <T> withQuery(account: String, extra: QueryBuilder.() -> Unit, block: (CFMutableDictionaryRef?) -> T): T {
        val dict = CFDictionaryCreateMutable(kCFAllocatorDefault, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        try {
            QueryBuilder(dict).apply {
                add(kSecClass, kSecClassGenericPassword)
                add(kSecAttrService, service)
                add(kSecAttrAccount, account)
                extra()
            }
            return block(dict)
        } finally {
            CFRelease(dict)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData() else usePinned { NSData.dataWithBytes(it.addressOf(0), size.toULong()) }

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val bytes = ByteArray(length.toInt())
    if (bytes.isNotEmpty()) bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
    return bytes
}
