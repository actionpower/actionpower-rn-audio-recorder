//
//  Utils.swift
//  RNAudioRecorder
//
//  Created by Woochan Jeong on 2024/03/20.
//

import AVFoundation

struct Helper {
    static let reasonDescriptions: [AVAudioSession.RouteChangeReason: String] = [
        .newDeviceAvailable: "새로운 출력 장치가 사용 가능해짐 - 예: 헤드폰 연결",
        .oldDeviceUnavailable: "기존 출력 장치가 사용 불가능해짐 - 예: 헤드폰 연결 해제",
        .categoryChange: "오디오 세션 카테고리 변경",
        .override: "오디오 출력 경로가 오버라이드됨",
        .wakeFromSleep: "기기가 슬립 모드에서 깨어남",
        .noSuitableRouteForCategory: "현재 카테고리에 적합한 라우트가 없음",
        .routeConfigurationChange: "라우트 구성 변경",
        .unknown: "unknown"
    ]
    
    static let audioSessionErrorDescriptions: [AVAudioSession.ErrorCode: String] = [
        .badParam: "badParam",
        .cannotInterruptOthers: "cannotInterruptOthers",
        .cannotStartPlaying: "cannotStartPlaying",
        .cannotStartRecording: "cannotStartRecording",
        .expiredSession: "expiredSession",
        .incompatibleCategory: "incompatibleCategory",
        .insufficientPriority: "insufficientPriority",
        .isBusy: "isBusy",
        .mediaServicesFailed: "mediaServicesFailed",
        .missingEntitlement: "missingEntitlement",
        .none: "none",
        .resourceNotAvailable: "resourceNotAvailable",
        .sessionNotActive: "sessionNotActive",
        .siriIsRecording: "siriIsRecording",
        .unspecified: "unspecified"
    ]
    
    static func audioFormat(forEncoding encoding: String?) -> Int {
        switch encoding {
        case nil:
            return Int(kAudioFormatAppleLossless)
        case "lpcm", "ima4":
            return Int(kAudioFormatAppleIMA4)
        case "aac", "mp4":
            return Int(kAudioFormatMPEG4AAC)
        case "MAC3":
            return Int(kAudioFormatMACE3)
        case "MAC6":
            return Int(kAudioFormatMACE6)
        case "ulaw":
            return Int(kAudioFormatULaw)
        case "alaw":
            return Int(kAudioFormatALaw)
        case "mp1":
            return Int(kAudioFormatMPEGLayer1)
        case "mp2":
            return Int(kAudioFormatMPEGLayer2)
        case "alac":
            return Int(kAudioFormatAppleLossless)
        case "amr":
            return Int(kAudioFormatAMR)
        case "flac":
            if #available(iOS 11.0, *) {
                return Int(kAudioFormatFLAC)
            } else {
                return Int(kAudioFormatAppleLossless)
            }
        case "opus":
            return Int(kAudioFormatOpus)
        default:
            return Int(kAudioFormatAppleLossless)
        }
    }
    
    static func audioSessionMode(forMode mode: String?) -> AVAudioSession.Mode {
        switch mode {
        case "measurement":
            return .measurement
        case "gamechat":
            return .gameChat
        case "movieplayback":
            return .moviePlayback
        case "spokenaudio":
            return .spokenAudio
        case "videochat":
            return .videoChat
        case "videorecording":
            return .videoRecording
        case "voicechat":
            return .voiceChat
        case "voiceprompt":
            if #available(iOS 12.0, *) {
                return .voicePrompt
            } else {
                return .default
            }
        default:
            return .default
        }
    }
}


