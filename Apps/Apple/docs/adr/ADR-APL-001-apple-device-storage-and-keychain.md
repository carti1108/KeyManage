### ADR-APL-001 Apple 클라이언트 기기 볼트 저장소 및 Keychain 연동
- 상태: 채택
- 날짜: 2026-11-01
- 영역: Apple

> **요약** Apple 클라이언트(iOS·macOS)는 암호화된 볼트와 동기화 메타데이터를 Application Support 샌드박스 파일에 보존하고, 세션 토큰은 Keychain에 안전하게 저장한다.

#### 배경

[VLT-002](../../../../docs/policies/VLT/VLT-002-offline-pending-sync.md)와 [VLT-003](../../../../docs/policies/VLT/VLT-003-device-vault-persistence.md)는 클라이언트가 오프라인 변경 사항을 보존하고 재시작 뒤에도 되돌림 감지 기준과 대기 내용을 유지하도록 요구한다. 웹은 XSS 위험으로 인해 세션 토큰을 로컬에 두지 않고 메모리에만 유지하지만, Apple 플랫폼은 OS 하드웨어 암호화로 보호되는 Keychain과 앱 샌드박스를 제공한다. 네이티브 앱의 재실행 시 사용자 경험과 백그라운드 재동기화를 위해 Apple 플랫폼에 적합한 저장 매체와 보안 경계를 결정해야 한다.

#### 결정

1. **암호화된 볼트 및 메타데이터 파일 저장**:
   - 위치: `Application Support/KeyManage/` 샌드박스 내부 디렉터리.
   - 암호화 볼트: `vault.enc` 바이너리 파일로 저장하며, iOS에서는 `FileProtectionType.completeUntilFirstUserAuthentication` 속성을 지정한다.
   - 동기화 메타데이터: `sync_meta.json`으로 저장하며 계정 식별자(email), 기준 revision, 마지막으로 본 revision, 대기 플래그, KDF 파라미터(salt, iterations)를 보존한다. 민감한 암호화 키는 절대 파일에 쓰지 않는다.
2. **세션 토큰 Keychain 보존**:
   - 세션 토큰은 `kSecClassGenericPassword` 클래스를 사용하여 Apple Keychain에 저장한다.
   - 접근성 속성은 `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`를 적용하여 기기 잠금 해제 후 사용 가능하게 하고 다른 기기 백업으로의 유출을 차단한다.
3. **단일 계정 격리 및 보안 소거**:
   - 기기에는 한 번에 단 한 계정의 기록만 유지한다 (VLT-003 규칙 2).
   - 로그아웃 또는 다른 계정의 인증(로그인·가입) 성공 시, 볼트 파일, 메타데이터 파일 및 Keychain 항목을 즉시 전수 삭제한다.
4. **Clean Architecture 인터페이스 분리**:
   - 도메인 계층(`Core/Domain`)은 `DeviceVaultStoreProtocol` 인터페이스만 정의하여 비즈니스 로직을 파일 시스템 및 보안 프레임워크와 격리한다.
   - 구체적인 파일 및 Keychain 입출력 구현체는 데이터 계층(`KeyManageData`)에 구현한다.

#### 고려한 대안

| 대안 | 기각 이유 |
| :--- | :--- |
| UserDefaults에 볼트 저장 | 크기 제한(수 MB 볼트 페이로드 부적합) 및 암호화/접근 제어 속성 미지원 |
| CoreData / SwiftData 데이터베이스 | 단순 암호화 바이너리 페이로드 및 메타데이터 저장에 과도한 의존성 및 마이그레이션 복잡성 유발 |
| 세션 토큰 로컬 미보존 (웹 방식과 동일화) | 앱을 열 때마다 매번 마스터 비밀번호로 재로그인해야 하므로 네이티브 앱 UX가 심각하게 저하됨 |
| 볼트 전체를 Keychain에 저장 | Keychain은 4KB 미만의 작은 비밀 데이터에 최적화되어 있어 최대 5MB의 볼트 페이로드 저장 시 OS 에러(errSecParam 등) 위험 |

#### 결과

- 얻는 것:
  - VLT-002, VLT-003 정책 100% 충족 (오프라인 대기 및 재시작 후 복원 보장).
  - Apple 샌드박스 및 Keychain 하드웨어 암호화를 활용한 안전한 토큰 관리.
  - 앱 재실행 시 오프라인 상태에서도 로컬 볼트를 즉시 로드하고 잠금 해제 가능.
- 비용:
  - 파일 시스템 I/O 에러 및 Keychain 에러 처리 로직 추가 필요.

#### 관련

- 정책: [VLT-002](../../../../docs/policies/VLT/VLT-002-offline-pending-sync.md), [VLT-003](../../../../docs/policies/VLT/VLT-003-device-vault-persistence.md), [SEC-003](../../../../docs/policies/SEC/SEC-003-unlock-and-auto-lock.md)
- 공통 ADR: [ADR-0001](../../../../docs/adr/ADR-0001-zero-knowledge-security-architecture.md)
- 이슈: #68
