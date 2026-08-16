import Foundation

actor AnvilClient {
    let baseURL: URL
    private let session: URLSession
    private let decoder: JSONDecoder
    private let encoder: JSONEncoder

    init(baseURL: URL, session: URLSession = .shared) {
        self.baseURL = baseURL
        self.session = session
        self.decoder = JSONDecoder()
        self.encoder = JSONEncoder()
    }

    func health() async throws -> HealthResponse {
        try await get("/api/health", as: HealthResponse.self)
    }

    func createThread(root: String) async throws -> AnvilThread {
        try await post("/v1/threads", body: ["cwd": root], as: AnvilThread.self)
    }

    func startRun(
        threadId: String,
        mode: Mode,
        model: String,
        prompt: String
    ) async throws -> AnvilRun {
        let body: [String: String] = [
            "mode": mode.rawValue,
            "model": model,
            "message": prompt
        ]
        return try await post("/v1/threads/\(threadId)/runs", body: body, as: AnvilRun.self)
    }

    func respondApproval(id: String, decision: ApprovalDecision) async throws {
        struct Response: Decodable {
            let approvalId: String
            let decision: String

            enum CodingKeys: String, CodingKey {
                case approvalId = "approval_id"
                case decision
            }
        }
        _ = try await post(
            "/v1/approvals/\(id)/respond",
            body: ["decision": decision.rawValue],
            as: Response.self
        )
    }

    func workspaceTree(threadId: String) async throws -> WorkspaceTreeResponse {
        try await get("/v1/workspace/tree?thread_id=\(threadId)", as: WorkspaceTreeResponse.self)
    }

    func workspaceFile(threadId: String, path: String) async throws -> WorkspaceFileResponse {
        let encoded = path.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? path
        return try await get(
            "/v1/workspace/file?thread_id=\(threadId)&path=\(encoded)",
            as: WorkspaceFileResponse.self
        )
    }

    func events(runId: String, fromSeq: Int = 0) -> AsyncThrowingStream<AnvilEvent, Error> {
        AsyncThrowingStream { continuation in
            Task {
                do {
                    let url = baseURL.appendingPathComponent("v1/runs/\(runId)/events")
                    var components = URLComponents(url: url, resolvingAgainstBaseURL: false)!
                    components.queryItems = [URLQueryItem(name: "from_seq", value: String(fromSeq))]
                    guard let requestURL = components.url else {
                        throw AnvilClientError.invalidURL
                    }

                    var request = URLRequest(url: requestURL)
                    request.setValue("text/event-stream", forHTTPHeaderField: Accept)

                    let (bytes, response) = try await session.bytes(for: request)
                    guard let http = response as? HTTPURLResponse else {
                        throw AnvilClientError.sseFailed("no HTTP response")
                    }
                    guard (200 ..< 300).contains(http.statusCode) else {
                        throw AnvilClientError.httpStatus(http.statusCode, "SSE attach failed")
                    }

                    for try await jsonLine in SseStream.jsonLines(from: bytes) {
                        let data = Data(jsonLine.utf8)
                        do {
                            let event = try decoder.decode(AnvilEvent.self, from: data)
                            continuation.yield(event)
                            if event.type == "run.completed"
                                || event.type == "run.failed"
                                || event.type == "run.cancelled"
                            {
                                continuation.finish()
                                return
                            }
                        } catch {
                            throw AnvilClientError.decodingFailed(error.localizedDescription)
                        }
                    }
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: error)
                }
            }
        }
    }

    // MARK: - HTTP helpers

    private func get<T: Decodable>(_ path: String, as type: T.Type) async throws -> T {
        guard let url = URL(string: path, relativeTo: baseURL) else {
            throw AnvilClientError.invalidURL
        }
        let (data, response) = try await session.data(from: url)
        try validate(response: response, data: data)
        do {
            return try decoder.decode(T.self, from: data)
        } catch {
            throw AnvilClientError.decodingFailed(error.localizedDescription)
        }
    }

    private func post<T: Decodable, Body: Encodable>(
        _ path: String,
        body: Body,
        as type: T.Type
    ) async throws -> T {
        guard let url = URL(string: path, relativeTo: baseURL) else {
            throw AnvilClientError.invalidURL
        }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try encoder.encode(body)
        let (data, response) = try await session.data(for: request)
        try validate(response: response, data: data)
        do {
            return try decoder.decode(T.self, from: data)
        } catch {
            throw AnvilClientError.decodingFailed(error.localizedDescription)
        }
    }

    private func validate(response: URLResponse, data: Data) throws {
        guard let http = response as? HTTPURLResponse else { return }
        guard (200 ..< 300).contains(http.statusCode) else {
            let body = String(data: data, encoding: .utf8) ?? ""
            throw AnvilClientError.httpStatus(http.statusCode, body)
        }
    }
}

private let Accept = "Accept"
