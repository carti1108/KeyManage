### ADR-0027 생체 해제는 마스터 비밀번호가 아니라 유도한 암호화 키를 저장한다
- 상태: 채택
- 날짜: 2026-11-14
- 영역: 공통

> **요약** 생체 해제를 켜면 비밀번호로 연 직후 공유 세션이 볼트 암호화 키 사본(32바이트)을 한 번 내보내고, iOS는 그 키를 이 기기 전용·생체 보호 키체인 항목에 둔다. 생체 해제는 그 키로 볼트를 바로 연다. 마스터 비밀번호는 어디에도 저장하지 않는다.

#### 배경

[SEC-003](../policies/SEC/SEC-003-unlock-and-auto-lock.md)은 생체 해제용 키를 이 기기 전용 저장소에 두고 생체 인증으로 보호하라고 정한다. 옛 Swift 구현(`VaultLockManager.unlockWithBiometrics(recoveredEncryptionKey:)`)은 볼트 암호화 키를 저장하고 그 키로 복호화했다.

[KMP 도메인 이전](../research/kmp-migration/domain-migration-plan.md) 3단계에서 공유 세션(`VaultSession`)에는 비밀번호로 여는 길만 있어, 처음 구현은 iOS 키체인에 마스터 비밀번호를 넣었다. 마스터 비밀번호는 모든 키의 근원이라, 키체인 항목이 새면 다른 기기의 볼트와 서버 로그인(인증 해시)까지 열린다. 사용자가 옛 방식으로 되돌리기로 정했다.

#### 결정

1. `VaultSession.unlock(password, acceptRollback, exportUnlockKey)`에 `exportUnlockKey = true`를 넘기면, 비밀번호로 연 직후 암호화 키 사본을 `UnlockResult.unlockKey`로 돌려준다. 기본값은 `false`라 웹은 바뀌지 않는다. 사본은 메모리에서만 키체인에 넘기고, 넘긴 뒤 부른 쪽이 0으로 지운다.
2. `VaultSession.unlockWithKey(unlockKey, acceptRollback)`는 그 키로 기기에 저장한 암호화 볼트를 복호화해 연다. 키는 복사해 쓴다. 열지 못하면 `StaleUnlockKeyException`을 던지고 잠긴 채 둔다.
3. 키에는 인증 해시가 없으므로, 세션 토큰이 없을 때(재시작 뒤 기기 기록으로 다시 연 경우) 로그인하지 않고 세션 없이 연다. 서버와 맞추려면 비밀번호로 재연결한다([ADR-0019](../../Apps/Web/docs/adr/ADR-0019-web-device-storage-single-account.md)). 토큰이 있으면 비밀번호 해제와 같이 서버와 맞춘다.
4. 저장한 키는 다음 때 지운다.
   - 마스터 비밀번호 변경 성공, 계정 삭제, 로그아웃, 그리고 이 작업 중 세션이 로그인 전 상태로 바뀐 경우(`BiometricInvalidatingSession`)
   - 생체 정보가 바뀌어 키체인이 항목을 내주지 않을 때(`kSecAccessControlBiometryCurrentSet`, SEC-003 8번)
   - 키로 볼트를 열 수 없을 때(다른 기기에서 비밀번호를 바꾼 경우)
5. 마지막 비밀번호 해제로부터 14일이 지나면 생체 해제를 막고 비밀번호를 받는 규칙은 그대로다. 비밀번호로 열 때만 키와 기준 시각을 새로 한다.
6. 키 유도(PBKDF2-SHA256, HKDF 분리), 볼트 암호 형식, 서버 API는 바꾸지 않는다. 키는 서버로 보내지 않는다.

#### 고려한 대안

| 대안 | 기각 이유 |
| :--- | :--- |
| 마스터 비밀번호를 생체 보호 키체인에 저장(3단계 처음 구현) | 항목이 새면 모든 키와 서버 인증 해시를 다시 만들 수 있다. 저장하는 비밀의 범위가 이 기기 볼트 복호화보다 넓다 |
| 마스터 키(HKDF 전)를 저장 | 마스터 키로 인증 해시도 만들 수 있어 서버 로그인이 가능해진다. 암호화 키만으로 생체 해제에 충분하다 |
| 키를 저장하지 않고 Secure Enclave 키로 암호화 키를 감싸 저장 | 보호 수준은 비슷하지만 키 감싸기·풀기 코드가 더 들고, 이미 `kSecAccessControlBiometryCurrentSet` 키체인 항목이 같은 조건(이 기기, 현재 생체)을 준다 |

#### 결과

- 얻는 것: 키체인 항목이 새어도 이 기기에 저장한 볼트만 열 수 있고, 서버 로그인·다른 볼트·백업 비밀번호는 알 수 없다. 옛 Swift 동작과 같다.
- 비용: 재시작 뒤 생체로 열면 세션 없이 열려 동기화하려면 비밀번호를 한 번 입력해야 한다. 공유 세션에 키를 밖으로 내보내는 API가 생겨, 이 API를 생체 해제 외에 쓰지 않도록 리뷰에서 지킨다.

#### 관련

[SEC-003](../policies/SEC/SEC-003-unlock-and-auto-lock.md), [ADR-0008](ADR-0008-auth-hash-and-server-session.md)(인증 해시와 서버 세션), [ADR-0019](../../Apps/Web/docs/adr/ADR-0019-web-device-storage-single-account.md)(기기 볼트 기록), [ADR-0023](ADR-0023-master-password-change.md)(마스터 비밀번호 변경), [KMP 도메인 이전 계획](../research/kmp-migration/domain-migration-plan.md) 12.5
