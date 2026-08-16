import XCTest
@testable import AnvilWorkbench

final class AnvilClientTests: XCTestCase {
    func testEventDecodesSnakeCaseFields() throws {
        let json = """
        {
          "protocol_version": "1.0",
          "thread_id": "thr_1",
          "run_id": "run_1",
          "seq": 0,
          "type": "run.started",
          "ts": "2026-08-13T00:00:00Z",
          "payload": {"mode": "agent", "model": "scripted:read-add"}
        }
        """
        let event = try JSONDecoder().decode(AnvilEvent.self, from: Data(json.utf8))
        XCTAssertEqual(event.threadId, "thr_1")
        XCTAssertEqual(event.runId, "run_1")
        XCTAssertEqual(event.type, "run.started")
        XCTAssertEqual(event.seq, 0)
    }

    func testModeWireValues() {
        XCTAssertEqual(Mode.agent.rawValue, "agent")
        XCTAssertEqual(ApprovalDecision.allowOnce.rawValue, "allow_once")
    }

    func testFileTreeBuilder() {
        let nodes = [
            WorkspaceNode(path: "src/main/Add.java", type: "file"),
            WorkspaceNode(path: "src/main", type: "dir"),
            WorkspaceNode(path: "README.md", type: "file")
        ]
        let roots = FileTreeBuilder.build(from: nodes)
        XCTAssertFalse(roots.isEmpty)
    }
}
