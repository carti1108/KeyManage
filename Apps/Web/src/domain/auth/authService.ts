// 인증 흐름(가입·로그인·로그아웃·비밀번호 변경·계정 삭제)의 구현은 공유 Kotlin(Apps/Shared/domain의 AuthService)으로 옮겼다.
// 웹에는 화면이 오류 종류와 KDF 값 타입을 구분하는 데 쓰는 정의만 남긴다 (SEC-002, SEC-004, SEC-005, ADR-0008)

export class InvalidEmailError extends Error {
  constructor() {
    super('이메일 형식이 올바르지 않음')
    this.name = 'InvalidEmailError'
  }
}

/** 기기에 저장한 KDF 값이 서버와 다르거나 로그인이 인증 실패로 거절됨. 다른 기기에서 마스터 비밀번호를 바꾼 것으로 본다 (SEC-003) */
export class MasterPasswordChangedError extends Error {
  constructor() {
    super('마스터 비밀번호가 바뀜')
    this.name = 'MasterPasswordChangedError'
  }
}

/** 잠금 해제 때 서버 없이 같은 키를 다시 만들기 위한 값. 비밀이 아니다 (SEC-003) */
export interface UnlockKdf {
  /** Standard Base64, 16바이트 */
  readonly salt: string
  readonly iterations: number
}
