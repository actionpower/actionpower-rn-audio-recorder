import Foundation
import AVFoundation
import CallKit

@objc(RNAudioRecorder)
class RNAudioRecorder: RCTEventEmitter, AVAudioRecorderDelegate {
    var subscriptionDuration: Double = 0.5
    var audioFileURL: URL?
    
    var callObserver: CXCallObserver?
    
    var audioSession: AVAudioSession!
    var audioRecorder: AVAudioRecorder!
    var recordTimer: Timer?
    var _meteringEnabled: Bool = false
    
    var _isPausedByUser: Bool = false
    var _isInterrupted: Bool = false
    var _isPausedByInterrupt: Bool = false
    var _isFailResumeByNative: Bool = false
    var _isCalled: Bool = false
    
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
    
    // RN에서 RNAudioRecorder가 Singleton으로 동작되기 때문에 녹음이 끝났을 때 전역변수들을 직접 초기화시켜주기 위한 함수
    func initialize() {
        if (recordTimer != nil) {
            recordTimer!.invalidate()
            recordTimer = nil
        }
        
        // 녹음 후 보드에서 녹음된 음성 파일 재생 시 사운드 출력이 작아지는 문제 Fix
        // Category를 .playAndRecord으로 남아 있으면, 사운드 출력이 돌아오지 않음
        do {
            try audioSession.setCategory(.playback)
        } catch {
            print("DEBUG : \(error.localizedDescription)")
        }
        
        _isPausedByUser = false
        _isInterrupted = false
        _isPausedByInterrupt = false
        _isFailResumeByNative = false
        _isCalled = false
        
        
        callObserver = nil
        NotificationCenter.default.removeObserver(self, name: AVAudioSession.interruptionNotification, object: nil)
        NotificationCenter.default.removeObserver(self, name: AVAudioSession.routeChangeNotification, object: nil)
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
            print("DEBUG: [interruption began]")
            _isInterrupted = true
            if (!_isPausedByUser) {
                recordTimer?.invalidate()
                recordTimer = nil;
                _isPausedByInterrupt = true
                audioRecorder.pause()
                sendEvent(withName: "rn-recordback", body: ["status": "pausedByNative"])
            }
        case .ended:
            print("DEBUG: [interruption ended]")
            _isInterrupted = false
            if (_isPausedByInterrupt) {
                if (recordTimer == nil) {
                    startRecorderTimer()
                }
                _isPausedByInterrupt = false
                if let optionsRawValue = userInfo[AVAudioSessionInterruptionOptionKey] as? UInt {
                    let options = AVAudioSession.InterruptionOptions(rawValue: optionsRawValue)
                    if options.contains(.shouldResume) {
                        audioRecorder.record()
                        sendEvent(withName: "rn-recordback", body: ["status": "resumeByNative"])
                    } else {
                        // voip 전화인 경우 interrupt가 시작되자마자 전화가 끊기지도 않았는데 interrupt가 end되는 버그 발생
                        // voip 전화로 인해 interrupt가 지속되고 있음을 변수를 통해 관리
                        // call observer의 hasEnded 이벤트에서 처리됨
                        _isFailResumeByNative = true
                    }
                }
            }
        @unknown default:
            print("DEBUG: @unknown default 인터럽트")
        }
    }
    
    @objc(startRecorder:audioSets:meteringEnabled:resolve:reject:)
    func startRecorder(path: String, audioSets: [String: Any], meteringEnabled: Bool, resolve: @escaping RCTPromiseResolveBlock,
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
        
        avFormat = Helper.audioFormat(forEncoding: encoding)
        avMode = Helper.audioSessionMode(forMode: mode)
        
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
        
        self.callObserver = CXCallObserver()
        self.callObserver?.setDelegate(self, queue: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(handleAudioSessionInterruption(notification:)), name: AVAudioSession.interruptionNotification, object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(controlRouteChange), name: AVAudioSession.routeChangeNotification, object: nil)
        audioSession = AVAudioSession.sharedInstance()
        
        do {
            try audioSession.setCategory(.playAndRecord, mode: avMode, options: [.duckOthers, .allowBluetoothA2DP, .interruptSpokenAudioAndMixWithOthers])
            try audioSession.setActive(true, options: .notifyOthersOnDeactivation)
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
            
            let isCurrentTimeError = audioRecorder.currentTime < 0
            
            // 녹음 재개 버튼 클릭과 동시에 siri 실행 시 currentTime이 음수로 나타나는 현상 Fix
            // 일시 정지 후 다시 재개하면 정상적으로 녹음 가능
            if isCurrentTimeError {
                if audioRecorder.isRecording {
                    audioRecorder.pause()
                    sendEvent(withName: "rn-recordback", body: "failResumeByNative")
                }
            }
            
            if audioRecorder.isRecording {
                sendEvent(withName: "rn-recordback", body: status)
            }
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
        // 일시 정지 상태에서 Interrupt 이벤트 받지 않도록 Fix
        // 일시 정지 상태에서 Interrupt 수신할 경우, pause 중복으로 파일 유실될 수 있음
        // 보드에 진입했다가 일시정지를 누른상태에서 interrupt ended 이벤트를 받을 수 없음
        controlSessionActivation(false)
        
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
        
        if (_isFailResumeByNative) {
            return reject("RNAudioPlayerRecorder", "voip", nil)
        }
        
        if (_isCalled) {
            return reject("RNAudioPlayerRecorder", "call", nil)
        }
        
        // 녹음 덮어쓰기가 발생하지 않도록 Fix
        // record  중복으로 파일이 유실될 수 있음
        if audioRecorder.isRecording {
            audioRecorder.pause()
            sleep(1)
            audioRecorder.record()
        } else {
            audioRecorder.record()
        }
        
        if (recordTimer == nil) {
            startRecorderTimer()
        }
        
        _isPausedByUser = false
        
        resolve("Recorder paused!")
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
        initialize()
        
        resolve(audioFileURL?.absoluteString)
    }
    
    func controlSessionActivation(_ active: Bool) {
        do {
            try audioSession.setActive(active)
        } catch let error as NSError {
            if let errorCode = AVAudioSession.ErrorCode(rawValue: error.code) {
                if let description = Helper.audioSessionErrorDescriptions[errorCode] {
                    print("DEBUG: AVAudioSession ErrorCode - \(description)")
                } else {
                    fatalError()
                }
            }
        }
    }
    
    @objc func controlRouteChange(notification: Notification) {
        guard let userInfo = notification.userInfo,
              let reasonValue = userInfo[AVAudioSessionRouteChangeReasonKey] as? UInt,
              let reason = AVAudioSession.RouteChangeReason(rawValue: reasonValue) else {
            return
        }
        
        if let description = Helper.reasonDescriptions[reason] {
            print("DEBUG: route change reason: \(description)")
        } else {
            print("DEBUG: route change reason: 알 수 없는 라우트 변경")
        }
    }
}

// MARK: - CallObserver
// 일반적인 전화는 audio session interrupt handler에서 모두 처리되지만
// slack hudle과 같은 voip 전화는 interrupt handler에서 정확한처리가 불가
// 이를 처리하기 위해 추가
extension RNAudioRecorder: CXCallObserverDelegate {
    func changeIsCallEvent() {
        if (!_isCalled) {
            _isCalled = true
        }
    }
    
    func callObserver(_ callObserver: CXCallObserver, callChanged call: CXCall) {
        if call.hasEnded {
            print("DEBUG: CallKitEvent - hasEnded")
            _isCalled = false
            if (_isFailResumeByNative) {
                if (!_isPausedByUser) {
                    sendEvent(withName: "rn-recordback", body: ["status": "failResumeByNative"])
                }
                _isFailResumeByNative = false
            }
        }
        else if call.isOnHold {
            print("DEBUG: CallKitEvent - isOnHold")
            changeIsCallEvent()
        }
        else if call.isOutgoing {
            print("DEBUG: CallKitEvent - isOutgoing")
            changeIsCallEvent()
        }
        else if call.hasConnected {
            print("DEBUG: CallKitEvent - hasConnected")
            changeIsCallEvent()
        }
        else if !call.isOutgoing && !call.hasConnected && !call.hasEnded {
            print("DEBUG: CallKitEvent - incoming")
            changeIsCallEvent()
        } else {
            print("DEBUG: CallKitEvent - missed")
            _isCalled = false
        }
    }
}
