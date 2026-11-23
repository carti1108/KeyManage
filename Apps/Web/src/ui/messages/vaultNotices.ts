// 볼트 상태 안내 문구 (VLT-001~003)

export const ROLLBACK_WARNING = {
  title: '서버 볼트가 이전 상태로 돌아감',
  body: '서버의 볼트가 이 기기에서 마지막으로 본 상태보다 이전으로 돌아갔습니다. 서버를 복구한 경우가 아니라면 받아들이지 마세요.',
  accept: '서버 볼트 받아들이기',
  cancel: '취소',
} as const

/** 다른 기기에서 마스터 비밀번호를 바꿈 (SEC-003) */
export const PASSWORD_CHANGED = '마스터 비밀번호가 바뀌었습니다. 새 비밀번호로 로그인해 주세요.'

/** 기기 저장 실패 (VLT-003) */
export const DEVICE_STORAGE_FAILED = '이 기기에 볼트를 저장하지 못했습니다. 새로고침하면 서버에 올리지 못한 변경 사항이 사라질 수 있습니다.'

export const READ_ONLY_NOTICE = '새 버전 앱에서 저장한 볼트입니다. 앱을 업데이트하기 전까지는 읽기만 할 수 있습니다.'
