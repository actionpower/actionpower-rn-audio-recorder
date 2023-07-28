# @actionpower/rn-audio-recorder

[![supports iOS](https://img.shields.io/badge/iOS-4630EB.svg?style=flat-square&logo=APPLE&labelColor=999999&logoColor=fff)](https://itunes.apple.com/app/apple-store/id982107779)
[![supports Android](https://img.shields.io/badge/Android-4630EB.svg?style=flat-square&logo=ANDROID&labelColor=A4C639&logoColor=fff)](https://play.google.com/store/apps/details?id=host.exp.exponent&referrer=www)
[![code style: prettier](https://img.shields.io/badge/code_style-prettier-ff69b4.svg?style=flat-square)](https://github.com/prettier/prettier)

This is a react-native link module for audio recorder. This is not a playlist audio module and this library provides simple recorder functionalities for both `android` and `ios` platforms. This only supports default file extension for each platform. This module can also handle file from url.

## Getting started

`$  yarn add --registry http://10.0.1.16:4873 @actionpower/rn-audio-recorder`

## Installation

#### Using React Native >= 0.61

[iOS only]

```sh
npx pod-install
```

#### Using React Native < 0.60

`$ react-native link react-native-audio-recorder-player`

### Manual installation

#### iOS

1. In XCode, in the project navigator, right click `Libraries` ➜ `Add Files to [your project's name]`
2. Go to `node_modules` ➜ `react-native-audio-recorder-player` and add `RNAudioRecorderPlayer.xcodeproj`
3. In XCode, in the project navigator, select your project. Add `libRNAudioRecorderPlayer.a` to your project's `Build Phases` ➜ `Link Binary With Libraries`
4. Run your project (`Cmd+R`)<

#### Android

1. Open up `android/app/src/main/java/[...]/MainApplication.java`

- Add `import package com.dooboolab.audiorecorderplayer.RNAudioRecorderPlayerPackage;` to the imports at the top of the file
- Add `new RNAudioRecorderPlayerPackage()` to the list returned by the `getPackages()` method

2. Append the following lines to `android/settings.gradle`:
   ```
   include ':react-native-audio-recorder-player'
   project(':react-native-audio-recorder-player').projectDir = new File(rootProject.projectDir, 	'../node_modules/react-native-audio-recorder-player/android')
   ```
3. Insert the following lines inside the dependencies block in `android/app/build.gradle`:
   ```
     compile project(':react-native-audio-recorder-player')
   ```

## Post installation

#### iOS

On _iOS_ you need to add a usage description to `Info.plist`:

```xml
<key>NSMicrophoneUsageDescription</key>
<string>Give $(PRODUCT_NAME) permission to use your microphone. Your record wont be shared without your permission.</string>
```

Also, add [swift bridging header](https://stackoverflow.com/questions/31716413/xcode-not-automatically-creating-bridging-header) if you haven't created one for `swift` compatibility.

<img width="800" alt="1" src="https://user-images.githubusercontent.com/27461460/111863065-8be6e300-899c-11eb-8ad8-6811e0bd0fbd.png">

#### Android

On _Android_ you need to add a permission to `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
```

Also, android above `Marshmallow` needs runtime permission to record audio. Using [react-native-permissions](https://github.com/yonahforst/react-native-permissions) will help you out with this problem. Below is sample usage before when before staring the recording.

```ts
if (Platform.OS === 'android') {
  try {
    const grants = await PermissionsAndroid.requestMultiple([
      PermissionsAndroid.PERMISSIONS.WRITE_EXTERNAL_STORAGE,
      PermissionsAndroid.PERMISSIONS.READ_EXTERNAL_STORAGE,
      PermissionsAndroid.PERMISSIONS.RECORD_AUDIO,
    ]);

    console.log('write external stroage', grants);

    if (
      grants['android.permission.WRITE_EXTERNAL_STORAGE'] ===
        PermissionsAndroid.RESULTS.GRANTED &&
      grants['android.permission.READ_EXTERNAL_STORAGE'] ===
        PermissionsAndroid.RESULTS.GRANTED &&
      grants['android.permission.RECORD_AUDIO'] ===
        PermissionsAndroid.RESULTS.GRANTED
    ) {
      console.log('Permissions granted');
    } else {
      console.log('All required permissions not granted');
      return;
    }
  } catch (err) {
    console.warn(err);
    return;
  }
}
```

Lastly, you need to enable `kotlin`. Please change add the line below in `android/build.gradle`.

```diff
buildscript {
  ext {
      buildToolsVersion = "29.0.3"
+     // Note: Below change is necessary for pause / resume audio feature. Not for Kotlin.
+     minSdkVersion = 24
      compileSdkVersion = 29
      targetSdkVersion = 29
+     kotlinVersion = '1.6.10'

      ndkVersion = "20.1.5948944"
  }
  repositories {
      google()
      jcenter()
  }
  dependencies {
      classpath("com.android.tools.build:gradle:4.2.2")
+     classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion"
  }
...
```

## Breaking Changes

- [`Android`] Apply Foreground Service for Recording Behavior in Background.

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.actionpower.audiorecorder">
    ...
    <application>
        <service android:name="com.actionpower.audiorecorder.ForegroundService" android:stopWithTask="false"/>
    </application>
</manifest>
```

- Apply recording status change code for `Interrupt` (ios), `Audio Focus Change` (Android) situations during recording.

android

```kt
 override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                isInterrupted = true
                if (!isPausedByUser) {
                    isPausedByInterrupt = true
                    pauseRecorder(null)
                    val obj = Arguments.createMap()
                    obj.putString("status", "paused")
                    sendEvent(reactContext, "rn-recordback", obj)
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                isInterrupted = false
                if (isPausedByInterrupt) {
                    isPausedByInterrupt = false
                    resumeRecorder(null);
                    val obj = Arguments.createMap()
                    obj.putString("status", "resume")
                    sendEvent(reactContext, "rn-recordback", obj)
                }
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        val audioManager = reactContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val result = audioManager.requestAudioFocus(
                this,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
        )
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        val audioManager = reactContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.abandonAudioFocus(this)
    }
```

ios

```swift
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
                recordTimer?.invalidate()
                recordTimer = nil;
                _isPausedByInterrupt = true

                audioRecorder.pause()
                sendEvent(withName: "rn-recordback", body: ["status": "paused"])
            }
        case .ended:
            _isInterrupted = false
            if (_isPausedByInterrupt) {
                do {
                    try AVAudioSession.sharedInstance().setActive(true)
                    audioRecorder.record()
                    if (recordTimer == nil) {
                        startRecorderTimer()
                    }
                    _isPausedByInterrupt = false
                    sendEvent(withName: "rn-recordback", body: ["status": "resume"])
                } catch {
                    print("Failed to activate audio session or resume recording.")
                }
            }
        @unknown default:
            break
        }
    }
```

## Methods

All methods are implemented with promises.

| Func                     |                    Param                     |      Return       | Description                                                                                                         |
| :----------------------- | :------------------------------------------: | :---------------: | :------------------------------------------------------------------------------------------------------------------ |
| mmss                     |               `number` seconds               |     `string`      | Convert seconds to `minute:second` string                                                                           |
| setSubscriptionDuration  |                                              |      `void`       | Set default callback time when starting recorder or player. Default to `0.5` which is `500ms`                       |
| addRecordBackListener    |             `Function` callBack              |      `void`       | Get callback from native module. Will receive `currentPosition`, `currentMetering` (if configured in startRecorder) |
| removeRecordBackListener |             `Function` callBack              |      `void`       | Removes recordback listener                                                                                         |
| startRecorder            | `<string>` uri? `<boolean>` meteringEnabled? |  `Promise<void>`  | Start recording. Not passing uri will save audio in default location.                                               |
| pauseRecorder            |                                              | `Promise<string>` | Pause recording.                                                                                                    |
| resumeRecorder           |                                              | `Promise<string>` | Resume recording.                                                                                                   |
| stopRecorder             |                                              | `Promise<string>` | Stop recording.                                                                                                     |

## Able to customize recorded audio quality (from `2.3.0`)

```
interface AudioSet {
  AVSampleRateKeyIOS?: number;
  AVFormatIDKeyIOS?: AVEncodingType;
  AVModeIOS?: AVModeType;
  AVNumberOfChannelsKeyIOS?: number;
  AVEncoderAudioQualityKeyIOS?: AVEncoderAudioQualityIOSType;
  AudioSourceAndroid?: AudioSourceAndroidType;
  OutputFormatAndroid?: OutputFormatAndroidType;
  AudioEncoderAndroid?: AudioEncoderAndroidType;
}
```

> More description on each parameter types are described in `index.d.ts`. Below is an example code.

```ts
const audioSet: AudioSet = {
  AudioEncoderAndroid: AudioEncoderAndroidType.AAC,
  AudioSourceAndroid: AudioSourceAndroidType.MIC,
  AVModeIOS: AVModeIOSOption.measurement,
  AVEncoderAudioQualityKeyIOS: AVEncoderAudioQualityIOSType.high,
  AVNumberOfChannelsKeyIOS: 2,
  AVFormatIDKeyIOS: AVEncodingOption.aac,
};
const meteringEnabled = false;

const uri = await this.audioRecorderPlayer.startRecorder(
  path,
  audioSet,
  meteringEnabled,
);

this.audioRecorderPlayer.addRecordBackListener((e: any) => {
  this.setState({
    recordSecs: e.currentPosition,
    recordTime: this.audioRecorderPlayer.mmssss(Math.floor(e.currentPosition)),
  });
});
```

## Default Path

- Default path for android uri is `{cacheDir}/sound.mp4`.
- Default path for ios uri is `{cacheDir}/sound.m4a`.

## Usage

```javascript
import AudioRecorder from '@actionpower/rn-audio-recorder';

const audioRecorder = new AudioRecorder();

onStartRecord = async () => {
  const result = await this.audioRecorder.startRecorder();
  this.audioRecorder.addRecordBackListener((e) => {
    this.setState({
      recordSecs: e.currentPosition,
      recordTime: this.audioRecorder.mmssss(Math.floor(e.currentPosition)),
    });
    return;
  });
  console.log(result);
};

onStopRecord = async () => {
  const result = await this.audioRecorder.stopRecorder();
  this.audioRecorder.removeRecordBackListener();
  this.setState({
    recordSecs: 0,
  });
  console.log(result);
};
```
