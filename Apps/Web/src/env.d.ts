interface ImportMetaEnv {
  /** 서버 출처. 비우면 같은 출처(개발 중에는 Vite 프록시)로 요청한다 (ADR-0013) */
  readonly VITE_API_BASE_URL?: string
}
