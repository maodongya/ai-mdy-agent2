import Foundation

enum Mode: String, Codable, CaseIterable, Identifiable {
    case ask
    case plan
    case agent
    case debug

    var id: String { rawValue }

    var label: String {
        switch self {
        case .ask: return "Ask"
        case .plan: return "Plan"
        case .agent: return "Agent"
        case .debug: return "Debug"
        }
    }
}

enum ApprovalDecision: String, Codable, CaseIterable {
    case allowOnce = "allow_once"
    case allowSession = "allow_session"
    case deny
    case alwaysDeny = "always_deny"
}

struct AnvilThread: Codable, Identifiable {
    let threadId: String
    let workspaceRoot: String
    let status: String

    var id: String { threadId }

    enum CodingKeys: String, CodingKey {
        case threadId = "thread_id"
        case workspaceRoot = "workspace_root"
        case status
    }
}

struct AnvilRun: Codable, Identifiable {
    let runId: String
    let threadId: String
    let status: String

    var id: String { runId }

    enum CodingKeys: String, CodingKey {
        case runId = "run_id"
        case threadId = "thread_id"
        case status
    }
}

struct HealthResponse: Codable {
    let ok: Bool
    let name: String
    let protocolVersion: String

    enum CodingKeys: String, CodingKey {
        case ok, name
        case protocolVersion = "protocolVersion"
    }
}

struct WorkspaceNode: Codable, Identifiable, Hashable {
    let path: String
    let type: String

    var id: String { path }

    var isDirectory: Bool { type == "dir" }
}

struct WorkspaceTreeResponse: Codable {
    let threadId: String
    let nodes: [WorkspaceNode]

    enum CodingKeys: String, CodingKey {
        case threadId = "thread_id"
        case nodes
    }
}

struct WorkspaceFileResponse: Codable {
    let path: String
    let content: String
}

struct AnvilEvent: Codable, Identifiable, Hashable {
    let protocolVersion: String?
    let threadId: String
    let runId: String
    let seq: Int
    let type: String
    let ts: String
    let payload: JSONValue?

    var id: Int { seq }

    enum CodingKeys: String, CodingKey {
        case protocolVersion = "protocol_version"
        case threadId = "thread_id"
        case runId = "run_id"
        case seq, type, ts, payload
    }

    func payloadString(_ key: String) -> String? {
        payload?[key]?.stringValue
    }

    func payloadObject(_ key: String) -> [String: JSONValue]? {
        payload?[key]?.objectValue
    }
}

/// Lightweight JSON for event payloads.
enum JSONValue: Codable, Hashable {
    case string(String)
    case number(Double)
    case bool(Bool)
    case object([String: JSONValue])
    case array([JSONValue])
    case null

    var stringValue: String? {
        if case .string(let s) = self { return s }
        return nil
    }

    var objectValue: [String: JSONValue]? {
        if case .object(let o) = self { return o }
        return nil
    }

    var arrayValue: [JSONValue]? {
        if case .array(let a) = self { return a }
        return nil
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if container.decodeNil() {
            self = .null
        } else if let b = try? container.decode(Bool.self) {
            self = .bool(b)
        } else if let i = try? container.decode(Int.self) {
            self = .number(Double(i))
        } else if let d = try? container.decode(Double.self) {
            self = .number(d)
        } else if let s = try? container.decode(String.self) {
            self = .string(s)
        } else if let a = try? container.decode([JSONValue].self) {
            self = .array(a)
        } else if let o = try? container.decode([String: JSONValue].self) {
            self = .object(o)
        } else {
            throw DecodingError.dataCorruptedError(in: container, debugDescription: "unsupported JSON")
        }
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        switch self {
        case .string(let s): try container.encode(s)
        case .number(let n): try container.encode(n)
        case .bool(let b): try container.encode(b)
        case .object(let o): try container.encode(o)
        case .array(let a): try container.encode(a)
        case .null: try container.encodeNil()
        }
    }
}

struct ApprovalRequest: Identifiable, Hashable {
    let approvalId: String
    let tool: String
    let risk: String
    let preview: [String: JSONValue]

    var id: String { approvalId }

    var summary: String {
        preview["summary"]?.stringValue ?? tool
    }
}

enum AnvilClientError: Error, LocalizedError {
    case invalidURL
    case httpStatus(Int, String)
    case decodingFailed(String)
    case sseFailed(String)

    var errorDescription: String? {
        switch self {
        case .invalidURL: return "Invalid server URL"
        case .httpStatus(let code, let body): return "HTTP \(code): \(body)"
        case .decodingFailed(let msg): return "Decode error: \(msg)"
        case .sseFailed(let msg): return "SSE error: \(msg)"
        }
    }
}
