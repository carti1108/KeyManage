// 마스터 비밀번호 → 마스터 키 → 암호화 키·인증 해시 유도(vault-spec 2장, ADR-0008, ADR-0011)는 공유 Kotlin(Apps/Shared/crypto)이 한다.
// 웹에는 기기 기록 검사(deviceRecord)가 쓰는 KDF 한계값과 화면이 오류 종류를 구분하는 데 쓰는 오류 정의만 남긴다

export const MIN_ITERATIONS = 600_000
// 서버가 지나치게 큰 값을 내려줘 기기를 오래 멈추게 하는 것도 막는다 (SEC-002)
export const MAX_ITERATIONS = 10_000_000
export const SALT_BYTES = 16

/** 서버가 내려준 KDF 값이 규격보다 약하거나 형식이 틀림. 따르지 않고 로그인을 멈춘다 (SEC-002) */
export class InvalidKdfParamsError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'InvalidKdfParamsError'
  }
}

/** 짝이 맞지 않는 서로게이트가 있어 플랫폼마다 UTF-8 변환 결과가 달라질 수 있는 비밀번호 */
export class InvalidPasswordError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'InvalidPasswordError'
  }
}
