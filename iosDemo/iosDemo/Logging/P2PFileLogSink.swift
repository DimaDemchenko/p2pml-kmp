import Foundation
import P2PML
import UIKit

/// Persists library logs to a per-session file in Documents/p2pml-logs so they can be
/// pulled off the device later (Files app, Finder device pane, or the share sheet).
/// Chains in front of the library's default sink, so the Xcode console keeps working.
enum P2PFileLogSink {
    private static let logDirName = "p2pml-logs"
    private static let maxSessionFiles = 5

    private static let writeQueue = DispatchQueue(label: "com.novage.p2pml.demo.logsink")
    private static var fileHandle: FileHandle?

    private(set) static var currentLogURL: URL?

    private static let lineTimeFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm:ss.SSS"
        return formatter
    }()

    static func install() {
        guard currentLogURL == nil, let url = createSessionFile() else { return }
        currentLogURL = url

        let device = UIDevice.current
        let sessionHeader = "--- P2PML demo session, \(device.model), iOS \(device.systemVersion) ---"
        // Queued before the sink is chained, so the serial queue guarantees the
        // handle exists by the time the first writeEntry task runs.
        writeQueue.async {
            fileHandle = try? FileHandle(forWritingTo: url)
            appendLine(sessionHeader)
        }

        let platformSink = P2PLogging.shared.sink
        P2PLogging.shared.sink = ChainedSink(platformSink: platformSink)
    }

    fileprivate static func writeEntry(level: LogLevel, tag: String, message: String, throwable: KotlinThrowable?) {
        let timestamp = Date()
        writeQueue.async {
            let time = lineTimeFormatter.string(from: timestamp)
            let shortTag = tag.replacingOccurrences(of: "P2PML~", with: "")
            var line = "\(time) \(level.name.prefix(1)) [\(shortTag)] \(message)"
            if let throwable {
                line += "\n\(throwable.description)"
            }
            appendLine(line)
        }
    }

    private static func appendLine(_ line: String) {
        guard let data = (line + "\n").data(using: .utf8) else { return }
        // The throwing FileHandle APIs: the legacy ones raise uncatchable
        // NSExceptions if the file disappears (it is user-deletable via the Files app).
        _ = try? fileHandle?.seekToEnd()
        try? fileHandle?.write(contentsOf: data)
    }

    private static func createSessionFile() -> URL? {
        let fileManager = FileManager.default
        guard let documents = fileManager.urls(for: .documentDirectory, in: .userDomainMask).first else {
            return nil
        }

        let dir = documents.appendingPathComponent(logDirName, isDirectory: true)
        try? fileManager.createDirectory(at: dir, withIntermediateDirectories: true)

        // Timestamped names sort lexicographically — prune all but the newest sessions.
        if let existing = try? fileManager.contentsOfDirectory(at: dir, includingPropertiesForKeys: nil) {
            existing
                .sorted { $0.lastPathComponent > $1.lastPathComponent }
                .dropFirst(maxSessionFiles - 1)
                .forEach { try? fileManager.removeItem(at: $0) }
        }

        let sessionFormatter = DateFormatter()
        sessionFormatter.dateFormat = "yyyy-MM-dd_HH-mm-ss"
        let url = dir.appendingPathComponent("p2pml-\(sessionFormatter.string(from: Date())).log")
        fileManager.createFile(atPath: url.path, contents: nil)
        return url
    }
}

private final class ChainedSink: NSObject, P2PLogger {
    private let platformSink: P2PLogger?

    init(platformSink: P2PLogger?) {
        self.platformSink = platformSink
    }

    func log(level: LogLevel, tag: String, message: String, throwable: KotlinThrowable?) {
        platformSink?.log(level: level, tag: tag, message: message, throwable: throwable)
        P2PFileLogSink.writeEntry(level: level, tag: tag, message: message, throwable: throwable)
    }
}
