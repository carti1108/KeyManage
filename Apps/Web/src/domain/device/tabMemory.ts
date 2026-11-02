// 이 탭이 마지막으로 연 계정의 KDF 값을 탭에만 기억하는 포트 (SEC-003 규칙 11, ADR-WEB-001)
// 기기 기록은 탭들이 함께 쓰므로, 다른 탭이 비밀번호를 바꾸면 새 KDF 값으로 덮인다. 이 탭이 새로 열려도(새로고침·탭 폐기)
// 남는 기억과 비교해 비밀번호가 바뀐 것을 안다. 비밀이 아닌 값(이메일·salt·반복 횟수)만 둔다. 키·비밀번호·인증 해시는 두지 않는다
import type { UnlockKdf } from '../auth/authService'

export interface RememberedKdf {
  readonly email: string
  /** Standard Base64, 16바이트 */
  readonly salt: string
  readonly iterations: number
}

/** 구현은 저장소 오류를 밖으로 내지 않는다. 읽지 못하면 기억이 없는 것(null)으로 본다 */
export interface TabMemory {
  get(): RememberedKdf | null
  set(value: RememberedKdf): void
  clear(): void
}

export function rememberedKdf(email: string, kdf: UnlockKdf): RememberedKdf {
  return { email, salt: kdf.salt, iterations: kdf.iterations }
}
