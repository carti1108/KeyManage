rootProject.name = "keymanage-back"

// 포트-어댑터 구조 (ADR-0002): 의존 방향은 api, infrastructure -> domain, app -> 전부
include(
    "domain",
    "infrastructure",
    "api",
    "app",
)
