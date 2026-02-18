#import <React/RCTBridgeModule.h>

NS_ASSUME_NONNULL_BEGIN

/**
 * ReactNativeTextMeasure
 *
 * Native module that measures text bounding dimensions (width, height, lineCount)
 * using Apple's TextKit layout engine — identical to UILabel / UITextView rendering.
 * No view is created or rendered.
 *
 * Exposes two JS methods:
 *   measureText(text, options)     → Promise  (async, background thread)
 *   measureTextSync(text, options) → Object   (sync,  JS thread)
 */
@interface ReactNativeTextMeasure : NSObject <RCTBridgeModule>
@end

NS_ASSUME_NONNULL_END
