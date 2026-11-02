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
