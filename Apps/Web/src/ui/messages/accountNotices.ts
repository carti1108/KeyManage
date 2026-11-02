// 마스터 비밀번호 변경 문구. 정책 문구를 그대로 쓴다 (SEC-004)

/** 변경 전 안내(제목·본문·버튼은 정책 문구)와 입력 칸을 한 대화상자로 보인다. 칸 이름·취소·진행 문구는 기본값 */
export const CHANGE_PASSWORD_DIALOG = {
  title: '마스터 비밀번호 변경',
  body: '비밀번호를 바꾸면 다른 기기는 모두 로그아웃되고, 새 비밀번호로 다시 로그인해야 합니다. 다른 기기에서 아직 올리지 못한 변경 사항은 사라집니다. 예전에 내보낸 백업은 예전 비밀번호로 엽니다.',
  currentLabel: '현재 마스터 비밀번호',
  newLabel: '새 마스터 비밀번호',
  confirmLabel: '새 마스터 비밀번호 확인',
  submit: '변경',
  cancel: '취소',
  progress: '마스터 비밀번호를 바꾸는 중입니다…',
} as const

export const PASSWORD_CHANGE_DONE = '마스터 비밀번호를 바꿨습니다.'

export const SAME_MASTER_PASSWORD = '새 비밀번호가 지금 비밀번호와 같습니다.'

export const PASSWORD_CHANGE_OFFLINE = '인터넷에 연결된 상태에서만 비밀번호를 바꿀 수 있습니다.'

// 계정 삭제 문구. 정책 문구를 그대로 쓴다 (SEC-005)

/** 확인 문구. 앞뒤 공백을 포함해 정확히 같아야 삭제 버튼이 켜진다 (SEC-005 값) */
export const ACCOUNT_DELETION_PHRASE = '계정 삭제'

/** 삭제 전 안내(제목·본문·버튼)와 확인 문구 칸 이름은 정책 문구. 비밀번호 칸 이름·취소·진행 문구는 기본값 */
export const DELETE_ACCOUNT_DIALOG = {
  title: '계정 삭제',
  body: '계정과 볼트가 서버에서 바로 지워지고 되돌릴 수 없습니다. 다른 기기도 모두 로그아웃되고, 아직 올리지 못한 변경 사항은 사라집니다. 필요하면 먼저 백업을 내보내 주세요.',
  passwordLabel: '마스터 비밀번호',
  phraseLabel: "확인을 위해 '계정 삭제'를 입력해 주세요.",
  submit: '삭제',
  cancel: '취소',
  progress: '계정을 삭제하는 중입니다…',
} as const

export const ACCOUNT_DELETED = '계정을 삭제했습니다.'

export const ACCOUNT_DELETION_OFFLINE = '인터넷에 연결된 상태에서만 계정을 삭제할 수 있습니다.'

export const ACCOUNT_DELETED_DEVICE_NOT_CLEARED = '계정은 삭제했지만 이 기기에 남은 볼트 기록을 지우지 못했습니다. 브라우저의 사이트 데이터를 직접 지워 주세요.'
