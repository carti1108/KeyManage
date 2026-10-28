package com.keymanage.back.domain.auth

import java.time.Duration

// 인증 도메인 오류. API 계층에서 각 주석의 상태 코드로 응답한다 (api-spec 2.5, SEC-002)

/** 이메일·인증 해시·KDF 값 형식 오류. 400 INVALID_PARAMETER */
class InvalidAuthInputException(val field: String) : RuntimeException("$field 형식이 올바르지 않음")

/** 서버 설정으로 가입이 꺼져 있음. 403 REGISTRATION_DISABLED */
class RegistrationDisabledException : RuntimeException("가입이 꺼져 있음")

/** 이미 가입된 이메일. 409 EMAIL_ALREADY_REGISTERED */
class EmailAlreadyRegisteredException : RuntimeException("이미 가입된 이메일")

/** 이메일이 없거나 인증 해시가 틀림. 두 경우를 구분하지 않는다. 401 AUTH_FAILED */
class AuthFailedException : RuntimeException("인증 실패")

/** 로그인 시도 제한 초과. 429 TOO_MANY_ATTEMPTS, [retryAfter]는 Retry-After 헤더로 보낸다 */
class TooManyAttemptsException(val retryAfter: Duration) : RuntimeException("시도 제한 초과")

/** 토큰이 없거나 만료·폐기됨. 401 UNAUTHORIZED */
class UnauthorizedException : RuntimeException("유효하지 않은 세션")
