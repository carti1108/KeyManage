# 백엔드 REST API 계약

- 적용 범위: 백엔드(`Back/`)와 모든 클라이언트(iOS/macOS, Android, Web)
- 관련 정책: SEC-002, VLT-001

> **요약** 클라이언트는 인증 해시로 로그인해 세션 토큰을 받고, 그 토큰으로 계정 볼트 하나를 조회·저장한다. 볼트는 암호문 그대로 오가며, 저장은 `revision`으로 충돌을 감지한다.

| 엔드포인트 | 메서드 | 토큰 | 절 |
| :--- | :--- | :--- | :--- |
| `/api/v1/vault` | GET | 필요 | 2.3 볼트 조회 |
| `/api/v1/vault` | POST | 필요 | 2.4 볼트 저장 |
| `/api/v1/auth/prelogin` | POST | 불필요 | 2.5.1 키 유도 정보 조회 |
| `/api/v1/auth/register` | POST | 불필요 | 2.5.2 가입 |
| `/api/v1/auth/login` | POST | 불필요 | 2.5.3 로그인 |
| `/api/v1/auth/logout` | POST | 필요 | 2.5.4 로그아웃 |

---

## 1. 공통 규격

| 항목 | 규격 |
| :--- | :--- |
| Base URL | `http://localhost:8080/api/v1` (로컬) 또는 `https://<domain>/api/v1` (배포) |
| 전송 보안 | 클라이언트는 `localhost`·`127.0.0.1`이 아닌 서버에는 HTTPS로만 접속한다. 인증 해시와 세션 토큰이 오가기 때문이다 (SEC-002). |
| Content-Type | `application/json; charset=UTF-8` |
| 인증 | 2.5의 prelogin·가입·로그인을 뺀 모든 요청에 `Authorization: Bearer <accessToken>` 헤더가 필요하다 (SEC-002). |
| 요청 본문 상한 | 6MB(6,291,456바이트, 볼트 페이로드 5MB + 여유). 넘으면 본문을 해석하기 전에 `413 VAULT_TOO_LARGE`로 거절한다. |

### 교차 출처(CORS)

1. 서버는 설정 `keymanage.http.allowed-origins`에 적은 출처(데스크톱 앱)만 허용한다. 기본값은 비어 있어 모두 거절한다.
2. 허용 메서드는 `GET`·`POST`, 요청 헤더는 `Authorization`·`Content-Type`이다. `Retry-After`를 노출한다.
3. 허용하지 않은 출처의 요청은 `403 ORIGIN_NOT_ALLOWED` / "허용되지 않은 출처의 요청입니다."로 거절한다.
4. 서버는 브라우저가 보낸 `Origin`을 자신이 받은 요청 주소와 비교한다. 그래서 TLS를 끝내는 리버스 프록시 뒤에서 웹을 같은 출처로 서비스하면, 그 웹 출처도 허용 목록에 넣는다 (ADR-0013).

### 오류 응답 형식

모든 4xx, 5xx 응답은 아래 형식을 따른다.

```json
{
  "code": "INVALID_PARAMETER",
  "message": "요청 형식이 올바르지 않습니다.",
  "timestamp": "2026-10-27T11:20:00Z"
}
```

### 공통 오류 코드

엔드포인트별 코드(`VAULT_CONFLICT` 등)는 각 절에 적는다.

| 상태 | 코드 | 메시지 | 조건 |
| :--- | :--- | :--- | :--- |
| 400 | `INVALID_PARAMETER` | 요청 형식이 올바르지 않습니다. | 필수 필드·헤더가 없거나, 값 형식이 틀리거나, JSON이 깨졌다. |
| 401 | `UNAUTHORIZED` | 로그인이 만료되었습니다. 다시 로그인해 주세요. | 토큰이 없거나 만료·폐기됐다. 클라이언트는 로그인 화면으로 보낸다. |
| 429 | `TOO_MANY_ATTEMPTS` | 로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요. | 시도 제한을 넘었다. `Retry-After` 헤더(초)를 함께 보낸다. |
| 500 | `INTERNAL_ERROR` | 일시적인 서버 오류입니다. 잠시 후 다시 시도해 주세요. | 예상하지 못한 오류. 상세 내용은 서버 로그에만 남긴다. 페이로드·인증 해시·토큰은 로그에 남기지 않는다. |

---

## 2. 엔드포인트

### 2.1. 무작위 비밀번호 생성 · 2.2. 비밀번호 보안 진단 (제거됨)

- 제거됨 ([ADR-0005](adr/ADR-0005-client-only-password-generation-and-audit.md)). 평문 비밀번호가 서버로 가지 않도록 생성과 진단은 각 클라이언트가 한다. 규칙은 [GEN-001](policies/GEN/GEN-001-password-generation-and-entropy-scoring.md)을 따른다.
- 절 번호는 기존 참조가 깨지지 않도록 비워 둔다.

---

### 2.3. 볼트 조회

| 항목 | 값 |
| :--- | :--- |
| URL | `GET /api/v1/vault` |
| 헤더 | `Authorization: Bearer <accessToken>` |
| 설명 | 로그인한 계정의 암호화된 볼트를 가져온다. 계정당 볼트는 1개이며, 토큰의 계정으로 정해진다 (ADR-0008). |

**응답 200 OK**

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

| 필드 | 타입 | 뜻 |
| :--- | :--- | :--- |
| `revision` | Int | 서버가 발급한 볼트 revision. 다음 저장 요청의 `expectedRevision`으로 그대로 보낸다 (VLT-001, ADR-0006). |
| `payload` | Object | 저장 요청에서 받은 JSON 객체. 서버는 해석하지 않고 그대로 돌려준다. |

**오류**

| 상태 | 코드 | 메시지 | 조건 |
| :--- | :--- | :--- | :--- |
| 404 | `VAULT_NOT_FOUND` | 저장된 볼트가 없습니다. | 계정은 있지만 아직 저장된 볼트가 없다. |

---

### 2.4. 볼트 저장

| 항목 | 값 |
| :--- | :--- |
| URL | `POST /api/v1/vault` |
| 헤더 | `Authorization: Bearer <accessToken>` |
| 설명 | 클라이언트가 암호화한 볼트를 로그인한 계정의 볼트로 저장한다. |

**요청 본문**

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

| 필드 | 타입 | 뜻 |
| :--- | :--- | :--- |
| `expectedRevision` | Int | 클라이언트가 마지막으로 받은 revision. 볼트를 처음 저장할 때는 `0`. |
| `payload` | Object | 암호화된 볼트([vault-spec](vault-spec.md) 3장). 페이로드 JSON이 5,242,880바이트(5MB)를 넘으면 저장하지 않는다 (VLT-001). |

**응답 200 OK**

```json
{
  "revision": 8
}
```

서버는 저장 시각을 관리하지 않는다. 동기화 판단은 `revision`으로만 한다 (ADR-0006).

**오류**

| 상태 | 코드 | 메시지 | 조건 |
| :--- | :--- | :--- | :--- |
| 409 | `VAULT_CONFLICT` | 다른 기기에서 볼트가 먼저 변경되었습니다. | `expectedRevision`이 서버의 현재 revision과 다르다. 응답에 `currentRevision`을 더한다. 클라이언트는 최신 볼트를 조회해 항목별로 병합한 뒤 다시 저장한다 (VLT-001, ADR-0006). |
| 413 | `VAULT_TOO_LARGE` | 볼트 허용 용량(5MB)을 초과했습니다. | 페이로드가 허용 용량을 넘었다. |

409 응답 예:

```json
{
  "code": "VAULT_CONFLICT",
  "message": "다른 기기에서 볼트가 먼저 변경되었습니다.",
  "currentRevision": 9,
  "timestamp": "2026-10-27T11:20:00Z"
}
```

---

### 2.5. 계정 인증

키 유도와 인증 해시 계산은 [vault-spec](vault-spec.md) 1·2장을 따른다. 서버는 마스터 비밀번호와 암호화 키를 받지 않는다 (SEC-002, ADR-0008).

| 필드 | 규칙 |
| :--- | :--- |
| `email` | 앞뒤 공백을 지우고 소문자로 맞춘 뒤 비교한다. 최대 254자. |
| `authHash` | Standard Base64(RFC 4648). 32바이트 |
| `salt`, `kdfSalt` | Standard Base64(RFC 4648). 16바이트 |

#### 2.5.1. 키 유도 정보 조회 (Prelogin)

| 항목 | 값 |
| :--- | :--- |
| URL | `POST /api/v1/auth/prelogin` (토큰 불필요) |
| 요청 | `{ "email": "user@example.com" }` |
| 응답 200 OK | `{ "kdf": "PBKDF2-HMAC-SHA256", "iterations": 600000, "salt": "dGhpcyBpcyBhIHNhbHQxNg==" }` |

> **주의** 가입되지 않은 이메일에도 200으로 응답한다. 서버 비밀키로 만든 가짜 salt를 같은 이메일에 항상 같은 값으로 돌려준다. 응답으로 계정 존재 여부를 알 수 없다.

#### 2.5.2. 가입 (Register)

| 항목 | 값 |
| :--- | :--- |
| URL | `POST /api/v1/auth/register` (토큰 불필요) |
| 응답 201 Created | `{ "accessToken": "<토큰>", "expiresAt": "2026-11-27T11:20:00Z" }` |

```json
{ "email": "user@example.com", "authHash": "<32바이트 Base64>", "kdfSalt": "<16바이트 Base64>", "kdfIterations": 600000 }
```

`kdfIterations`는 600,000 이상이어야 한다.

| 상태 | 코드 | 메시지 | 조건 |
| :--- | :--- | :--- | :--- |
| 409 | `EMAIL_ALREADY_REGISTERED` | 이미 가입된 이메일입니다. | 이미 가입된 이메일 |
| 403 | `REGISTRATION_DISABLED` | 현재 새 계정을 만들 수 없습니다. | 서버 설정으로 가입이 꺼져 있다. |

#### 2.5.3. 로그인 (Login)

| 항목 | 값 |
| :--- | :--- |
| URL | `POST /api/v1/auth/login` (토큰 불필요) |
| 요청 | `{ "email": "user@example.com", "authHash": "<32바이트 Base64>" }` |
| 응답 200 OK | `{ "accessToken": "<토큰>", "expiresAt": "2026-11-27T11:20:00Z" }` |

| 상태 | 코드 | 메시지 | 조건 |
| :--- | :--- | :--- | :--- |
| 401 | `AUTH_FAILED` | 이메일 또는 마스터 비밀번호가 올바르지 않습니다. | 이메일이 없거나 인증 해시가 틀렸다. 두 경우를 응답 내용으로 구분하지 않는다. |
| 429 | `TOO_MANY_ATTEMPTS` | (1장 공통 오류 코드) | 계정 또는 IP 시도 제한을 넘었다. |

#### 2.5.4. 로그아웃 (Logout)

| 항목 | 값 |
| :--- | :--- |
| URL | `POST /api/v1/auth/logout` |
| 헤더 | `Authorization: Bearer <accessToken>` |
| 응답 204 No Content | 이 토큰만 폐기한다. 이미 폐기된 토큰으로 다시 요청해도 204로 응답한다. |
