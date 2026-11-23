package com.keymanage.securestore

class SecureStoreException(val status: Int, operation: String, cause: Throwable? = null) :
    Exception("보안 저장소 $operation 실패 (status=$status)", cause)
