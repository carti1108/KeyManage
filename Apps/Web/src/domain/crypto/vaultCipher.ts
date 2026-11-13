// 볼트 평문 JSON ↔ 암호화 페이로드 변환(vault-spec 3장, ADR-0011)은 공유 Kotlin(Apps/Shared/crypto)이 한다.
// 웹에는 페이로드 타입과 화면이 오류 종류를 구분하는 데 쓰는 오류 정의만 남긴다

/** 서버와 기기에 저장되는 암호화 볼트. 평문 헤더(version, cipher)는 AAD로 인증된다 */
export interface EncryptedVaultPayload {
  readonly version: 1
  readonly cipher: 'AES-256-GCM'
  readonly iv: string
  readonly ciphertext: string
  readonly tag: string
}

/** 키가 다르거나(다른 마스터 비밀번호·계정) 볼트가 변조되어 인증 태그 검증에 실패함 */
export class VaultDecryptError extends Error {
  constructor(message = '마스터 키가 다르거나 볼트가 변조됨') {
    super(message)
    this.name = 'VaultDecryptError'
  }
}

/** 이 앱이 모르는 형식(version·cipher)의 볼트. 복호화를 시도하지 않는다 */
export class UnsupportedPayloadError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'UnsupportedPayloadError'
  }
}
