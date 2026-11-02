import Foundation

public final class URLSessionAuthNetworkClient: AuthNetworkClientProtocol, @unchecked Sendable {
	public let baseURL: URL
	public let session: URLSession

	public init(
		baseURL: URL = URL(string: "http://localhost:8080/api/v1")!,
		session: URLSession = .shared
	) {
		self.baseURL = baseURL
		self.session = session
	}

	public func prelogin(email: String) async throws -> PreloginResponse {
		let url = baseURL.appendingPathComponent("auth/prelogin")
		var request = URLRequest(url: url)
		request.httpMethod = "POST"
		request.setValue("application/json; charset=UTF-8", forHTTPHeaderField: "Content-Type")
		let body = ["email": email]
		request.httpBody = try JSONSerialization.data(withJSONObject: body)

		let (data, response): (Data, URLResponse)
		do {
			(data, response) = try await session.data(for: request)
		} catch is CancellationError {
			throw CancellationError()
		} catch let urlError as URLError where urlError.code == .notConnectedToInternet || urlError.code == .networkConnectionLost {
			throw AuthError.offline
		} catch {
			throw AuthError.offline
		}

		guard let httpResp = response as? HTTPURLResponse else {
			throw AuthError.networkError("응답을 수신하지 못했습니다.")
		}

		if (200...299).contains(httpResp.statusCode) {
			return try JSONDecoder().decode(PreloginResponse.self, from: data)
		}

		if httpResp.statusCode == 401 || httpResp.statusCode == 404 {
			throw AuthError.loginFailed
		} else if httpResp.statusCode == 429 {
			throw AuthError.tooManyAttempts
		} else {
			throw AuthError.networkError("서버 오류: HTTP \(httpResp.statusCode)")
		}
	}

	public func register(request: RegisterRequestPayload) async throws -> RegisterResponsePayload {
		let url = baseURL.appendingPathComponent("auth/register")
		var urlRequest = URLRequest(url: url)
		urlRequest.httpMethod = "POST"
		urlRequest.setValue("application/json; charset=UTF-8", forHTTPHeaderField: "Content-Type")
		urlRequest.httpBody = try JSONEncoder().encode(request)

		let (data, response): (Data, URLResponse)
		do {
			(data, response) = try await session.data(for: urlRequest)
		} catch is CancellationError {
			throw CancellationError()
		} catch let urlError as URLError where urlError.code == .notConnectedToInternet || urlError.code == .networkConnectionLost {
			throw AuthError.offline
		} catch {
			throw AuthError.offline
		}

		guard let httpResp = response as? HTTPURLResponse else {
			throw AuthError.networkError("응답을 수신하지 못했습니다.")
		}

		if (200...299).contains(httpResp.statusCode) {
			return try JSONDecoder().decode(RegisterResponsePayload.self, from: data)
		}

		if httpResp.statusCode == 409 {
			throw AuthError.emailAlreadyRegistered
		} else if httpResp.statusCode == 403 {
			throw AuthError.registrationDisabled
		} else if httpResp.statusCode == 429 {
			throw AuthError.tooManyAttempts
		} else {
			throw AuthError.networkError("서버 오류: HTTP \(httpResp.statusCode)")
		}
	}

	public func login(request: LoginRequestPayload) async throws -> LoginResponsePayload {
		let url = baseURL.appendingPathComponent("auth/login")
		var urlRequest = URLRequest(url: url)
		urlRequest.httpMethod = "POST"
		urlRequest.setValue("application/json; charset=UTF-8", forHTTPHeaderField: "Content-Type")
		urlRequest.httpBody = try JSONEncoder().encode(request)

		let (data, response): (Data, URLResponse)
		do {
			(data, response) = try await session.data(for: urlRequest)
		} catch is CancellationError {
			throw CancellationError()
		} catch let urlError as URLError where urlError.code == .notConnectedToInternet || urlError.code == .networkConnectionLost {
			throw AuthError.offline
		} catch {
			throw AuthError.offline
		}

		guard let httpResp = response as? HTTPURLResponse else {
			throw AuthError.networkError("응답을 수신하지 못했습니다.")
		}

		if (200...299).contains(httpResp.statusCode) {
			return try JSONDecoder().decode(LoginResponsePayload.self, from: data)
		}

		if httpResp.statusCode == 401 {
			throw AuthError.loginFailed
		} else if httpResp.statusCode == 429 {
			throw AuthError.tooManyAttempts
		} else {
			throw AuthError.networkError("서버 오류: HTTP \(httpResp.statusCode)")
		}
	}

	public func getVault(accessToken: String) async throws -> GetVaultResponsePayload? {
		let url = baseURL.appendingPathComponent("vault")
		var urlRequest = URLRequest(url: url)
		urlRequest.httpMethod = "GET"
		urlRequest.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")

		let (data, response): (Data, URLResponse)
		do {
			(data, response) = try await session.data(for: urlRequest)
		} catch is CancellationError {
			throw CancellationError()
		} catch let urlError as URLError where urlError.code == .notConnectedToInternet || urlError.code == .networkConnectionLost {
			throw AuthError.offline
		} catch {
			throw AuthError.offline
		}

		guard let httpResp = response as? HTTPURLResponse else {
			throw AuthError.networkError("응답을 수신하지 못했습니다.")
		}

		if httpResp.statusCode == 404 {
			return nil
		}

		if (200...299).contains(httpResp.statusCode) {
			return try JSONDecoder().decode(GetVaultResponsePayload.self, from: data)
		}

		if httpResp.statusCode == 401 {
			throw AuthError.sessionExpired
		} else {
			throw AuthError.networkError("서버 오류: HTTP \(httpResp.statusCode)")
		}
	}

	public func changePassword(
		accessToken: String,
		request: ChangePasswordRequestPayload
	) async throws -> ChangePasswordResponsePayload {
		let url = baseURL.appendingPathComponent("auth/change-password")
		var urlRequest = URLRequest(url: url)
		urlRequest.httpMethod = "POST"
		urlRequest.setValue("application/json; charset=UTF-8", forHTTPHeaderField: "Content-Type")
		urlRequest.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
		urlRequest.httpBody = try JSONEncoder().encode(request)

		let (data, response): (Data, URLResponse)
		do {
			(data, response) = try await session.data(for: urlRequest)
		} catch {
			throw MasterPasswordChangeError.networkError("네트워크 연결 실패: \(error.localizedDescription)")
		}

		guard let httpResp = response as? HTTPURLResponse else {
			throw MasterPasswordChangeError.networkError("응답을 수신하지 못했습니다.")
		}

		if (200...299).contains(httpResp.statusCode) {
			return try JSONDecoder().decode(ChangePasswordResponsePayload.self, from: data)
		}

		if httpResp.statusCode == 401 {
			if let errObj = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
			   let code = errObj["code"] as? String, code == "AUTH_FAILED" {
				throw MasterPasswordChangeError.invalidCurrentPassword
			}
			throw MasterPasswordChangeError.sessionExpired
		} else if httpResp.statusCode == 409 {
			throw MasterPasswordChangeError.conflict
		} else if httpResp.statusCode == 413 {
			throw MasterPasswordChangeError.vaultTooLarge
		} else if httpResp.statusCode == 429 {
			throw MasterPasswordChangeError.tooManyAttempts
		} else {
			throw MasterPasswordChangeError.networkError("서버 오류: HTTP \(httpResp.statusCode)")
		}
	}

	public func deleteAccount(
		accessToken: String,
		authHash: String
	) async throws {
		let url = baseURL.appendingPathComponent("auth/delete-account")
		var urlRequest = URLRequest(url: url)
		urlRequest.httpMethod = "POST"
		urlRequest.setValue("application/json; charset=UTF-8", forHTTPHeaderField: "Content-Type")
		urlRequest.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
		let payload = DeleteAccountRequestPayload(authHash: authHash)
		urlRequest.httpBody = try JSONEncoder().encode(payload)

		let (data, response): (Data, URLResponse)
		do {
			(data, response) = try await session.data(for: urlRequest)
		} catch is CancellationError {
			throw CancellationError()
		} catch let urlError as URLError where urlError.code == .notConnectedToInternet || urlError.code == .networkConnectionLost {
			throw AccountDeletionError.offline
		} catch {
			throw AccountDeletionError.networkError(AccountDeletionStrings.offlineMessage)
		}

		guard let httpResp = response as? HTTPURLResponse else {
			throw AccountDeletionError.networkError("응답을 수신하지 못했습니다.")
		}

		if (200...299).contains(httpResp.statusCode) {
			return
		}

		if httpResp.statusCode == 401 {
			if let errObj = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
			   let code = errObj["code"] as? String, code == "AUTH_FAILED" {
				throw AccountDeletionError.invalidPassword
			}
			throw AccountDeletionError.sessionExpired
		} else if httpResp.statusCode == 429 {
			throw AccountDeletionError.tooManyAttempts
		} else {
			throw AccountDeletionError.networkError("서버 오류: HTTP \(httpResp.statusCode)")
		}
	}
}
