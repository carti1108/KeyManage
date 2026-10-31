// 볼트 상태 안내 문구 (VLT-001)

export const ROLLBACK_WARNING = {
  title: '서버 볼트가 이전 상태로 돌아감',
  body: '서버의 볼트가 이 기기에서 마지막으로 본 상태보다 이전으로 돌아갔습니다. 서버를 복구한 경우가 아니라면 받아들이지 마세요.',
  accept: '서버 볼트 받아들이기',
  cancel: '취소',
} as const

export const OFFLINE_NOTICE = '오프라인 상태입니다. 변경 사항이 서버에 동기화되지 않습니다.'

/** 동기화 대기 중 로그아웃 확인 */
export const PENDING_LOGOUT_CONFIRM = {
  title: '동기화되지 않은 변경 사항',
  body: '로그아웃하면 서버에 올리지 못한 변경 사항이 사라집니다.',
  confirm: '로그아웃',
  cancel: '취소',
} as const

export const READ_ONLY_NOTICE = '새 버전 앱에서 저장한 볼트입니다. 앱을 업데이트하기 전까지는 읽기만 할 수 있습니다.'
