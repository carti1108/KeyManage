# 플랫폼 간 암호 테스트 벡터

iOS와 웹(ADR-0010)이 **바이트 단위까지 같은** 마스터 키·암호화 키·인증 해시·볼트 암호문을 만드는지 확인하는 기준값을 만들고 검증합니다.
규격은 [vault-spec](../../docs/vault-spec.md) 2·3장, 결과 파일은 [`docs/crypto-test-vectors.json`](../../docs/crypto-test-vectors.json)입니다.

## 구성

| 파일 | 역할 |
| :--- | :--- |
| `vectors.mjs` | 웹 클라이언트가 쓸 **WebCrypto**로 벡터를 생성한다. `--check`는 파일이 다시 계산한 값과 같은지만 본다 |
| `crosscheck.py` | **Python 표준 라이브러리**로 전처리·PBKDF2·HKDF를 다시 계산한다 |
| `CrossCheck.java` | **JDK**로 전처리·PBKDF2·HKDF·AES-256-GCM(AAD) 암호화·복호화와 틀린 키·헤더 변조 거절을 다시 계산한다 |

한 구현의 실수가 기준값에 그대로 박히지 않도록, 서로 다른 세 구현이 모두 같은 값을 내야 통과로 봅니다. 검사기는 반복 횟수·HKDF info·AAD 같은 규격 값을 검사 대상 파일에서 읽지 않고 직접 고정해 둡니다. 세 가지 모두 CI의 `crypto-vectors` 작업에서 돕니다(ADR-0011).

## 사용법 (저장소 루트에서)

```bash
node Tools/crypto-vectors/vectors.mjs            # 벡터 생성 (규격을 바꿨을 때만)
node Tools/crypto-vectors/vectors.mjs --check    # 파일 검사
python3 Tools/crypto-vectors/crosscheck.py       # Python 교차 검증
java Tools/crypto-vectors/CrossCheck.java        # JDK 교차 검증 (JDK 21)
```

## 케이스

- `ascii-passphrase`: 공백이 들어간 패스프레이즈
- `korean-emoji`: 한글과 4바이트 UTF-8 이모지
- `nfc-composed` / `nfd-decomposed`: 같은 글자를 다른 유니코드 표현으로 입력해도 NFC 정규화로 같은 결과
- `surrounding-whitespace`: 앞뒤 공백·탭·줄바꿈·전각 공백은 잘리고 중간 공백은 유지되어 `ascii-passphrase`와 같은 결과
- `vault-ascii-passphrase`: 볼트 평문(vault-spec 4장 예시)의 AES-256-GCM 암호문과 태그. AAD는 `keymanage-vault|1|AES-256-GCM`. **고정 IV는 테스트 전용**이며 실제로는 암호화할 때마다 새로 만든다
