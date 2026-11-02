// 로그인·가입·비밀번호 변경 화면 안내 문구 (SEC-001, SEC-002)
import type { NewMasterPasswordProblem } from '../../domain/auth/masterPassword'

/** 가입 시 마스터 비밀번호 입력 안내. 잃어버리면 복구할 수 없음을 알린다 (SEC-001) */
export const MASTER_PASSWORD_SETUP = {
  title: '마스터 비밀번호 설정',
  body: '볼트를 열기 위한 마스터 비밀번호를 입력해주세요. 마스터 비밀번호를 잃어버리면 복구할 수 없습니다.',
} as const

/** 새 마스터 비밀번호 규칙 위반 문구. 가입과 비밀번호 변경이 함께 쓴다 (SEC-002) */
export const NEW_PASSWORD_PROBLEMS: Record<NewMasterPasswordProblem, string> = {
  tooShort: '마스터 비밀번호는 12자 이상이어야 합니다.',
  mismatch: '비밀번호 확인이 일치하지 않습니다.',
}
