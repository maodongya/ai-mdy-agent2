import Foundation

enum ConsoleLineKind: String, Hashable {
    case system
    case message
    case tool
    case approval
    case error
}

struct ConsoleLine: Identifiable, Hashable {
    let id = UUID()
    let seq: Int
    let kind: ConsoleLineKind
    let text: String
    let timestamp: Date
}

@Observable
final class RunViewModel {
    var lines: [ConsoleLine] = []
    var isRunning = false
    var pendingApproval: ApprovalRequest?
    var mode: Mode = .agent
    var model: String = AppSettings.shared.defaultModel
    var inputText: String = ""
    var writtenPaths: [String] = []

    private var eventTask: Task<Void, Never>?

    private var client: AnvilClient { AppSettings.shared.client }

    @MainActor
    func submit(threadId: String) {
        let prompt = inputText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !prompt.isEmpty, !isRunning else { return }
        inputText = ""
        isRunning = true
        writtenPaths = []
        append(.system, seq: lines.count, text: "> \(prompt)")

        eventTask?.cancel()
        eventTask = Task { @MainActor in
            do {
                let run = try await client.startRun(
                    threadId: threadId,
                    mode: mode,
                    model: model,
                    prompt: prompt
                )
                append(.system, seq: lines.count, text: "run \(run.runId) [\(run.status)]")

                for try await event in client.events(runId: run.runId) {
                    handle(event)
                }
                append(.system, seq: lines.count, text: "--- run finished ---")
            } catch {
                append(.error, seq: lines.count, text: error.localizedDescription)
            }
            isRunning = false
        }
    }

    @MainActor
    func respondApproval(_ decision: ApprovalDecision) {
        guard let approval = pendingApproval else { return }
        pendingApproval = nil
        Task {
            do {
                try await client.respondApproval(id: approval.approvalId, decision: decision)
                await MainActor.run {
                    append(.approval, seq: lines.count, text: "approval \(approval.approvalId): \(decision.rawValue)")
                }
            } catch {
                await MainActor.run {
                    append(.error, seq: lines.count, text: error.localizedDescription)
                }
            }
        }
    }

    @MainActor
    func noteWrittenPaths(from preview: [String: JSONValue]) {
        if let paths = preview["paths"]?.arrayValue {
            for p in paths {
                if let s = p.stringValue {
                    writtenPaths.append(s)
                }
            }
        }
    }

    @MainActor
    private func handle(_ event: AnvilEvent) {
        switch event.type {
        case "message.completed":
            let text = event.payloadString("text") ?? "(empty message)"
            append(.message, seq: event.seq, text: text)
        case "tool.planned", "tool.started":
            let name = event.payloadString("name") ?? event.payload?["tool_call_id"]?.stringValue ?? "tool"
            append(.tool, seq: event.seq, text: "[\(event.type)] \(name)")
        case "tool.completed":
            append(.tool, seq: event.seq, text: "[tool.completed] ok")
        case "tool.failed":
            let msg = event.payloadObject("error")?["message"]?.stringValue ?? "failed"
            append(.error, seq: event.seq, text: "[tool.failed] \(msg)")
        case "approval.required":
            if let payload = event.payload {
                let id = payload["approval_id"]?.stringValue ?? ""
                let tool = payload["tool"]?.stringValue ?? ""
                let risk = payload["risk"]?.stringValue ?? ""
                let preview = payload["preview"]?.objectValue ?? [:]
                pendingApproval = ApprovalRequest(
                    approvalId: id,
                    tool: tool,
                    risk: risk,
                    preview: preview
                )
                append(.approval, seq: event.seq, text: "approval required: \(tool) (\(risk))")
            }
        case "run.completed", "run.failed", "run.cancelled":
            append(.system, seq: event.seq, text: event.type)
        default:
            append(.system, seq: event.seq, text: event.type)
        }
    }

    @MainActor
    private func append(_ kind: ConsoleLineKind, seq: Int, text: String) {
        lines.append(ConsoleLine(seq: seq, kind: kind, text: text, timestamp: Date()))
    }
}
