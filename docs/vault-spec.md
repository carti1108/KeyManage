# 볼트 암호화 규격과 평문 볼트 스키마

- 적용 범위: 모든 클라이언트(iOS/macOS, Android, Web)
- 관련 정책: SEC-001, SEC-002, VLT-001

> **요약** 클라이언트는 마스터 비밀번호에서 키를 유도해 볼트를 AES-256-GCM으로 암호화한다. 서버에는 암호문(`EncryptedVaultPayload`)만 보낸다. 모든 클라이언트는 같은 파라미터와 같은 평문 스키마(`VaultData`)를 써야 서로의 볼트를 열 수 있다.

> **주의** 이 규격의 값 하나라도 플랫폼마다 다르면, 다른 기기에서 볼트를 열 수 없다. 값을 바꾸려면 ADR이 필요하다.

---

## 1. 암호화 흐름

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

- 서버는 인증 해시만 받는다.
- 인증 해시와 암호화 키는 HKDF의 서로 다른 info로 만든다. 그래서 인증 해시로 암호화 키를 계산할 수 없다 (ADR-0008).

---

## 2. 암호 파라미터

| 파라미터 | 규격 | 비고 |
| :--- | :--- | :--- |
| 비밀번호 전처리 | NFC 정규화 → 앞뒤 공백 제거 → UTF-8 | 아래 '전처리 세부'를 본다 |
| 키 유도 | PBKDF2-HMAC-SHA256 | |
| 계정 salt | 16바이트(128비트) | 가입할 때 클라이언트가 CSPRNG로 한 번 만들어 서버에 등록한다. 다른 기기는 prelogin으로 조회한다 ([api-spec](api-spec.md) 2.5). |
| 반복 횟수 | 600,000회 | 무차별 대입 방어(OWASP 권장값). 클라이언트는 600,000~10,000,000 밖의 값을 거절한다 (SEC-002). |
| 마스터 키 | 32바이트(256비트) | 기기 밖으로 보내지 않는다. |
| 키 분리 | HKDF-SHA256 | salt는 빈 값(RFC 5869에 따라 32바이트 0). info는 `keymanage-enc` / `keymanage-auth`(UTF-8). 출력 32바이트. 암호화 키와 인증 해시를 나눈다. |
| 암호화 | AES-256-GCM | 기밀성과 무결성(AEAD)을 함께 보장한다. |
| IV(nonce) | 12바이트(96비트) | 암호화할 때마다 새로 만든다. |
| 인증 태그 | 16바이트(128비트) | 변조를 막는다. 암호문과 나눠 `tag` 필드에 담는다. |
| AAD | `keymanage-vault\|{version}\|{cipher}` (UTF-8) | 예: `keymanage-vault\|1\|AES-256-GCM`. 페이로드의 평문 헤더를 암호문에 묶는다. 헤더를 바꾸면 복호화가 태그 오류로 실패한다 (ADR-0011). |

### 전처리 세부

1. 앞뒤에서 지우는 문자는 U+0020, U+0009, U+000A, U+000D, U+00A0, U+3000뿐이다. 중간 공백은 유지한다. 플랫폼마다 trim 범위가 달라서 문자를 고정한다.
2. 짝이 맞지 않는 서로게이트(U+D800~U+DFFF 단독)가 있으면, 클라이언트는 키를 유도하지 않고 거절한다. UTF-8 변환 결과가 플랫폼마다 달라지기 때문이다.

### 플랫폼 간 테스트 벡터

- 모든 클라이언트는 [`crypto-test-vectors.json`](crypto-test-vectors.json)의 값을 그대로 재현하는 단위 테스트를 둔다.
- 이 파일은 [`Tools/crypto-vectors`](../Tools/crypto-vectors/README.md)의 생성기로 만든다. 직접 고치지 않는다.
- 생성기 결과는 Python·JDK 독립 구현으로 교차 검증한다.

---

## 3. 암호화 볼트 컨테이너 (`EncryptedVaultPayload`)

서버로 보내거나 기기에 저장하는 암호화된 볼트의 JSON 구조다.

```json
{
  "version": 1,
  "cipher": "AES-256-GCM",
  "iv": "MDEyMzQ1Njc4OWFi",
  "ciphertext": "k7Z9X...base64...",
  "tag": "YWJjZGVmZ2hpamtsbW5vcA=="
}
```

| 필드 | 타입 | 뜻 |
| :--- | :--- | :--- |
| `version` | Int | 스키마 버전. AAD로 인증된다. |
| `cipher` | String | 암호 알고리즘 이름. AAD로 인증된다. |
| `iv` | String | IV. Standard Base64(RFC 4648) |
| `ciphertext` | String | 암호문. Standard Base64(RFC 4648) |
| `tag` | String | 인증 태그. Standard Base64(RFC 4648) |

1. KDF 이름·반복 횟수·salt는 계정 정보로 관리하므로 페이로드에 넣지 않는다. 같은 값이 두 곳에 있으면 어긋날 수 있기 때문이다 (ADR-0008).
2. 평문 헤더는 `version`과 `cipher`뿐이다. 수정 시각은 암호문 안의 볼트 데이터(4장 `updatedAt`)에만 둔다 (ADR-0011).
3. 동기화 충돌 감지용 `revision`은 페이로드 안이 아니라 API 요청·응답 최상위에 둔다 ([api-spec](api-spec.md) 2.3·2.4, ADR-0006).
4. 인증 태그 검증에 실패하면(마스터 키 불일치 등), 클라이언트는 즉시 복호화를 멈추고 사용자에게 마스터 키 오류를 알린다.

---

## 4. 평문 볼트 스키마 (`VaultData`)

복호화에 성공했을 때 메모리에 있는 평문 JSON 구조다.

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

| 필드 | 타입 | 필수 | 뜻 |
| :--- | :--- | :--- | :--- |
| `version` | Int | 예 | 볼트 스키마 버전(기본값 1) |
| `updatedAt` | String | 예 | ISO 8601 UTC 타임스탬프(`YYYY-MM-DDTHH:mm:ssZ`) |
| `items` | Array | 예 | 계정 자격증명 목록 |
| `items[].id` | String | 예 | UUID v4 고유 식별자 |
| `items[].serviceName` | String | 예 | 서비스·사이트 이름(예: "Google", "GitHub") |
| `items[].username` | String | 예 | 로그인 아이디 또는 이메일 |
| `items[].password` | String | 예 | 평문 비밀번호 |
| `items[].url` | String | 아니요 | 로그인 페이지 URL |
| `items[].notes` | String | 아니요 | 사용자 메모 |
| `items[].createdAt` | String | 예 | 등록 일시(ISO 8601) |
| `items[].updatedAt` | String | 예 | 최종 수정 일시(ISO 8601) |
| `items[].deletedAt` | String | 아니요 | 삭제 일시(ISO 8601). 아래 '삭제 표시'를 본다. |

**삭제 표시** (VLT-001, ADR-0006)

1. `deletedAt`에 값이 있으면 삭제된 항목으로 보고 화면에 표시하지 않는다.
2. 기기 간 병합에서 삭제가 되살아나지 않도록, 항목을 목록에서 빼지 않고 이 값을 기록한다.
3. 이때 `id`·`createdAt`·`updatedAt`·`deletedAt`만 남기고 나머지 필드(모르는 필드 포함)는 지운다.

### 병합과 호환 규칙

| 규칙 | 내용 |
| :--- | :--- |
| 항목 병합 | 같은 `id`의 항목이 양쪽에 있으면, 최종 변경 시각이 늦은 쪽을 통째로 고른다. 최종 변경 시각은 `deletedAt`이 있으면 `updatedAt`과 `deletedAt` 중 늦은 값, 없으면 `updatedAt`이다. 시각이 같으면 서버 쪽을 고른다. 한쪽에만 있는 항목은 그대로 둔다 (VLT-001). |
| 모르는 필드 | 볼트와 항목에 이 문서에 없는 필드가 있으면, 지우지 않고 그대로 다시 저장한다. 새 버전 앱이 추가한 데이터를 옛 버전 앱이 지우지 않게 하기 위해서다. |
| 스키마 버전 | `version`이 앱이 아는 값보다 크면, 읽기 전용으로 열고 저장하지 않는다. 1보다 작거나 정수가 아니면, 형식 오류로 보고 열지 않는다. |
| 형식 오류 | 복호화는 됐지만 필수 필드가 없거나 타입이 틀리면, 볼트를 열지 않고 서버의 볼트를 덮어쓰지 않는다. 삭제된 항목은 `id`·`createdAt`·`updatedAt`·`deletedAt`만 필수다. |
| 시각 형식 | 모든 시각은 UTC 표기(`Z` 또는 `+09:00` 같은 오프셋)가 있는 ISO 8601이어야 한다. 표기가 없으면 기기 시간대마다 다르게 해석되므로 형식 오류로 본다. 앱이 쓸 때는 `YYYY-MM-DDTHH:mm:ssZ`를 쓴다. |
| null | 선택 필드(`url`, `notes`, `deletedAt`)의 `null`은 필드가 없는 것으로 본다. |
| 변경 시각은 늘기만 한다 | 항목을 수정·삭제할 때 시각은 현재 시각과 그 항목의 이전 `updatedAt` + 1초 중 큰 값으로 정한다. 같은 초 안에 다시 고치거나 다른 기기 시계가 앞서 있어도, 병합에서 방금 한 변경이 지지 않게 하기 위해서다. |
| 저장 전 확인 | 클라이언트는 저장하기 전에 볼트가 이 규격에 맞는지 다시 확인한다. 규격에 맞지 않는 볼트를 올리면 모든 기기가 열지 못하게 된다. |

---

## 5. 플랫폼 구현

각 플랫폼이 이 규격을 어떤 라이브러리로 구현하는지는 그 플랫폼의 `docs/platform-bindings.md`에 적는다.
