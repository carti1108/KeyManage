package com.keymanage.securestore

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.CFBridgingRelease
import platform.Foundation.NSData
import platform.LocalAuthentication.LAContext
import platform.Security.SecAccessControlCreateWithFlags
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAccessControlBiometryCurrentSet
import platform.Security.kSecAttrAccessControl
import platform.Security.kSecAttrAccessibleWhenUnlockedThisDeviceOnly
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecUseAuthenticationContext
import platform.Security.kSecValueData

// 키체인 항목에 생체 인증 접근 제어(현재 등록된 생체만 허용)를 건다.
// 읽을 때는 LAContext로 먼저 인증하고, 그 컨텍스트를 키체인 조회에 넘겨 두 번 묻지 않게 한다
@OptIn(ExperimentalForeignApi::class)
class BiometricKeychainStore(
    private val service: String,
    private val authenticator: LocalBiometricAuthenticator = LocalBiometricAuthenticator(),
) : BiometricSecureStore {

    override fun saveProtected(account: String, value: ByteArray) {
        delete(account)
        val access = SecAccessControlCreateWithFlags(
            kCFAllocatorDefault,
            kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
            kSecAccessControlBiometryCurrentSet,
            null,
        ) ?: throw SecureStoreException(0, "접근 제어 생성")
        try {
            val status = withKeychainQuery(service, account, {
                addObject(kSecValueData, value.toNSData())
                add(kSecAttrAccessControl, access)
            }) { SecItemAdd(it, null) }
            if (status != errSecSuccess) throw SecureStoreException(status, "보호 저장")
        } finally {
            CFRelease(access)
        }
    }

    override suspend fun loadProtected(account: String, reason: String): ByteArray? {
        val context = LAContext()
        val result = authenticator.evaluate(context, reason)
        if (result != BiometricResult.Success) throw BiometricAuthenticationException(result)
        return memScoped {
            val data = alloc<CFTypeRefVar>()
            val status = withKeychainQuery(service, account, {
                add(kSecReturnData, kCFBooleanTrue)
                add(kSecMatchLimit, kSecMatchLimitOne)
                addObject(kSecUseAuthenticationContext, context)
            }) { SecItemCopyMatching(it, data.ptr) }
            when (status) {
                errSecSuccess -> (CFBridgingRelease(data.value) as NSData).toByteArray()
                errSecItemNotFound -> null
                else -> throw SecureStoreException(status, "보호 조회")
            }
        }
    }

    override fun delete(account: String) {
        val status = withKeychainQuery(service, account, {}) { SecItemDelete(it) }
        if (status != errSecSuccess && status != errSecItemNotFound) throw SecureStoreException(status, "삭제")
    }
}
