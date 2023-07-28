#import "React/RCTBridgeModule.h"
#import "React/RCTEventEmitter.h"

@interface RCT_EXTERN_MODULE(RNAudioRecorder, RCTEventEmitter)

RCT_EXTERN_METHOD(setSubscriptionDuration:(double)duration);

RCT_EXTERN_METHOD(startRecorder:(NSString *)path
                  audioSets:(NSDictionary *)audioSets
                  meteringEnabled:(BOOL)meteringEnabled
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject);

RCT_EXTERN_METHOD(stopRecorder:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject);

RCT_EXTERN_METHOD(pauseRecorder:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject);

RCT_EXTERN_METHOD(resumeRecorder:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject);


@end
