// 암호화 백업 내보내기·복원 문구. 정책 문구를 그대로 쓴다 (VLT-004)

/** 비밀번호가 틀렸거나 파일이 손상됨. AES-GCM은 둘을 구분하지 못한다 */
export const BACKUP_OPEN_FAILED = '백업을 열 수 없습니다. 비밀번호가 맞는지, 파일이 손상되지 않았는지 확인해 주세요.'

/** 형식이 아니거나 5MB 초과 */
export const NOT_A_BACKUP = 'KeyManage 백업 파일이 아닙니다.'

export const NEWER_BACKUP = '더 새 버전의 앱에서 만든 백업입니다. 앱을 업데이트한 뒤 복원해 주세요.'
