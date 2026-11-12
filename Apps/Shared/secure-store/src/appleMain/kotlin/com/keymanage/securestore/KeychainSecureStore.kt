package com.keymanage.securestore

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.CFBridgingRelease
import platform.Foundation.NSData
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleWhenUnlockedThisDeviceOnly
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

// Security 프레임워크의 SecItem API를 Kotlin/Native에서 직접 호출한다. Swift 코드 없음
@OptIn(ExperimentalForeignApi::class)
class KeychainSecureStore(private val service: String) : SecureStore {

    override fun save(account: String, value: ByteArray) {
        delete(account)
        val status = withKeychainQuery(service, account, {
            addObject(kSecValueData, value.toNSData())
            add(kSecAttrAccessible, kSecAttrAccessibleWhenUnlockedThisDeviceOnly)
        }) { SecItemAdd(it, null) }
        if (status != errSecSuccess) throw SecureStoreException(status, "저장")
    }

    override fun load(account: String): ByteArray? = memScoped {
        val result = alloc<CFTypeRefVar>()
        val status = withKeychainQuery(service, account, {
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
        val status = withKeychainQuery(service, account, {}) { SecItemDelete(it) }
        if (status != errSecSuccess && status != errSecItemNotFound) throw SecureStoreException(status, "삭제")
    }
}
