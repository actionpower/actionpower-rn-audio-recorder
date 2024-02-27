import Foundation
import AVFoundation

@objc(RNAudioRecorder)
class RNAudioRecorder: RCTEventEmitter, AVAudioRecorderDelegate {
    var subscriptionDuration: Double = 0.5
    var audioFileURL: URL?

    // Recorder
    var audioSession: AVAudioSession!
    var recordTimer: Timer?
    var audioRecorder = AVAudioRecorder()
    var _meteringEnabled: Bool = false
    var _isPausedByUser: Bool = false
    var _isInterrupted: Bool = false
    var _isPausedByInterrupt: Bool = false

    override static func requiresMainQueueSetup() -> Bool {
      return true
    }

    override func supportedEvents() -> [String]! {
        return ["rn-recordback"]
    }
    
    @objc
    func construct() {
        self.subscriptionDuration = 0.1
    }
    
    @objc(setSubscriptionDuration:)
    func setSubscriptionDuration(duration: Double) -> Void {
        subscriptionDuration = duration
    }

    func setAudioFileURL(path: String) {
        if (path == "DEFAULT") {
            let cachesDirectory = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first!
            audioFileURL = cachesDirectory.appendingPathComponent("sound.m4a")
        } else if (path.hasPrefix("http://") || path.hasPrefix("https://") || path.hasPrefix("file://")) {
            audioFileURL = URL(string: path)
        } else {
            let cachesDirectory = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first!
            audioFileURL = cachesDirectory.appendingPathComponent(path)
        }
    }
    
    
    @objc func handleAudioSessionInterruption(notification: Notification) {
        
        guard let userInfo = notification.userInfo,
              let interruptionTypeRawValue = userInfo[AVAudioSessionInterruptionTypeKey] as? UInt,
              let interruptionType = AVAudioSession.InterruptionType(rawValue: interruptionTypeRawValue) else {
            return
        }

        switch interruptionType {
        case .began:
            _isInterrupted = true
            if (!_isPausedByUser) {
//                recordTimer?.invalidate()
//                recordTimer = nil;
                _isPausedByInterrupt = true
                audioRecorder.pause()
                sendEvent(withName: "rn-recordback", body: ["status": "pausedByNative"])
            }
        case .ended:
            _isInterrupted = false
            if (_isPausedByInterrupt) {
                do {
                    try AVAudioSession.sharedInstance().setActive(true)
                    audioRecorder.record()
//                    if (recordTimer == nil) {
//                        startRecorderTimer()
//                    }
                    _isPausedByInterrupt = false
                    sendEvent(withName: "rn-recordback", body: ["status": "resumeByNative"])
                } catch {
                    print("Failed to activate audio session or resume recording.")
                }
            }
        @unknown default:
            break
        }
    }
    
    @objc(startRecorder:audioSets:meteringEnabled:resolve:reject:)
    func startRecorder(path: String,  audioSets: [String: Any], meteringEnabled: Bool, resolve: @escaping RCTPromiseResolveBlock,
       rejecter reject: @escaping RCTPromiseRejectBlock) -> Void {

        _meteringEnabled = meteringEnabled;

        let encoding = audioSets["AVFormatIDKeyIOS"] as? String
        let mode = audioSets["AVModeIOS"] as? String
        let avLPCMBitDepth = audioSets["AVLinearPCMBitDepthKeyIOS"] as? Int
        let avLPCMIsBigEndian = audioSets["AVLinearPCMIsBigEndianKeyIOS"] as? Bool
        let avLPCMIsFloatKey = audioSets["AVLinearPCMIsFloatKeyIOS"] as? Bool
        let avLPCMIsNonInterleaved = audioSets["AVLinearPCMIsNonInterleavedIOS"] as? Bool

        var avFormat: Int? = nil
        var avMode: AVAudioSession.Mode = AVAudioSession.Mode.default
        var sampleRate = audioSets["AVSampleRateKeyIOS"] as? Int
        var numberOfChannel = audioSets["AVNumberOfChannelsKeyIOS"] as? Int
        var audioQuality = audioSets["AVEncoderAudioQualityKeyIOS"] as? Int

        setAudioFileURL(path: path)

        if (sampleRate == nil) {
            sampleRate = 44100;
        }

        if (encoding == nil) {
            avFormat = Int(kAudioFormatAppleLossless)
        } else {
            if (encoding == "lpcm") {
                avFormat = Int(kAudioFormatAppleIMA4)
            } else if (encoding == "ima4") {
                avFormat = Int(kAudioFormatAppleIMA4)
            } else if (encoding == "aac") {
                avFormat = Int(kAudioFormatMPEG4AAC)
            } else if (encoding == "MAC3") {
                avFormat = Int(kAudioFormatMACE3)
            } else if (encoding == "MAC6") {
                avFormat = Int(kAudioFormatMACE6)
            } else if (encoding == "ulaw") {
                avFormat = Int(kAudioFormatULaw)
            } else if (encoding == "alaw") {
                avFormat = Int(kAudioFormatALaw)
            } else if (encoding == "mp1") {
                avFormat = Int(kAudioFormatMPEGLayer1)
            } else if (encoding == "mp2") {
                avFormat = Int(kAudioFormatMPEGLayer2)
            } else if (encoding == "mp4") {
                avFormat = Int(kAudioFormatMPEG4AAC)
            } else if (encoding == "alac") {
                avFormat = Int(kAudioFormatAppleLossless)
            } else if (encoding == "amr") {
                avFormat = Int(kAudioFormatAMR)
            } else if (encoding == "flac") {
                if #available(iOS 11.0, *) {
                    avFormat = Int(kAudioFormatFLAC)
                }
            } else if (encoding == "opus") {
                avFormat = Int(kAudioFormatOpus)
            }
        }

        if (mode == "measurement") {
            avMode = AVAudioSession.Mode.measurement
        } else if (mode == "gamechat") {
            avMode = AVAudioSession.Mode.gameChat
        } else if (mode == "movieplayback") {
            avMode = AVAudioSession.Mode.moviePlayback
        } else if (mode == "spokenaudio") {
            avMode = AVAudioSession.Mode.spokenAudio
        } else if (mode == "videochat") {
            avMode = AVAudioSession.Mode.videoChat
        } else if (mode == "videorecording") {
            avMode = AVAudioSession.Mode.videoRecording
        } else if (mode == "voicechat") {
            avMode = AVAudioSession.Mode.voiceChat
        } else if (mode == "voiceprompt") {
            if #available(iOS 12.0, *) {
                avMode = AVAudioSession.Mode.voicePrompt
            } else {
                // Fallback on earlier versions
            }
        }


        if (numberOfChannel == nil) {
            numberOfChannel = 2
        }

        if (audioQuality == nil) {
            audioQuality = AVAudioQuality.medium.rawValue
        }

        func startRecording() {
            let settings = [
                AVSampleRateKey: sampleRate!,
                AVFormatIDKey: avFormat!,
                AVNumberOfChannelsKey: numberOfChannel!,
                AVEncoderAudioQualityKey: audioQuality!,
                AVLinearPCMBitDepthKey: avLPCMBitDepth ?? AVLinearPCMBitDepthKey.count,
                AVLinearPCMIsBigEndianKey: avLPCMIsBigEndian ?? true,
                AVLinearPCMIsFloatKey: avLPCMIsFloatKey ?? false,
                AVLinearPCMIsNonInterleaved: avLPCMIsNonInterleaved ?? false
            ] as [String : Any]

            do {
                audioRecorder = try AVAudioRecorder(url: audioFileURL!, settings: settings)

                if (audioRecorder != nil) {
                    audioRecorder.prepareToRecord()
                    audioRecorder.delegate = self
                    audioRecorder.isMeteringEnabled = _meteringEnabled
                    let isRecordStarted = audioRecorder.record()

                    if !isRecordStarted {
                        reject("RNAudioPlayerRecorder", "Error occured during initiating recorder", nil)
                        return
                    }

                    startRecorderTimer()

                    resolve(audioFileURL?.absoluteString)
                    return
                }

                reject("RNAudioPlayerRecorder", "Error occured during initiating recorder", nil)
            } catch {
                reject("RNAudioPlayerRecorder", "Error occured during recording", nil)
            }
        }
        

        NotificationCenter.default.addObserver(self, selector: #selector(handleAudioSessionInterruption(notification:)), name: AVAudioSession.interruptionNotification, object: nil)
        audioSession = AVAudioSession.sharedInstance()

        do {
            try audioSession.setCategory(.playAndRecord, mode: avMode, options: [.duckOthers, .allowBluetooth, .allowBluetoothA2DP, .interruptSpokenAudioAndMixWithOthers])
            try audioSession.setActive(true)

            audioSession.requestRecordPermission { granted in
                DispatchQueue.main.async {
                    if granted {
                        startRecording()
                    } else {
                        reject("RNAudioPlayerRecorder", "Record permission not granted", nil)
                    }
                }
            }
        } catch {
            reject("RNAudioPlayerRecorder", "Failed to record", nil)
        }
    }
    
    @objc(stopRecorder:rejecter:)
    public func stopRecorder(
        resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
    ) -> Void {
        if (audioRecorder == nil) {
            reject("RNAudioPlayerRecorder", "Failed to stop recorder. It is already nil.", nil)
            return
        }

        audioRecorder.stop()

        if (recordTimer != nil) {
            recordTimer!.invalidate()
            recordTimer = nil
        }
        
        NotificationCenter.default.removeObserver(self, name: AVAudioSession.interruptionNotification, object: nil)


        resolve(audioFileURL?.absoluteString)
    }
    
    @objc(pauseRecorder:rejecter:)
    public func pauseRecorder(
        resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
    ) -> Void {
        if (audioRecorder == nil) {
            return reject("RNAudioPlayerRecorder", "Recorder is not recording", nil)
        }

        recordTimer?.invalidate()
        recordTimer = nil;
        
        _isPausedByUser = true

        audioRecorder.pause()
        resolve("Recorder paused!")
    }

    @objc(resumeRecorder:rejecter:)
    public func resumeRecorder(
        resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
    ) -> Void {
        if (audioRecorder == nil) {
            return reject("RNAudioPlayerRecorder", "Recorder is nil", nil)
        }
        
        if (_isPausedByInterrupt || _isInterrupted) {
            return reject("RNAudioPlayerRecorder", "Recorder is nil", nil)
        }
        

        audioRecorder.record()

        if (recordTimer == nil) {
            startRecorderTimer()
        }
        
        _isPausedByUser = false

        resolve("Recorder paused!")
    }
    
    @objc(updateRecorderProgress:)
    public func updateRecorderProgress(timer: Timer) -> Void {
        if (audioRecorder != nil) {
            var currentMetering: Float = 0

            if (_meteringEnabled) {
                audioRecorder.updateMeters()
                currentMetering = audioRecorder.averagePower(forChannel: 0)
            }
        
            let status = [
                "isRecording": audioRecorder.isRecording,
                "currentPosition": audioRecorder.currentTime * 1000,
                "currentMetering": currentMetering,
            ] as [String : Any];
            
            let inInputError = isOtherAudioPlaying == false && currentMetering == -120
            let inSessionError = isOtherAudioPlaying == false && currentMetering == -160
            
            if inInputError || inSessionError {
                sendEvent(withName: "rn-recordback", body: ["status": "resumeByNative"])
            }
            
            sendEvent(withName: "rn-recordback", body: status)
        }
    }

    @objc(startRecorderTimer)
    func startRecorderTimer() -> Void {
        DispatchQueue.main.async {
            self.recordTimer = Timer.scheduledTimer(
                timeInterval: self.subscriptionDuration,
                target: self,
                selector: #selector(self.updateRecorderProgress),
                userInfo: nil,
                repeats: true
            )
        }
    }

    func audioRecorderDidFinishRecording(_ recorder: AVAudioRecorder, successfully flag: Bool) {
        if !flag {
            print("Failed to stop recorder")
        }
    }
}
