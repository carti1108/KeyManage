# 2026-10-29 · 플랫폼 간 암호 테스트 벡터 작성 (#26)

> 요약: iOS와 웹이 바이트 단위까지 같은 키·인증 해시·볼트 암호문을 만들도록 vault-spec의 빈칸(비밀번호 전처리, HKDF salt, AAD)을 채우고(ADR-0011), WebCrypto로 테스트 벡터를 만든 뒤 Python·JDK 독립 구현으로 교차 검증했다. 독립 리뷰로 AAD 결정의 근거가 불완전했음을 발견해 헤더를 AAD로 묶도록 바꿨다.

## 사용 도구

- 도구: Claude Code (Claude Opus 5.5), Node.js WebCrypto, Python 표준 라이브러리, JDK 21
- 플러그인/스킬: `code-review`(Claude Code 내장, 독립 서브에이전트), `issue-worktree:commit-rules`, `project-docs:ai-usage`

## 요청

- 클라이언트 구현 전 공통 선행 작업으로 암호 테스트 벡터 진행. 착수 전 되돌리기 비싼 결정 두 가지(유니코드 정규화, AES-GCM AAD)를 확인받았다.

## AI 산출물

- 결정 제안: 마스터 비밀번호 NFC 정규화(같은 글자의 다른 유니코드 표현으로 플랫폼 간 로그인 실패 방지). AAD는 처음에 "없음"을 제안했다가 리뷰 후 헤더 바인딩으로 바꿔 제안했다.
- [`docs/adr/ADR-0011-password-preprocessing-and-header-aad.md`](../../adr/ADR-0011-password-preprocessing-and-header-aad.md): 전처리와 헤더 AAD 결정, 페이로드 평문 updatedAt 제거.
- vault-spec 보완: 전처리 순서(NFC → 앞뒤 공백 제거 → UTF-8)와 제거할 공백 문자 고정(U+0020, U+0009, U+000A, U+000D, U+00A0, U+3000), HKDF salt 빈 값의 의미, 태그 분리, 헤더 AAD(`keymanage-vault|{version}|{cipher}`), 페이로드 평문 updatedAt 제거(api-spec 예시 포함).
- `Tools/crypto-vectors/`: `vectors.mjs`(WebCrypto 생성기, `--check`), `crosscheck.py`(Python), `CrossCheck.java`(JDK, AES-GCM 포함), `README.md`. 결과 파일 `docs/crypto-test-vectors.json`.
- 케이스: ASCII 패스프레이즈, 한글·이모지, NFC/NFD 동일 결과, 앞뒤 공백 동일 결과, 볼트 암호화.
- CI: 생성기 재계산, Python·JDK 교차 검증을 백엔드 결과와 무관한 별도 `crypto-vectors` 작업으로 추가.
- 애플 "강력한 암호" 형식(6자씩 하이픈 구분, 헷갈리는 문자 제외)은 생성기 구현 시 GEN-001 옵션으로 넣고, 가입 화면 자동 제안은 자동 완성 후속 작업에 포함한다고 안내.

## 사람의 판단

- NFC 정규화와 AAD 없음을 추천대로 승인했다.
- AAD를 헤더 바인딩(B안)으로 바꾸는 것을 승인했다.
- 구현 순서를 바꿔 iOS보다 웹 클라이언트를 먼저 작성하기로 했다(ADR-0010의 "iOS와 웹 먼저" 범위 안의 순서 조정).
- 비밀번호 앞뒤 공백을 자르지 않겠다는 제안에 이의를 제기했다. 실제로 앞뒤 공백을 쓰는 경우가 드물다는 판단에 따라, 앞뒤는 자르고 중간 공백(패스프레이즈)은 유지하도록 바꿨다.

## 검증

- 세 독립 구현(Node WebCrypto, Python, JDK)의 결과 일치: 키 유도 5건, AES-256-GCM(AAD) 암호화·복호화, 틀린 키·헤더 변조 거절, 같은 결과 케이스.
- 검사기 자체 검증: 벡터 파일의 인증 해시·태그를 바꾼 경우, 기록된 반복 횟수만 바꾼 경우, 헤더 version만 바꾼 경우를 모두 실패로 잡음.
- 독립 리뷰(`/code-review`) 10건 모두 반영: AAD 근거 정정과 ADR 추가, 검사기가 규격 값을 파일에서 읽던 문제, 같은 결과 케이스 미검사, JDK 첫 케이스만 검사·키 고정·실행 위치 의존, CI 단계 의존, 생성기 import 부작용.
- `bash Scripts/check-docs.sh` 통과.

## 검증하지 못한 것

1. iOS(CryptoKit·CommonCrypto)로는 아직 재현하지 않았다. iOS 클라이언트의 첫 단위 테스트로 이 벡터를 재현해야 한다.

## AI의 오류와 정정

1. "AAD 없음"을 추천하면서 다른 계정 볼트와의 바꿔치기만 고려했고, 평문 헤더(version·cipher·updatedAt)가 인증되지 않는다는 점을 놓쳤다. 사용자는 그 근거로 승인했다. 독립 리뷰가 지적해 근거가 불완전했음을 밝히고 헤더를 AAD로 묶는 안을 다시 승인받았다.
2. 처음 만든 Python 검사기는 반복 횟수와 info 문자열을 검사 대상 파일에서 읽어, 생성기의 잘못된 값을 그대로 따라갈 수 있었다. 독립 리뷰 지적으로 규격 값을 고정했다.
3. 처음에 "앞뒤 공백을 자르지 않는다"고 제안했으나, 실제 사용 패턴(앞뒤 공백은 거의 없고 실수로 들어가는 경우가 많음)을 고려하지 못했다. 사용자 지적으로 앞뒤 공백을 자르되, 플랫폼마다 trim 범위가 다른 문제를 막기 위해 제거 대상 문자를 고정했다.

## 근거 링크

- Issue #26
- 커밋: PR 참고
