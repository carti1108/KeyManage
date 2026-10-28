package com.keymanage.back.api.error

import com.keymanage.back.domain.auth.AuthFailedException
import com.keymanage.back.domain.auth.EmailAlreadyRegisteredException
import com.keymanage.back.domain.auth.InvalidAuthInputException
import com.keymanage.back.domain.auth.RegistrationDisabledException
import com.keymanage.back.domain.auth.TooManyAttemptsException
import com.keymanage.back.domain.auth.UnauthorizedException
import com.keymanage.back.domain.vault.InvalidVaultIdException
import com.keymanage.back.domain.vault.VaultConflictException
import com.keymanage.back.domain.vault.VaultTooLargeException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.ErrorResponse
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * 모든 실패를 표준 오류 형식(`{code, message, timestamp}`)으로 바꾸는 한 곳 (api-spec 1장).
 *
 * 예외 메시지와 스택 트레이스는 응답에 넣지 않는다. 요청 본문(볼트 페이로드·인증 해시)과 토큰은 로그에도 남기지 않는다 (VLT-001, SEC-002).
 */
@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(ApiException::class)
    fun api(e: ApiException): ResponseEntity<ErrorBody> = respond(e.error)

    @ExceptionHandler(VaultConflictException::class)
    fun conflict(e: VaultConflictException): ResponseEntity<ErrorBody> =
        respond(ApiError.VAULT_CONFLICT, currentRevision = e.currentRevision.value)

    @ExceptionHandler(VaultTooLargeException::class)
    fun tooLarge(e: VaultTooLargeException): ResponseEntity<ErrorBody> = respond(ApiError.VAULT_TOO_LARGE)

    @ExceptionHandler(InvalidVaultIdException::class, InvalidAuthInputException::class, MissingRequestHeaderException::class)
    fun invalidRequest(e: Exception): ResponseEntity<ErrorBody> = respond(ApiError.INVALID_PARAMETER)

    @ExceptionHandler(UnauthorizedException::class)
    fun unauthorized(e: UnauthorizedException): ResponseEntity<ErrorBody> = respond(ApiError.UNAUTHORIZED)

    // 이메일이 없는 경우와 해시가 틀린 경우를 같은 응답으로 돌려준다 (SEC-002)
    @ExceptionHandler(AuthFailedException::class)
    fun authFailed(e: AuthFailedException): ResponseEntity<ErrorBody> = respond(ApiError.AUTH_FAILED)

    @ExceptionHandler(RegistrationDisabledException::class)
    fun registrationDisabled(e: RegistrationDisabledException): ResponseEntity<ErrorBody> = respond(ApiError.REGISTRATION_DISABLED)

    @ExceptionHandler(EmailAlreadyRegisteredException::class)
    fun emailTaken(e: EmailAlreadyRegisteredException): ResponseEntity<ErrorBody> = respond(ApiError.EMAIL_ALREADY_REGISTERED)

    @ExceptionHandler(TooManyAttemptsException::class)
    fun tooManyAttempts(e: TooManyAttemptsException): ResponseEntity<ErrorBody> =
        ResponseEntity.status(ApiError.TOO_MANY_ATTEMPTS.status)
            .header(HttpHeaders.RETRY_AFTER, ErrorResponses.retryAfterSeconds(e.retryAfter).toString())
            .body(ErrorBody.of(ApiError.TOO_MANY_ATTEMPTS))

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun unreadable(e: HttpMessageNotReadableException): ResponseEntity<ErrorBody> {
        // Content-Length 없이 상한을 넘은 본문은 읽는 도중 끊겨 이 예외로 감싸여 온다
        val cutOff = generateSequence<Throwable>(e) { it.cause }.any { it is RequestBodyTooLargeException }
        return respond(if (cutOff) ApiError.VAULT_TOO_LARGE else ApiError.INVALID_PARAMETER)
    }

    @ExceptionHandler(Exception::class)
    fun unexpected(e: Exception): ResponseEntity<ErrorBody> {
        // Spring이 만든 4xx(지원하지 않는 메서드, 없는 경로 등)는 상태 코드를 유지한 채 요청 문제로 알린다
        if (e is ErrorResponse && e.statusCode.is4xxClientError) {
            return ResponseEntity.status(e.statusCode).body(ErrorBody.of(ApiError.INVALID_PARAMETER))
        }
        log.error("예상하지 못한 오류", e)
        return respond(ApiError.INTERNAL_ERROR)
    }

    private fun respond(error: ApiError, currentRevision: Long? = null): ResponseEntity<ErrorBody> =
        ResponseEntity.status(error.status).body(ErrorBody.of(error, currentRevision))

    private companion object {
        val log = LoggerFactory.getLogger(ApiExceptionHandler::class.java)
    }
}
