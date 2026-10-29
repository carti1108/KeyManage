# [Contract] 백엔드 REST API 계약 명세서 (API Spec)

이 문서는 `KeyManage` 백엔드(`Back/`)와 모든 플랫폼 클라이언트(iOS, Android, Web) 간의 통신 규격을 정의한 인터페이스 계약(Contract)입니다.

---

## 1. 기본 규격 (Common Convention)

* **Base URL**: `http://localhost:8080/api/v1` (로컬) 또는 `https://<domain>/api/v1` (배포)
* **전송 보안**: 클라이언트는 `localhost`·`127.0.0.1`이 아닌 서버에는 HTTPS로만 접속한다. 인증 해시와 세션 토큰이 오가기 때문이다 (SEC-002).
* **Content-Type**: `application/json; charset=UTF-8`
* **인증**: 2.5의 prelogin·가입·로그인을 제외한 모든 요청은 `Authorization: Bearer <accessToken>` 헤더가 필요하다 (SEC-002).
* **표준 에러 응답 형식 (Standard Error Response)**:
  모든 4xx, 5xx 응답은 아래 포맷을 따릅니다:
  ```json
  {
    "code": "INVALID_PARAMETER",
    "message": "요청 형식이 올바르지 않습니다.",
    "timestamp": "2026-10-27T11:20:00Z"
  }
  ```
* **공통 오류 코드**: 엔드포인트별 코드(`VAULT_CONFLICT` 등)는 각 절에 적는다.
  * `400 INVALID_PARAMETER` / "요청 형식이 올바르지 않습니다.": 필수 필드·헤더가 없거나, 값 형식이 틀리거나, JSON이 깨진 경우.
  * `401 UNAUTHORIZED` / "로그인이 만료되었습니다. 다시 로그인해 주세요.": 토큰이 없거나 만료·폐기된 경우. 클라이언트는 로그인 화면으로 보낸다.
  * `429 TOO_MANY_ATTEMPTS` / "로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요.": 시도 제한 초과. `Retry-After` 헤더(초)를 함께 보낸다.
  * `500 INTERNAL_ERROR` / "일시적인 서버 오류입니다. 잠시 후 다시 시도해 주세요.": 예상하지 못한 오류. 상세 내용은 서버 로그에만 남기며 페이로드·인증 해시·토큰은 로그에 남기지 않는다.
* **요청 본문 상한**: 6MB(6,291,456바이트, 볼트 페이로드 5MB + 여유). 넘으면 본문을 해석하기 전에 `413 VAULT_TOO_LARGE`로 거절한다.

---

## 2. API 엔드포인트 상세

### 2.1. 무작위 비밀번호 생성 · 2.2. 비밀번호 보안 진단 (제거됨)
* 제거됨 ([ADR-0005](adr/ADR-0005-client-only-password-generation-and-audit.md)): 비밀번호 생성과 진단은 평문 비밀번호가 서버로 가지 않도록 각 클라이언트에서 수행하며, 규칙은 [GEN-001](policies/GEN/GEN-001-password-generation-and-entropy-scoring.md)을 따른다.
* 섹션 번호는 기존 참조가 깨지지 않도록 비워 둔다.

---

### 2.3. 암호화 볼트 조회 (Get Vault)
* **URL**: `GET /api/v1/vault`
* **설명**: 로그인한 계정의 암호화된 볼트를 가져옵니다. 계정당 볼트는 1개이며, 볼트는 토큰의 계정으로 결정됩니다 (ADR-0008).

#### Request Headers
* `Authorization: Bearer <accessToken>`

#### Response (200 OK)
```json
{
  "revision": 7,
  "payload": {
    "version": 1,
    "cipher": "AES-256-GCM",
    "iv": "MDEyMzQ1Njc4OWFi",
    "ciphertext": "k7Z9X...base64...",
    "tag": "YWJjZGVmZ2hpamtsbW5vcA=="
  }
}
```
* `revision` (Int): 서버가 발급한 볼트 revision. 다음 저장 요청의 `expectedRevision`으로 그대로 보낸다 (VLT-001, ADR-0006).
* `payload`는 저장 요청에서 받은 JSON 객체를 서버가 해석하지 않고 그대로 돌려준다.

#### Response (404 Not Found)
계정은 있지만 아직 저장된 볼트가 없을 때.
```json
{
  "code": "VAULT_NOT_FOUND",
  "message": "저장된 볼트가 없습니다.",
  "timestamp": "2026-10-27T11:20:00Z"
}
```

---

### 2.4. 암호화 볼트 저장 및 동기화 (Save Vault)
* **URL**: `POST /api/v1/vault`
* **설명**: 클라이언트에서 암호화한 볼트를 로그인한 계정의 볼트로 저장합니다.

#### Request Headers
* `Authorization: Bearer <accessToken>`

#### Request Body
```json
{
  "expectedRevision": 7,
  "payload": {
    "version": 1,
    "cipher": "AES-256-GCM",
    "iv": "MDEyMzQ1Njc4OWFi",
    "ciphertext": "k7Z9X...base64...",
    "tag": "YWJjZGVmZ2hpamtsbW5vcA=="
  }
}
```

* `expectedRevision` (Int): 클라이언트가 마지막으로 받은 revision. 볼트를 처음 저장할 때는 `0`.
* 요청 페이로드 JSON이 5,242,880바이트(5MB)를 넘으면 저장하지 않는다 (VLT-001).

#### Response (200 OK)
```json
{
  "revision": 8
}
```
* 서버는 저장 시각을 관리하지 않는다. 동기화 판단은 `revision`으로만 한다 (ADR-0006).

#### Response (409 Conflict)
`expectedRevision`이 서버의 현재 revision과 다를 때. 클라이언트는 최신 볼트를 조회해 항목별로 병합한 뒤 다시 저장한다 (VLT-001, ADR-0006).
```json
{
  "code": "VAULT_CONFLICT",
  "message": "다른 기기에서 볼트가 먼저 변경되었습니다.",
  "currentRevision": 9,
  "timestamp": "2026-10-27T11:20:00Z"
}
```

#### Response (413 Payload Too Large)
```json
{
  "code": "VAULT_TOO_LARGE",
  "message": "볼트 허용 용량(5MB)을 초과했습니다.",
  "timestamp": "2026-10-27T11:20:00Z"
}
```

---

### 2.5. 계정 인증 (Auth)
키 유도와 인증 해시 계산은 [vault-spec](vault-spec.md) 1·2장을 따른다. 서버는 마스터 비밀번호와 암호화 키를 받지 않는다 (SEC-002, ADR-0008).

* `email`: 앞뒤 공백을 지우고 소문자로 맞춘 뒤 비교한다. 최대 254자.
* `authHash`, `salt`, `kdfSalt`: Standard Base64 (RFC 4648). `authHash`는 32바이트, salt는 16바이트.

#### 2.5.1. 로그인 전 키 유도 정보 조회 (Prelogin)
* **URL**: `POST /api/v1/auth/prelogin` (토큰 불필요)
* **Request**: `{ "email": "user@example.com" }`
* **Response (200 OK)**:
  ```json
  { "kdf": "PBKDF2-HMAC-SHA256", "iterations": 600000, "salt": "dGhpcyBpcyBhIHNhbHQxNg==" }
  ```
* 가입되지 않은 이메일에도 200으로 응답하며, 서버 비밀키로 만든 가짜 salt를 같은 이메일에 항상 같은 값으로 돌려준다. 응답으로 계정 존재 여부를 알 수 없다.

#### 2.5.2. 가입 (Register)
* **URL**: `POST /api/v1/auth/register` (토큰 불필요)
* **Request**:
  ```json
  { "email": "user@example.com", "authHash": "<32바이트 Base64>", "kdfSalt": "<16바이트 Base64>", "kdfIterations": 600000 }
  ```
* **Response (201 Created)**: `{ "accessToken": "<토큰>", "expiresAt": "2026-11-27T11:20:00Z" }`
* `kdfIterations`는 600,000 이상이어야 한다.
* `409 EMAIL_ALREADY_REGISTERED` / "이미 가입된 이메일입니다.": 이미 가입된 이메일.
* `403 REGISTRATION_DISABLED` / "현재 새 계정을 만들 수 없습니다.": 서버 설정으로 가입이 꺼져 있을 때.

#### 2.5.3. 로그인 (Login)
* **URL**: `POST /api/v1/auth/login` (토큰 불필요)
* **Request**: `{ "email": "user@example.com", "authHash": "<32바이트 Base64>" }`
* **Response (200 OK)**: `{ "accessToken": "<토큰>", "expiresAt": "2026-11-27T11:20:00Z" }`
* `401 AUTH_FAILED` / "이메일 또는 마스터 비밀번호가 올바르지 않습니다.": 이메일이 없거나 인증 해시가 틀린 경우. 두 경우를 응답 내용으로 구분하지 않는다.
* `429 TOO_MANY_ATTEMPTS`: 계정 또는 IP 시도 제한 초과.

#### 2.5.4. 로그아웃 (Logout)
* **URL**: `POST /api/v1/auth/logout`
* **Request Headers**: `Authorization: Bearer <accessToken>`
* **Response (204 No Content)**: 이 토큰만 폐기한다. 이미 폐기된 토큰으로 다시 요청해도 204로 응답한다.
