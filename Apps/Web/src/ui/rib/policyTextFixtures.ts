// RIB 화면 테스트용 정책 문구 모음. 실제 앱에서는 Kotlin State가 이 값을 내려 주고, 화면은 그대로 그린다.
// 화면이 문구를 직접 갖지 않는지 보려면 테스트에서 이 값을 다른 글자로 바꿔 넣고 그 글자가 그려지는지 본다
import type {
  WebChangePasswordDialogText,
  WebConfirmText,
  WebDeleteAccountDialogText,
  WebMessage,
  WebReconnectPrompt,
  WebRestoreDialogText,
  WebSettingsPolicyText,
} from 'keymanage-web-session'

/** Kotlin 데이터 클래스(copy·equals 등이 붙은 타입)를 평범한 객체로 흉내 낸다. 테스트에서 State 일부를 바꿔 넣을 때 쓴다 */
export function asState<T>(value: Omit<T, 'copy' | 'hashCode' | 'equals'>): T {
  return value as unknown as T
}

/** [asState]로 만든 값의 일부 칸만 바꾼 복사본 */
export function withFields<T>(base: T, changes: Partial<Omit<T, 'copy' | 'hashCode' | 'equals'>>): T {
  return { ...base, ...changes } as T
}

export const ROLLBACK_WARNING_TEXT = asState<WebConfirmText>({
  title: '서버 볼트가 이전 상태로 돌아감',
  body: '서버의 볼트가 이 기기에서 마지막으로 본 상태보다 이전으로 돌아갔습니다. 서버를 복구한 경우가 아니라면 받아들이지 마세요.',
  confirm: '서버 볼트 받아들이기',
  cancel: '취소',
})

export const PENDING_LOGOUT_TEXT = asState<WebConfirmText>({
  title: '동기화되지 않은 변경 사항',
  body: '로그아웃하면 서버에 올리지 못한 변경 사항이 사라집니다.',
  confirm: '로그아웃',
  cancel: '취소',
})

export const RECONNECT_PROMPT_TEXT = asState<WebReconnectPrompt>({
  body: '서버와 동기화하려면 마스터 비밀번호를 다시 입력해 주세요.',
  action: '동기화',
})

export const MASTER_PASSWORD_SETUP_TEXT = asState<WebMessage>({
  title: '마스터 비밀번호 설정',
  body: '볼트를 열기 위한 마스터 비밀번호를 입력해주세요. 마스터 비밀번호를 잃어버리면 복구할 수 없습니다.',
})

export const WEAK_MASTER_PASSWORD_TEXT = '마스터 비밀번호가 보안에 취약합니다. 더 길게 만들거나 여러 종류의 문자를 섞어 주세요.'

export const DELETE_CONFIRM_TEXT = asState<WebConfirmText>({
  title: '항목 삭제',
  body: '이 항목을 삭제할까요? 다른 기기에서도 삭제됩니다.',
  confirm: '삭제',
  cancel: '취소',
})

export const WEAK_ITEM_PASSWORD_TEXT = asState<WebMessage>({
  title: '취약한 비밀번호 경고',
  body: '현재 비밀번호는 보안에 취약합니다. 80점 이상의 강력한 비밀번호로 재생성하세요.',
})

export const SEQUENTIAL_DIGITS_TEXT = '123처럼 이어지는 숫자가 들어 있어 추측하기 쉽습니다.'

export const SETTINGS_SECURITY_POLICY_TEXT = asState<WebSettingsPolicyText>({
  clipboardLabel: '클립보드 자동 삭제',
  clipboardValue: '30초 (고정, COM-001)',
  encryptionLabel: '암호화 구조',
  encryptionValue: '클라이언트 영지식 (AES-256-GCM)',
})

export const EXPORT_NOTICE_TEXT = asState<WebConfirmText>({
  title: '백업 내보내기',
  body: '이 파일은 내보낸 시점의 마스터 비밀번호로만 열 수 있습니다. 비밀번호를 바꾼 뒤에도 이 파일은 예전 비밀번호로 엽니다.',
  confirm: '내보내기',
  cancel: '취소',
})

export const RESTORE_DIALOG_TEXT = asState<WebRestoreDialogText>({
  title: '백업에서 복원',
  body: '백업에 있던 항목은 백업 시점 내용으로 돌아갑니다. 백업 뒤에 고치거나 지운 내용은 되돌아가고, 백업 뒤에 새로 만든 항목은 그대로 남습니다.',
  passwordLabel: '백업 파일 비밀번호',
  submit: '복원',
  cancel: '취소',
  progress: '백업을 복원하는 중입니다…',
})

export const CHANGE_PASSWORD_DIALOG_TEXT = asState<WebChangePasswordDialogText>({
  title: '마스터 비밀번호 변경',
  body: '비밀번호를 바꾸면 다른 기기는 모두 로그아웃되고, 새 비밀번호로 다시 로그인해야 합니다. 다른 기기에서 아직 올리지 못한 변경 사항은 사라집니다. 예전에 내보낸 백업은 예전 비밀번호로 엽니다.',
  currentLabel: '현재 마스터 비밀번호',
  newLabel: '새 마스터 비밀번호',
  confirmLabel: '새 마스터 비밀번호 확인',
  newPasswordRule: '마스터 비밀번호는 12자 이상이어야 합니다.',
  submit: '변경',
  cancel: '취소',
  progress: '마스터 비밀번호를 바꾸는 중입니다…',
})

export const DELETE_ACCOUNT_DIALOG_TEXT = asState<WebDeleteAccountDialogText>({
  title: '계정 삭제',
  body: '계정과 볼트가 서버에서 바로 지워지고 되돌릴 수 없습니다. 다른 기기도 모두 로그아웃되고, 아직 올리지 못한 변경 사항은 사라집니다. 필요하면 먼저 백업을 내보내 주세요.',
  passwordLabel: '마스터 비밀번호',
  phraseLabel: "확인을 위해 '계정 삭제'를 입력해 주세요.",
  submit: '삭제',
  cancel: '취소',
  progress: '계정을 삭제하는 중입니다…',
})
