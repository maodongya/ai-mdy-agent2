import Foundation
import SwiftUI

@Observable
final class AppSettings {
    static let shared = AppSettings()

    var serverURLString: String {
        didSet { UserDefaults.standard.set(serverURLString, forKey: Keys.serverURL) }
    }

    var workspacePath: String {
        didSet { UserDefaults.standard.set(workspacePath, forKey: Keys.workspacePath) }
    }

    var defaultModel: String {
        didSet { UserDefaults.standard.set(defaultModel, forKey: Keys.defaultModel) }
    }

    var isConnected: Bool = false
    var protocolVersion: String = ""
    var lastError: String?

    var serverURL: URL {
        URL(string: serverURLString) ?? URL(string: "http://127.0.0.1:7788")!
    }

    var client: AnvilClient {
        AnvilClient(baseURL: serverURL)
    }

    private enum Keys {
        static let serverURL = "anvil.serverURL"
        static let workspacePath = "anvil.workspacePath"
        static let defaultModel = "anvil.defaultModel"
    }

    private init() {
        serverURLString = UserDefaults.standard.string(forKey: Keys.serverURL) ?? "http://127.0.0.1:7788"
        workspacePath = UserDefaults.standard.string(forKey: Keys.workspacePath)
            ?? FileManager.default.homeDirectoryForCurrentUser.path + "/Documents"
        defaultModel = UserDefaults.standard.string(forKey: Keys.defaultModel) ?? "scripted:read-add"
    }

    @MainActor
    func checkHealth() async {
        do {
            let health = try await client.health()
            isConnected = health.ok
            protocolVersion = health.protocolVersion
            lastError = nil
        } catch {
            isConnected = false
            lastError = error.localizedDescription
        }
    }
}
