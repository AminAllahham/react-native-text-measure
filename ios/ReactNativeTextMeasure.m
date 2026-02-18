#import "ReactNativeTextMeasure.h"
#import <UIKit/UIKit.h>

// ─────────────────────────────────────────────────────────────────────────────
// Private helper — builds the measurement result dict from a string + options.
// Extracted so both the async and sync entry-points share identical logic.
// ─────────────────────────────────────────────────────────────────────────────
static NSDictionary *RNTMPerformMeasure(NSString *text, NSDictionary *options) {

  // ── 1. Parse options ───────────────────────────────────────────────────────
  CGFloat   fontSize      = options[@"fontSize"]      ? [options[@"fontSize"]      doubleValue] : 14.0;
  NSString *fontFamily    = options[@"fontFamily"];
  NSString *fontWeight    = options[@"fontWeight"]    ?: @"normal";
  NSString *fontStyle     = options[@"fontStyle"]     ?: @"normal";
  CGFloat   letterSpacing = options[@"letterSpacing"] ? [options[@"letterSpacing"] doubleValue] : 0.0;
  CGFloat   lineHeight    = options[@"lineHeight"]    ? [options[@"lineHeight"]    doubleValue] : 0.0;
  CGFloat   maxWidth      = options[@"maxWidth"]      ? [options[@"maxWidth"]      doubleValue] : 0.0;
  CGFloat   maxHeight     = options[@"maxHeight"]     ? [options[@"maxHeight"]     doubleValue] : 0.0;
  NSInteger maxLines      = options[@"numberOfLines"] ? [options[@"numberOfLines"] integerValue]: 0;

  if (maxWidth  <= 0) maxWidth  = CGFLOAT_MAX;
  if (maxHeight <= 0) maxHeight = CGFLOAT_MAX;

  // ── 2. Resolve UIFontWeight ────────────────────────────────────────────────
  UIFontWeight uiFontWeight = UIFontWeightRegular;
  NSDictionary *weightMap = @{
    @"100": @(UIFontWeightUltraLight),
    @"200": @(UIFontWeightThin),
    @"300": @(UIFontWeightLight),
    @"400": @(UIFontWeightRegular),
    @"500": @(UIFontWeightMedium),
    @"600": @(UIFontWeightSemibold),
    @"700": @(UIFontWeightBold),
    @"800": @(UIFontWeightHeavy),
    @"900": @(UIFontWeightBlack),
    @"bold": @(UIFontWeightBold),
    @"normal": @(UIFontWeightRegular),
  };
  if (weightMap[fontWeight]) {
    uiFontWeight = [weightMap[fontWeight] doubleValue];
  }

  BOOL isItalic = [fontStyle isEqualToString:@"italic"];

  // ── 3. Build UIFont ────────────────────────────────────────────────────────
  UIFont *font = nil;

  if (fontFamily.length > 0) {
    // Build a descriptor for the requested family so we can apply traits
    UIFontDescriptor *descriptor = [UIFontDescriptor fontDescriptorWithName:fontFamily size:fontSize];

    UIFontDescriptorSymbolicTraits traits = 0;
    if (uiFontWeight >= UIFontWeightBold) traits |= UIFontDescriptorTraitBold;
    if (isItalic)                         traits |= UIFontDescriptorTraitItalic;

    if (traits != 0) {
      UIFontDescriptor *traitDescriptor = [descriptor fontDescriptorWithSymbolicTraits:traits];
      // fontDescriptorWithSymbolicTraits returns nil if the traits can't be satisfied
      if (traitDescriptor) descriptor = traitDescriptor;
    }

    font = [UIFont fontWithDescriptor:descriptor size:fontSize];
  }

  // Fallback: system font (always available)
  if (!font) {
    if (isItalic) {
      UIFontDescriptor *sysDesc = [[UIFont systemFontOfSize:fontSize weight:uiFontWeight] fontDescriptor];
      UIFontDescriptor *italicDesc = [sysDesc fontDescriptorWithSymbolicTraits:UIFontDescriptorTraitItalic];
      font = italicDesc ? [UIFont fontWithDescriptor:italicDesc size:fontSize]
                        : [UIFont systemFontOfSize:fontSize weight:uiFontWeight];
    } else {
      font = [UIFont systemFontOfSize:fontSize weight:uiFontWeight];
    }
  }

  // Ultimate fallback
  if (!font) font = [UIFont systemFontOfSize:fontSize];

  // ── 4. Build paragraph style ───────────────────────────────────────────────
  NSMutableParagraphStyle *paraStyle = [[NSMutableParagraphStyle alloc] init];
  paraStyle.lineBreakMode = NSLineBreakByWordWrapping;

  if (lineHeight > 0.0) {
    // Setting both min and max to the same value gives an exact line height,
    // which matches React Native's lineHeight prop behaviour.
    paraStyle.minimumLineHeight = lineHeight;
    paraStyle.maximumLineHeight = lineHeight;
  }

  // ── 5. Build attributed string ─────────────────────────────────────────────
  NSMutableDictionary *attrs = [@{
    NSFontAttributeName:            font,
    NSParagraphStyleAttributeName:  paraStyle,
  } mutableCopy];

  if (letterSpacing != 0.0) {
    // NSKernAttributeName is in points — same unit as fontSize on iOS.
    attrs[NSKernAttributeName] = @(letterSpacing);
  }

  NSString *safeText = (text.length > 0) ? text : @"";
  NSAttributedString *attrString = [[NSAttributedString alloc] initWithString:safeText
                                                                   attributes:attrs];

  // ── 6. TextKit layout ──────────────────────────────────────────────────────
  //
  //  We use the full TextKit stack (NSTextStorage → NSLayoutManager → NSTextContainer)
  //  rather than boundingRectWithSize:options:context: because:
  //    • It correctly handles numberOfLines (via maximumNumberOfLines).
  //    • It gives us a real lineCount via enumerateLineFragments.
  //    • It is the same engine UILabel uses internally.
  //
  NSTextStorage   *storage   = [[NSTextStorage alloc] initWithAttributedString:attrString];
  NSLayoutManager *manager   = [[NSLayoutManager alloc] init];
  NSTextContainer *container = [[NSTextContainer alloc] initWithSize:CGSizeMake(maxWidth, maxHeight)];

  container.lineFragmentPadding  = 0.0;   // no extra horizontal padding
  container.lineBreakMode        = NSLineBreakByWordWrapping;
  if (maxLines > 0) container.maximumNumberOfLines = (NSUInteger)maxLines;

  [manager addTextContainer:container];
  [storage addLayoutManager:manager];

  // Force complete layout pass
  [manager ensureLayoutForTextContainer:container];

  CGRect usedRect = [manager usedRectForTextContainer:container];

  // Count lines by enumerating actual line fragments
  __block NSInteger lineCount = 0;
  NSRange glyphRange = [manager glyphRangeForTextContainer:container];
  [manager enumerateLineFragmentsForGlyphRange:glyphRange
                                    usingBlock:^(CGRect rect,
                                                 CGRect usedLineRect,
                                                 NSTextContainer *tc,
                                                 NSRange glyphRng,
                                                 BOOL *stop) {
    lineCount++;
  }];

  // ceil to avoid sub-pixel gaps when the caller uses these values for layout
  CGFloat measuredWidth  = ceil(usedRect.size.width);
  CGFloat measuredHeight = ceil(usedRect.size.height);

  return @{
    @"width":     @(measuredWidth),
    @"height":    @(measuredHeight),
    @"lineCount": @(lineCount),
  };
}


// ─────────────────────────────────────────────────────────────────────────────
// Module
// ─────────────────────────────────────────────────────────────────────────────

@implementation ReactNativeTextMeasure

RCT_EXPORT_MODULE()

// ── Async (recommended) ───────────────────────────────────────────────────────
/**
 * measureText(text, options) → Promise<{ width, height, lineCount }>
 *
 * Runs on a background serial queue — does NOT block the JS thread.
 */
RCT_EXPORT_METHOD(measureText:(NSString *)text
                     options:(NSDictionary *)options
                    resolver:(RCTPromiseResolveBlock)resolve
                    rejecter:(RCTPromiseRejectBlock)reject)
{
  // Capture to avoid potential mutation
  NSString     *capturedText    = [text copy];
  NSDictionary *capturedOptions = [options copy];

  dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^{
    @try {
      NSDictionary *result = RNTMPerformMeasure(capturedText, capturedOptions ?: @{});
      resolve(result);
    } @catch (NSException *exception) {
      reject(@"MEASURE_ERROR", exception.reason, nil);
    }
  });
}

// ── Synchronous (use sparingly) ───────────────────────────────────────────────
/**
 * measureTextSync(text, options) → { width, height, lineCount }
 *
 * Runs on the JS thread. Safe for use in synchronous layout code but
 * will block JS execution for the duration of the measurement.
 */
RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(measureTextSync:(NSString *)text
                                              options:(NSDictionary *)options)
{
  @try {
    return RNTMPerformMeasure(text ?: @"", options ?: @{});
  } @catch (NSException *exception) {
    return @{ @"error": exception.reason ?: @"Unknown error" };
  }
}

@end