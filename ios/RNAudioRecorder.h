#import <Foundation/Foundation.h>
#import <AVFoundation/AVFoundation.h>
#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>

@interface RNAudioRecorder: RCTEventEmitter <RCTBridgeModule, AVAudioPlayerDelegate>
- (void)updateRecorderProgress:(NSTimer*) timer;
@end
