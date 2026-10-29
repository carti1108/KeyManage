# [Spec] 볼트 암호화 규격 및 내부 데이터 스키마 (Vault Spec)

이 문서는 모든 클라이언트(iOS/macOS, Android, Web)가 마스터 키로 볼트를 암호화·복호화할 때 지켜야 하는 암호학적 규격과, 복호화된 평문 데이터의 표준 JSON 구조를 정의합니다. 플랫폼 간 상호 운용성을 위해 모든 구현체는 이 명세를 엄격히 준수해야 합니다.

---

## 1. 암호화 아키텍처 개요 (Zero-Knowledge)

```
[ Master Password (Plaintext) ] + [ Account Salt (16 Bytes, prelogin으로 조회) ]
                       │
                       ▼  PBKDF2-HMAC-SHA256 (600,000 Iterations)
             [ Master Key (256-bit) ]  ── 기기 밖으로 나가지 않음
                       │
          ┌────────────┴─────────────┐
          ▼ HKDF "keymanage-enc"     ▼ HKDF "keymanage-auth"
 [ Encryption Key (256-bit) ]   [ Auth Hash (256-bit) ] ──► HTTPS ──► 서버 로그인 (SEC-002)
          │
          ▼  AES-256-GCM (IV 12 Bytes)
 [ Vault Plaintext JSON ] ───► [ Ciphertext ] + [ Auth Tag (16 Bytes) ]
          │
          ▼
 [ EncryptedVaultPayload ] ──► HTTPS ──► [ Backend Storage ]
```

* 서버는 인증 해시만 받는다. 인증 해시와 암호화 키는 HKDF의 서로 다른 info로 만들므로, 인증 해시로 암호화 키를 계산할 수 없다 (ADR-0008).

---

## 2. 암호학적 파라미터 규격

| 파라미터 | 규격 | 비고 |
| :--- | :--- | :--- |
| **Password Preprocessing** | NFC 정규화 → 앞뒤 공백 제거 → UTF-8 | 제거 대상은 U+0020, U+0009, U+000A, U+000D, U+00A0, U+3000만이며 중간 공백은 유지. 플랫폼마다 trim 범위가 달라 문자를 고정한다. 짝이 맞지 않는 서로게이트(U+D800~U+DFFF 단독)가 있으면 UTF-8 변환이 플랫폼마다 달라지므로 키를 유도하지 않고 거절한다 |
| **Key Derivation** | PBKDF2-HMAC-SHA256 | 모던 표준 KDF |
| **Account Salt** | 16 바이트 (128비트) | 가입 시 클라이언트가 CSPRNG로 1회 생성해 서버에 등록. 다른 기기는 prelogin으로 조회 ([api-spec](api-spec.md) 2.5) |
| **Iteration Count** | 600,000 회 | 무차별 대입 공격(Brute-force) 방어 (OWASP 권장값). 클라이언트는 600,000~10,000,000 밖의 값을 거절한다 (SEC-002) |
| **Master Key Size** | 32 바이트 (256비트) | 기기 밖으로 보내지 않음 |
| **Key Separation** | HKDF-SHA256 (salt 빈 값 = RFC 5869에 따라 32바이트 0, info `keymanage-enc` / `keymanage-auth` UTF-8, 출력 32바이트) | 암호화 키와 인증 해시 분리 |
| **Cipher Algorithm** | AES-256-GCM | 기밀성 및 무결성(AEAD) 동시 보장 |
| **IV / Nonce** | 12 바이트 (96비트) | 암호화할 때마다 매번 새로 생성 |
| **Auth Tag** | 16 바이트 (128비트) | 변조 방지 인증 태그. 암호문과 나눠 `tag` 필드에 담는다 |
| **AAD** | `keymanage-vault\|{version}\|{cipher}` (UTF-8, 예: `keymanage-vault\|1\|AES-256-GCM`) | 페이로드의 평문 헤더를 암호문에 묶어, 헤더를 바꾸면 복호화에서 태그 오류로 거절된다 (ADR-0011) |

**플랫폼 간 테스트 벡터:** 모든 클라이언트는 [`crypto-test-vectors.json`](crypto-test-vectors.json)의 값을 그대로 재현하는 단위 테스트를 둔다. 이 파일은 [`Tools/crypto-vectors`](../Tools/crypto-vectors/README.md)의 생성기로 만들고(직접 고치지 않는다), Python·JDK 독립 구현으로 교차 검증한다.

---

## 3. 암호화 볼트 전송/저장 컨테이너 규격 (`EncryptedVaultPayload`)

백엔드로 전송되거나 로컬에 영속화되는 암호화 덩어리는 아래의 표준 JSON 구조를 갖습니다.

```json
{
  "version": 1,
  "cipher": "AES-256-GCM",
  "iv": "MDEyMzQ1Njc4OWFi",
  "ciphertext": "k7Z9X...base64...",
  "tag": "YWJjZGVmZ2hpamtsbW5vcA=="
}
```

* 모든 바이너리 필드(`iv`, `ciphertext`, `tag`)는 **Standard Base64 (RFC 4648)** 로 인코딩됩니다.
* KDF 이름·반복 횟수·salt는 계정 정보로 관리하므로 페이로드에 넣지 않는다. 같은 값이 두 곳에 있으면 어긋날 수 있기 때문이다 (ADR-0008).
* 평문 헤더는 `version`과 `cipher`뿐이며 둘 다 AAD로 인증된다. 수정 시각은 암호문 안의 볼트 데이터(4장 `updatedAt`)에만 둔다 (ADR-0011).
* `version`은 스키마 버전이다. 동기화 충돌 감지용 `revision`은 페이로드 안이 아니라 API 응답·요청 최상위에 둔다 ([api-spec](api-spec.md) 2.3·2.4, ADR-0006).
* 마스터 키 불일치 등으로 인증 태그(`tag`) 검증에 실패할 경우, 플랫폼 클라이언트는 즉시 복호화를 중단하고 사용자에게 마스터 키 오류를 알립니다.

---

## 4. 복호화된 평문 볼트 스키마 (`VaultData`)

복호화에 성공했을 때 메모리 상에 존재하는 평문 JSON 구조입니다.

```json
{
  "version": 1,
  "updatedAt": "2026-10-27T11:20:00Z",
  "items": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "serviceName": "GitHub",
      "username": "octocat@github.com",
      "password": "SuperSecurePassword123!",
      "url": "https://github.com/login",
      "notes": "개인 개발용 서브 계정",
      "createdAt": "2026-10-27T10:00:00Z",
      "updatedAt": "2026-10-27T11:20:00Z"
    }
  ]
}
```

### 필드 정의
* `version` (Int): 볼트 스키마 버전 (기본값: 1).
* `updatedAt` (String): ISO 8601 UTC 타임스탬프 (`YYYY-MM-DDTHH:mm:ssZ`).
* `items` (Array): 계정 자격증명 목록.
  * `id` (String): UUID v4 고유 식별자.
  * `serviceName` (String): 서비스/사이트명 (예: "Google", "GitHub").
  * `username` (String): 로그인 아이디 또는 이메일.
  * `password` (String): 평문 비밀번호.
  * `url` (String, Optional): 로그인 페이지 URL.
  * `notes` (String, Optional): 사용자 메모.
  * `createdAt` (String): 등록 일시 (ISO 8601).
  * `updatedAt` (String): 최종 수정 일시 (ISO 8601).
  * `deletedAt` (String, Optional): 삭제 일시 (ISO 8601). 값이 있으면 삭제된 항목으로 보고 화면에 표시하지 않는다. 기기 간 병합 시 삭제가 되살아나지 않도록 항목을 목록에서 빼지 않고 이 값을 기록한다. 이때 `id`·`createdAt`·`updatedAt`·`deletedAt`만 남기고 나머지 필드(모르는 필드 포함)는 지운다 (VLT-001, ADR-0006).

### 병합과 호환 규칙
* **항목 병합**: 같은 `id`의 항목이 양쪽에 있으면 최종 변경 시각(`deletedAt`이 있으면 `updatedAt`과 `deletedAt` 중 늦은 값, 없으면 `updatedAt`)이 늦은 쪽을 통째로 고른다. 시각이 같으면 서버 쪽을 고른다. 한쪽에만 있는 항목은 그대로 둔다 (VLT-001).
* **모르는 필드**: 볼트와 항목에 이 문서에 없는 필드가 있으면 지우지 않고 그대로 다시 저장한다. 새 버전 앱이 추가한 데이터를 옛 버전 앱이 지우지 않게 하기 위해서다.
* **스키마 버전**: `version`이 앱이 아는 값보다 크면 읽기 전용으로 열고 저장하지 않는다. 1보다 작거나 정수가 아니면 형식 오류로 보고 열지 않는다.
* **형식 오류**: 복호화는 됐지만 필수 필드가 없거나 타입이 틀리면 볼트를 열지 않고, 서버의 볼트를 덮어쓰지 않는다. 삭제된 항목은 `id`·`createdAt`·`updatedAt`·`deletedAt`만 필수다.
* **시각 형식**: 모든 시각은 UTC 표기(`Z` 또는 `+09:00` 같은 오프셋)가 있는 ISO 8601이어야 한다. 표기가 없으면 기기 시간대마다 다르게 해석되므로 형식 오류로 본다. 앱이 쓸 때는 `YYYY-MM-DDTHH:mm:ssZ`를 쓴다.
* **null**: 선택 필드(`url`, `notes`, `deletedAt`)의 `null`은 필드가 없는 것으로 본다.
* **변경 시각은 늘기만 한다**: 항목을 수정·삭제할 때 시각은 현재 시각과 그 항목의 이전 `updatedAt` + 1초 중 큰 값으로 정한다. 같은 초 안에 다시 고치거나 다른 기기 시계가 앞서 있어도 병합에서 방금 한 변경이 지지 않게 하기 위해서다.
* **저장 전 확인**: 클라이언트는 저장하기 전에 볼트가 이 규격에 맞는지 다시 확인한다. 규격에 맞지 않는 볼트를 올리면 모든 기기가 열지 못하게 된다.

---

## 5. 플랫폼별 표준 암호화 라이브러리 매핑

| 플랫폼 | 언어 / 프레임워크 | PBKDF2 라이브러리 | AES-256-GCM 라이브러리 |
| :--- | :--- | :--- | :--- |
| **iOS / macOS** | Swift | `CommonCrypto` (`CCKeyDerivationPBKDF`) | `CryptoKit.AES.GCM` |
| **Android** | Kotlin | `SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")` | `Cipher.getInstance("AES/GCM/NoPadding")` |
| **Web** | TypeScript / JS | Web Crypto API (`subtle.deriveKey`) | Web Crypto API (`subtle.encrypt({ name: "AES-GCM" })`) |
