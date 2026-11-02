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
		} catch {
			throw MasterPasswordChangeError.networkError("네트워크 연결 실패: \(error.localizedDescription)")
		}

		guard let httpResp = response as? HTTPURLResponse, (200...299).contains(httpResp.statusCode) else {
			throw MasterPasswordChangeError.networkError("키 유도 정보 조회 실패")
		}
		return try JSONDecoder().decode(PreloginResponse.self, from: data)
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
}
