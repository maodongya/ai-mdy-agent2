import Foundation

enum SseStream {
    /// Parses SSE `data:` lines from a byte stream into JSON strings.
    static func jsonLines(from bytes: URLSession.AsyncBytes) -> AsyncThrowingStream<String, Error> {
        AsyncThrowingStream { continuation in
            Task {
                do {
                    var buffer = ""
                    for try await byte in bytes {
                        let char = Character(UnicodeScalar(byte))
                        buffer.append(char)
                        while let range = buffer.range(of: "\n") {
                            let line = String(buffer[..<range.lowerBound])
                            buffer.removeSubrange(..<range.upperBound)
                            if line.hasPrefix("data:") {
                                let data = line.dropFirst(5).trimmingCharacters(in: .whitespaces)
                                if !data.isEmpty {
                                    continuation.yield(String(data))
                                }
                            }
                        }
                    }
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: error)
                }
            }
        }
    }
}
