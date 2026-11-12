package com.keymanage.devicestore

// 한 계정의 기기 기록(이메일, KDF 값, 볼트 암호문, revision)을 둔다. 비밀 평문·키·토큰은 담지 않는다.
// 기록은 공통 코드가 만든 JSON 문자열이다. 플랫폼 구현은 형식을 해석하지 않고 저장 위치만 맡는다
interface DeviceStore {
    /** 기록이 없으면 null. 읽은 값은 믿지 않고 호출한 쪽에서 확인한다 */
    suspend fun load(): String?
    suspend fun save(recordJson: String)
    suspend fun clear()
}

/** 기기 저장소에 읽거나 쓰지 못함(저장 공간 부족, 사생활 보호 모드, 다른 탭의 차단 등) */
class DeviceStorageException(message: String, cause: Throwable? = null) : Exception(message, cause)
