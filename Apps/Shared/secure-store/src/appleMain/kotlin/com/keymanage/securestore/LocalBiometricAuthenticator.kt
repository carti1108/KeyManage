package com.keymanage.securestore

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSError
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAErrorBiometryNotEnrolled
import platform.LocalAuthentication.LAErrorSystemCancel
import platform.LocalAuthentication.LAErrorUserCancel
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics
import kotlin.coroutines.resume

// LocalAuthentication(Objective-C API)을 Kotlin/Native에서 직접 호출한다
@OptIn(ExperimentalForeignApi::class)
class LocalBiometricAuthenticator : BiometricAuthenticator {

    override fun availability(): BiometricAvailability = memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        val canEvaluate = LAContext().canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, error.ptr)
        when {
            canEvaluate -> BiometricAvailability.AVAILABLE
            error.value?.code == LAErrorBiometryNotEnrolled -> BiometricAvailability.NOT_ENROLLED
            else -> BiometricAvailability.UNAVAILABLE
        }
    }

    override suspend fun authenticate(reason: String): BiometricResult = evaluate(LAContext(), reason)

    internal suspend fun evaluate(context: LAContext, reason: String): BiometricResult =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { context.invalidate() }
            context.evaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, reason) { success, error ->
                continuation.resume(toResult(success, error))
            }
        }

    private fun toResult(success: Boolean, error: NSError?): BiometricResult = when {
        success -> BiometricResult.Success
        error?.code == LAErrorUserCancel || error?.code == LAErrorSystemCancel -> BiometricResult.Cancelled
        else -> BiometricResult.Failed(error?.code ?: 0, error?.localizedDescription ?: "알 수 없음")
    }
}
