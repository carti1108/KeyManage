// 가입할 때 새 마스터 비밀번호 검사(SEC-002)는 공유 Kotlin이 한다. 웹에는 화면이 문구를 고르는 데 쓰는 문제 종류 타입만 남긴다

export type NewMasterPasswordProblem = 'tooShort' | 'mismatch'
