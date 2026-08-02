import Foundation

#if canImport(UIKit) && canImport(LiveKit) && canImport(AVFAudio)
import UIKit
import LiveKit
import AVFAudio

private enum ClickCallNotifications {
    static let start = Notification.Name("ClickCallStart")
    static let end = Notification.Name("ClickCallEnd")
    static let setMicrophone = Notification.Name("ClickCallSetMicrophone")
    static let setSpeaker = Notification.Name("ClickCallSetSpeaker")
    static let setCamera = Notification.Name("ClickCallSetCamera")
    static let stateDidChange = Notification.Name("ClickCallStateDidChange")
    static let registerVideoView = Notification.Name("ClickCallRegisterVideoView")
    static let unregisterVideoView = Notification.Name("ClickCallUnregisterVideoView")
}

@MainActor
final class ClickLiveKitBridge: NSObject, @preconcurrency RoomDelegate {
    static let shared = ClickLiveKitBridge()

    private var observers: [NSObjectProtocol] = []
    private var room: Room?
    /// participantId → VideoView used for rendering that participant's camera track.
    private var videoViews: [String: VideoView] = [:]
    /// participantId → Compose/UIKit container hosting the VideoView.
    private let containers: NSMapTable<NSString, UIView> = NSMapTable.strongToWeakObjects()
    private var mirrorFlags: [String: Bool] = [:]
    private var videoRequested = false
    private var microphoneEnabled = true
    private var speakerEnabled = false
    private var cameraEnabled = false
    private var endingLocally = false
    private var hadRemoteParticipant = false
    private var started = false
    private var activeSpeakerIdentities: Set<String> = []

    func start() {
        guard !started else { return }
        started = true

        let center = NotificationCenter.default
        observers.append(center.addObserver(forName: ClickCallNotifications.start, object: nil, queue: .main) { [weak self] notification in
            Task { @MainActor in
                self?.handleStart(notification)
            }
        })
        observers.append(center.addObserver(forName: ClickCallNotifications.end, object: nil, queue: .main) { [weak self] _ in
            Task { @MainActor in
                self?.handleEnd()
            }
        })
        observers.append(center.addObserver(forName: ClickCallNotifications.setMicrophone, object: nil, queue: .main) { [weak self] notification in
            Task { @MainActor in
                self?.handleSetMicrophone(notification)
            }
        })
        observers.append(center.addObserver(forName: ClickCallNotifications.setSpeaker, object: nil, queue: .main) { [weak self] notification in
            Task { @MainActor in
                self?.handleSetSpeaker(notification)
            }
        })
        observers.append(center.addObserver(forName: ClickCallNotifications.setCamera, object: nil, queue: .main) { [weak self] notification in
            Task { @MainActor in
                self?.handleSetCamera(notification)
            }
        })
        observers.append(center.addObserver(forName: ClickCallNotifications.registerVideoView, object: nil, queue: .main) { [weak self] notification in
            Task { @MainActor in
                self?.handleRegisterVideoView(notification)
            }
        })
        observers.append(center.addObserver(forName: ClickCallNotifications.unregisterVideoView, object: nil, queue: .main) { [weak self] notification in
            Task { @MainActor in
                self?.handleUnregisterVideoView(notification)
            }
        })
    }

    private func handleStart(_ notification: Notification) {
        guard let userInfo = notification.userInfo,
              let roomName = userInfo["roomName"] as? String,
              let token = userInfo["token"] as? String,
              let wsUrl = userInfo["wsUrl"] as? String,
              let videoEnabled = userInfo["videoEnabled"] as? Bool else {
            postEnded(reason: "Missing call configuration")
            return
        }

        videoRequested = videoEnabled
        microphoneEnabled = true
        speakerEnabled = videoEnabled
        cameraEnabled = videoEnabled
        endingLocally = false
        hadRemoteParticipant = false
        activeSpeakerIdentities = []
        configureAudioSession()
        postState(status: "connecting")

        Task {
            await connect(roomName: roomName, token: token, wsUrl: wsUrl, videoEnabled: videoEnabled)
        }
    }

    private func connect(roomName: String, token: String, wsUrl: String, videoEnabled: Bool) async {
        await disconnectCurrentRoom(reportIdle: false)

        // Match Android: disable adaptiveStream/dynacast so Compose/UIKit visibility cannot pause layers.
        let room = Room(
            delegate: self,
            roomOptions: RoomOptions(adaptiveStream: false, dynacast: false)
        )
        self.room = room

        do {
            try await room.connect(url: wsUrl, token: token)
            // Serialize mic → camera (same as Android) for reliable cross-platform publish.
            try await room.localParticipant.setMicrophone(enabled: true)
            if videoEnabled {
                try await room.localParticipant.setCamera(enabled: true)
            }
            refreshVideoBindings()
            postState(status: "connected")
        } catch {
            await disconnectCurrentRoom(reportIdle: false)
            postEnded(reason: error.localizedDescription)
        }
    }

    private func handleEnd() {
        endingLocally = true
        Task {
            await disconnectCurrentRoom(reportIdle: true)
        }
    }

    private func handleSetMicrophone(_ notification: Notification) {
        guard let enabled = notification.userInfo?["enabled"] as? Bool,
              let room else { return }

        Task {
            do {
                try await room.localParticipant.setMicrophone(enabled: enabled)
                await MainActor.run {
                    self.microphoneEnabled = enabled
                    self.postState(status: "connected")
                }
            } catch {
                await MainActor.run {
                    self.postEnded(reason: error.localizedDescription)
                }
            }
        }
    }

    private func handleSetSpeaker(_ notification: Notification) {
        guard let enabled = notification.userInfo?["enabled"] as? Bool else { return }
        speakerEnabled = enabled
        configureAudioSession()
        postState(status: room == nil ? "connecting" : "connected")
    }

    private func handleSetCamera(_ notification: Notification) {
        guard let enabled = notification.userInfo?["enabled"] as? Bool,
              let room else { return }

        Task {
            do {
                try await room.localParticipant.setCamera(enabled: enabled)
                await MainActor.run {
                    self.cameraEnabled = enabled
                    self.videoRequested = self.videoRequested || enabled
                    self.refreshVideoBindings()
                    self.postState(status: "connected")
                }
            } catch {
                await MainActor.run {
                    self.postEnded(reason: error.localizedDescription)
                }
            }
        }
    }

    private func handleRegisterVideoView(_ notification: Notification) {
        guard let container = notification.object as? UIView else { return }
        let participantId = (notification.userInfo?["participantId"] as? String)
            ?? legacyParticipantId(isLocal: notification.userInfo?["isLocal"] as? Bool)
        guard let participantId, !participantId.isEmpty else { return }

        if let mirror = notification.userInfo?["mirror"] as? Bool {
            mirrorFlags[participantId] = mirror
        }
        containers.setObject(container, forKey: participantId as NSString)
        let videoView = videoViews[participantId] ?? VideoView()
        videoViews[participantId] = videoView
        videoView.mirrorMode = (mirrorFlags[participantId] == true) ? .mirror : .auto
        attach(videoView: videoView, to: container)
        refreshVideoBindings()
    }

    private func handleUnregisterVideoView(_ notification: Notification) {
        guard let container = notification.object as? UIView else { return }
        let participantId = (notification.userInfo?["participantId"] as? String)
            ?? legacyParticipantId(isLocal: notification.userInfo?["isLocal"] as? Bool)
        guard let participantId else { return }

        if containers.object(forKey: participantId as NSString) === container {
            videoViews[participantId]?.removeFromSuperview()
            containers.removeObject(forKey: participantId as NSString)
        }
    }

    /// Backward-compatible path if Kotlin still sends isLocal without participantId.
    private func legacyParticipantId(isLocal: Bool?) -> String? {
        guard let isLocal else { return nil }
        if isLocal {
            return room?.localParticipant.identity?.stringValue ?? "local"
        }
        return room?.remoteParticipants.values.first?.identity?.stringValue
    }

    private func attach(videoView: VideoView, to container: UIView) {
        if videoView.superview !== container {
            videoView.removeFromSuperview()
            videoView.frame = container.bounds
            videoView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
            container.addSubview(videoView)
        } else {
            videoView.frame = container.bounds
        }
    }

    private func refreshVideoBindings() {
        guard let room else {
            for view in videoViews.values {
                view.track = nil
                view.isHidden = true
            }
            return
        }

        let localId = room.localParticipant.identity?.stringValue ?? "local"
        let trackById = currentVideoTracksByParticipantId(room: room)

        for (participantId, videoView) in videoViews {
            let track = trackById[participantId]
            videoView.track = track
            videoView.isHidden = track == nil
            videoView.mirrorMode = (mirrorFlags[participantId] == true) ? .mirror : .auto
            if let container = containers.object(forKey: participantId as NSString) {
                attach(videoView: videoView, to: container)
            }
        }

        // Ensure we have views ready for known participants even before Compose registers.
        _ = localId
    }

    private func currentVideoTracksByParticipantId(room: Room) -> [String: VideoTrack] {
        var result: [String: VideoTrack] = [:]
        let localId = room.localParticipant.identity?.stringValue ?? "local"
        if let localTrack = currentLocalVideoTrack() {
            result[localId] = localTrack
        }
        for participant in room.remoteParticipants.values {
            guard let id = participant.identity?.stringValue else { continue }
            if let track = videoTrack(for: participant) {
                result[id] = track
            }
        }
        return result
    }

    private func currentLocalVideoTrack() -> VideoTrack? {
        room?.localParticipant.localVideoTracks
            .first(where: { $0.source == .camera && !$0.isMuted })?
            .track as? VideoTrack
    }

    private func videoTrack(for participant: RemoteParticipant) -> VideoTrack? {
        if let camera = participant.videoTracks
            .first(where: { $0.source == .camera && !$0.isMuted && $0.isSubscribed })?
            .track as? VideoTrack {
            return camera
        }
        return participant.videoTracks
            .first(where: { !$0.isMuted && $0.isSubscribed })?
            .track as? VideoTrack
    }

    private func buildParticipantsPayload() -> [[String: Any]] {
        guard let room else { return [] }
        var rows: [[String: Any]] = []
        let local = room.localParticipant
        let localId = local.identity?.stringValue ?? "local"
        let localCam = local.localVideoTracks.first(where: { $0.source == .camera })
        let localMicMuted = local.localAudioTracks.first(where: { $0.source == .microphone })?.isMuted ?? !microphoneEnabled
        rows.append([
            "identity": localId,
            "displayName": local.name?.isEmpty == false ? (local.name ?? "You") : "You",
            "isLocal": true,
            "isMuted": localMicMuted || !microphoneEnabled,
            "isSpeaking": activeSpeakerIdentities.contains(localId),
            "cameraEnabled": cameraEnabled && localCam != nil && !(localCam?.isMuted ?? true),
            "hasVideo": currentLocalVideoTrack() != nil,
        ])
        for remote in room.remoteParticipants.values {
            guard let id = remote.identity?.stringValue else { continue }
            let micMuted = remote.audioTracks.first(where: { $0.source == .microphone })?.isMuted ?? false
            let hasVideo = videoTrack(for: remote) != nil
            let name = (remote.name?.isEmpty == false) ? (remote.name ?? id) : id
            rows.append([
                "identity": id,
                "displayName": name,
                "isLocal": false,
                "isMuted": micMuted,
                "isSpeaking": activeSpeakerIdentities.contains(id),
                "cameraEnabled": hasVideo,
                "hasVideo": hasVideo,
            ])
        }
        return rows
    }

    private func postState(status: String, reason: String? = nil) {
        if !(room?.remoteParticipants.isEmpty ?? true) {
            hadRemoteParticipant = true
        }
        let tracks = room.map { currentVideoTracksByParticipantId(room: $0) } ?? [:]
        let anyRemoteVideo = tracks.contains { key, _ in
            key != (room?.localParticipant.identity?.stringValue ?? "local")
        }
        NotificationCenter.default.post(
            name: ClickCallNotifications.stateDidChange,
            object: nil,
            userInfo: [
                "status": status,
                "reason": reason as Any,
                "videoRequested": videoRequested,
                "microphoneEnabled": microphoneEnabled,
                "speakerEnabled": speakerEnabled,
                "cameraEnabled": cameraEnabled,
                "localVideoAvailable": currentLocalVideoTrack() != nil,
                "remoteVideoAvailable": anyRemoteVideo,
                "hasRemoteParticipant": !(room?.remoteParticipants.isEmpty ?? true),
                "participants": buildParticipantsPayload(),
            ]
        )
    }

    private func postEnded(reason: String) {
        postState(status: "ended", reason: reason)
    }

    private func disconnectCurrentRoom(reportIdle: Bool) async {
        for view in videoViews.values {
            view.track = nil
            view.removeFromSuperview()
        }
        if let room {
            await room.disconnect()
        }
        room = nil
        hadRemoteParticipant = false
        activeSpeakerIdentities = []
        cameraEnabled = false
        microphoneEnabled = true
        speakerEnabled = false
        deactivateAudioSession()
        if reportIdle {
            postState(status: "idle")
        }
    }

    private func configureAudioSession() {
        let audioSession = AVAudioSession.sharedInstance()
        do {
            let options: AVAudioSession.CategoryOptions = speakerEnabled || videoRequested
                ? [.allowBluetooth, .allowBluetoothA2DP, .defaultToSpeaker]
                : [.allowBluetooth, .allowBluetoothA2DP]
            try audioSession.setCategory(.playAndRecord, mode: videoRequested ? .videoChat : .voiceChat, options: options)
            try audioSession.setActive(true)
            try audioSession.overrideOutputAudioPort(speakerEnabled || videoRequested ? .speaker : .none)
        } catch {
            print("ClickLiveKitBridge audio session configuration failed: \(error.localizedDescription)")
        }
    }

    private func deactivateAudioSession() {
        do {
            try AVAudioSession.sharedInstance().setActive(false)
        } catch {
            print("ClickLiveKitBridge audio session deactivation failed: \(error.localizedDescription)")
        }
    }

    func roomDidConnect(_ room: Room) {
        refreshVideoBindings()
        postState(status: "connected")
    }

    func room(_ room: Room, didFailToConnectWithError error: LiveKitError?) {
        postEnded(reason: error?.localizedDescription ?? "Failed to connect call")
    }

    func room(_ room: Room, didDisconnectWithError error: LiveKitError?) {
        let reason = error?.localizedDescription ?? "Call ended"
        let endedByLocalUser = endingLocally
        endingLocally = false

        Task { @MainActor in
            await disconnectCurrentRoom(reportIdle: endedByLocalUser)
            if !endedByLocalUser {
                self.postEnded(reason: reason)
            }
        }
    }

    func room(_ room: Room, participantDidConnect participant: RemoteParticipant) {
        refreshVideoBindings()
        postState(status: "connected")
    }

    func room(_ room: Room, participantDidDisconnect participant: RemoteParticipant) {
        if let id = participant.identity?.stringValue {
            videoViews[id]?.track = nil
        }
        refreshVideoBindings()
        if hadRemoteParticipant && room.remoteParticipants.isEmpty {
            Task { @MainActor in
                await disconnectCurrentRoom(reportIdle: false)
                postEnded(reason: "Call ended")
            }
        } else {
            postState(status: "connected")
        }
    }

    func room(_ room: Room, participant: RemoteParticipant, didSubscribeTrack publication: RemoteTrackPublication) {
        refreshVideoBindings()
        postState(status: "connected")
    }

    func room(_ room: Room, participant: RemoteParticipant, didUnsubscribeTrack publication: RemoteTrackPublication) {
        refreshVideoBindings()
        postState(status: "connected")
    }

    func room(_ room: Room, participant: LocalParticipant, didPublishTrack publication: LocalTrackPublication) {
        refreshVideoBindings()
        postState(status: "connected")
    }

    func room(_ room: Room, participant: LocalParticipant, didUnpublishTrack publication: LocalTrackPublication) {
        refreshVideoBindings()
        postState(status: "connected")
    }

    func room(_ room: Room, didUpdateSpeakingParticipants speakingParticipants: [Participant]) {
        activeSpeakerIdentities = Set(speakingParticipants.compactMap { $0.identity?.stringValue })
        postState(status: "connected")
    }
}
#else
final class ClickLiveKitBridge {
    static let shared = ClickLiveKitBridge()

    func start() {
    }
}
#endif
