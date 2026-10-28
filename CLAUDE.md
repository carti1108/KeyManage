# KeyManage 개발 가이드라인

## 핵심 아키텍처 및 원칙
- **영지식(Zero-Knowledge) 보안**: 마스터 키 유도 및 암·복호화는 클라이언트 내부에서만 수행하며, 서버는 암호문(Ciphertext)만 보관한다.
- **포트-어댑터(Hexagonal) 멀티 모듈**: 백엔드는 도메인 중심의 4개 모듈(`domain`, `api`, `infrastructure`, `app`)로 관심사를 엄격히 분리한다.
- **의존성 역전(DIP)**: 클라이언트 UI는 멍청한 껍데기(Dumb View)로 유지하며, 백엔드 통신 및 암호화 모듈을 추상화 인터페이스를 통해 주입받는다.

## 문서 주도 개발 (Docs-Driven Development)
- **무조건 구현 전 문서 작성이 먼저다.**

### 아키텍처 결정 기록 (ADR)
- 유의미한 설계·기술 결정(대안이 있었고 트레이드오프를 따져 하나를 고른 경우)은 `docs/adr/`에 ADR로 남긴다.
- ADR은 채택 후 내용을 고치지 않는다. 결정이 바뀌면 새 ADR을 쓰고 옛 ADR은 '대체됨'으로만 표시한다.
- ADR 기록·조회·대체는 `adr-manager:adr-manager` 스킬 절차를 따른다.

### 서비스 정책 SSOT
- 서비스 정책의 유일한 진실 공급원은 `docs/policies/`다.
- 정책의 조회·추가·수정·폐기·점검은 `policy-manager:policy-manager` 스킬 절차를 따른다.
- 정책을 고치기 전에 반드시 사용자에게 8개 카테고리(경계값, 예외, 사용자 문구 등)를 확인받는다.

### AI 활용 기록
- AI 활용 내용은 `docs/ai-usage/log/`에 항목당 새 파일로 추가한다. 기존 항목 파일은 고치지 않고 `log/README.md` 목록 표에 한 줄을 추가한다. 형식은 `docs/ai-usage/README.md`를 따른다.
- 기록 절차는 `project-docs:ai-usage` 스킬을 따른다.

### 검증 위임 (ADR-0009)
- 코드 PR을 열기 전 작성 맥락과 분리된 `/code-review`를 실행하고(인증·암호·저장소 변경은 `/security-review` 추가), 결과 요약을 PR 본문의 "독립 리뷰"에 남긴다.

### 문서 검사
- 문서를 고치면 `bash Scripts/check-docs.sh`를 실행해 링크, 정책·ADR·로그 목록 표, 정책·ADR 번호 참조가 맞는지 확인한다. 위반이 있으면 고친 뒤에 커밋한다.
- 루트 `CLAUDE.md`·`AGENTS.md`는 매 턴 컨텍스트에 포함되므로 작게 유지한다. 세부 도메인 규칙·릴리스 절차·아키텍처 결정은 `docs/README.md` 및 하위 문서에 두고 루트에는 링크만 남긴다.
