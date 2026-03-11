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
    private var localVideoView = VideoView()
    private var remoteVideoView = VideoView()
    private weak var localContainer: UIView?
    private weak var remoteContainer: UIView?
    private var videoRequested = false
    private var microphoneEnabled = true
    private var speakerEnabled = false
    private var cameraEnabled = false
    private var endingLocally = false
    private var started = false

    func start() {
        guard !started else { return }
        started = true

        localVideoView.isHidden = false
        remoteVideoView.isHidden = false

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
        configureAudioSession()
        postState(status: "connecting")

        Task {
            await connect(roomName: roomName, token: token, wsUrl: wsUrl, videoEnabled: videoEnabled)
        }
    }

    private func connect(roomName: String, token: String, wsUrl: String, videoEnabled: Bool) async {
        await disconnectCurrentRoom(reportIdle: false)

        let room = Room(delegate: self)
        self.room = room

        do {
            try await room.connect(url: wsUrl, token: token)
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
        guard let container = notification.object as? UIView,
              let isLocal = notification.userInfo?["isLocal"] as? Bool else { return }

        if isLocal {
            localContainer = container
            attach(videoView: localVideoView, to: container)
        } else {
            remoteContainer = container
            attach(videoView: remoteVideoView, to: container)
        }
        refreshVideoBindings()
    }

    private func handleUnregisterVideoView(_ notification: Notification) {
        guard let container = notification.object as? UIView,
              let isLocal = notification.userInfo?["isLocal"] as? Bool else { return }

        if isLocal, localContainer === container {
            localVideoView.removeFromSuperview()
            localContainer = nil
        } else if !isLocal, remoteContainer === container {
            remoteVideoView.removeFromSuperview()
            remoteContainer = nil
        }
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
        localVideoView.track = currentLocalVideoTrack()
        remoteVideoView.track = currentRemoteVideoTrack()
        localVideoView.isHidden = localVideoView.track == nil
        remoteVideoView.isHidden = remoteVideoView.track == nil
        if let localContainer {
            attach(videoView: localVideoView, to: localContainer)
        }
        if let remoteContainer {
            attach(videoView: remoteVideoView, to: remoteContainer)
        }
    }

    private func currentLocalVideoTrack() -> VideoTrack? {
        room?.localParticipant.localVideoTracks
            .first(where: { $0.source == .camera && !$0.isMuted })?
            .track as? VideoTrack
    }

    private func currentRemoteVideoTrack() -> VideoTrack? {
        room?.remoteParticipants.values
            .compactMap { participant in
                participant.videoTracks
                    .first(where: { $0.source == .camera && !$0.isMuted && $0.isSubscribed })?
                    .track as? VideoTrack
            }
            .first
    }

    private func postState(status: String, reason: String? = nil) {
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
                "remoteVideoAvailable": currentRemoteVideoTrack() != nil,
            ]
        )
    }

    private func postEnded(reason: String) {
        postState(status: "ended", reason: reason)
    }

    private func disconnectCurrentRoom(reportIdle: Bool) async {
        if let room {
            await room.disconnect()
        }
        room = nil
        localVideoView.track = nil
        remoteVideoView.track = nil
        localVideoView.removeFromSuperview()
        remoteVideoView.removeFromSuperview()
        if let localContainer {
            attach(videoView: localVideoView, to: localContainer)
        }
        if let remoteContainer {
            attach(videoView: remoteVideoView, to: remoteContainer)
        }
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
        refreshVideoBindings()
        postState(status: "connected")
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
}
#else
final class ClickLiveKitBridge {
    static let shared = ClickLiveKitBridge()

    func start() {
    }
}
#endif