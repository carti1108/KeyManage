package com.keymanage.devicestore

/** 기기 저장소에 읽거나 쓰지 못함(저장 공간 부족, 사생활 보호 모드, 다른 탭의 차단 등) */
class DeviceStorageException(message: String, cause: Throwable? = null) : Exception(message, cause)
