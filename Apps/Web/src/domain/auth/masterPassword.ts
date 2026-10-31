// 가입할 때 새 마스터 비밀번호 검사 (SEC-002). 서버는 비밀번호를 볼 수 없으므로 클라이언트가 막는다

export const MIN_MASTER_PASSWORD_LENGTH = 12

export type NewMasterPasswordProblem = 'tooShort' | 'mismatch'

/** @returns 문제가 없으면 null. 길이 부족을 불일치보다 먼저 알린다 */
export function checkNewMasterPassword(password: string, confirmation: string): NewMasterPasswordProblem | null {
  // 이모지처럼 UTF-16 두 단위인 문자도 한 글자로 센다. 앞뒤 공백도 비밀번호의 일부다
  if ([...password].length < MIN_MASTER_PASSWORD_LENGTH) return 'tooShort'
  if (password !== confirmation) return 'mismatch'
  return null
}
