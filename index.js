/**
 * react-native-text-measure
 *
 * Accurately measures text dimensions using native layout engines.
 * iOS  → NSLayoutManager (TextKit)   — identical to UILabel
 * Android → StaticLayout             — identical to TextView
 *
 * No view is rendered. Measurement is pure native computation.
 */

import { NativeModules } from "react-native";

const LINKING_ERROR =
  `react-native-text-measure: Native module not found.\n\n` +
  `iOS:     Make sure you ran \`pod install\` and the .m file is in your Xcode project.\n` +
  `Android: Make sure ReactNativeTextMeasurePackage is registered in MainApplication.\n\n` +
  `If you are using React Native >= 0.60, auto-linking should handle this.\n` +
  `Try rebuilding the app.`;

const NativeTextMeasure = NativeModules.ReactNativeTextMeasure
  ? NativeModules.ReactNativeTextMeasure
  : new Proxy(
      {},
      {
        get() {
          throw new Error(LINKING_ERROR);
        },
      },
    );

// ─────────────────────────────────────────────────────────────────────────────
// JSDoc typedef (works in plain JS + IDEs; see index.d.ts for TypeScript)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * @typedef {Object} TextMeasureOptions
 * @property {number}  [fontSize=14]        - Font size in dp (Android) / pt (iOS).
 * @property {string}  [fontFamily]         - Font family name. Falls back to system font.
 * @property {string}  [fontWeight='normal']- 'normal' | 'bold' | '100'–'900'.
 * @property {string}  [fontStyle='normal'] - 'normal' | 'italic'.
 * @property {number}  [letterSpacing=0]    - Extra letter spacing in dp/pt.
 * @property {number}  [lineHeight=0]       - Explicit line height. 0 = natural.
 * @property {number}  [maxWidth=0]         - Wrap width. 0 = no wrapping.
 * @property {number}  [maxHeight=0]        - Clamp height. 0 = unclamped.
 * @property {number}  [numberOfLines=0]    - Max lines. 0 = unlimited.
 * @property {boolean} [includeFontPadding=true] - Android only: match RN Text default.
 */

/**
 * @typedef {Object} TextMeasureResult
 * @property {number} width     - Measured width in dp (Android) / pt (iOS).
 * @property {number} height    - Measured height in dp (Android) / pt (iOS).
 * @property {number} lineCount - Number of lines the text was laid out into.
 */

// ─────────────────────────────────────────────────────────────────────────────
// Public API
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Asynchronously measures the bounding box of `text` with the given style options.
 * Runs off the JS thread — safe to call frequently.
 *
 * @param {string} text
 * @param {TextMeasureOptions} [options={}]
 * @returns {Promise<TextMeasureResult>}
 *
 * @example
 * const { width, height, lineCount } = await measureText('Hello world', {
 *   fontSize: 20,
 *   fontFamily: 'Helvetica',
 *   fontWeight: 'normal',
 *   maxWidth: 200,
 * });
 */
export function measureText(text, options = {}) {
  return NativeTextMeasure.measureText(String(text), sanitize(options));
}

/**
 * Synchronously measures text. Runs on the JS thread.
 * ⚠️  Prefer `measureText` (async). Use this only when you cannot await
 *     (e.g., inside a synchronous layout calculation).
 *
 * @param {string} text
 * @param {TextMeasureOptions} [options={}]
 * @returns {TextMeasureResult}
 *
 * @example
 * const { height } = measureTextSync('Hello', { fontSize: 16 });
 */
export function measureTextSync(text, options = {}) {
  return NativeTextMeasure.measureTextSync(String(text), sanitize(options));
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Remove undefined keys so the native side receives a clean map.
 * @param {TextMeasureOptions} options
 * @returns {Object}
 */
function sanitize(options) {
  const out = {};
  for (const [key, value] of Object.entries(options)) {
    if (value !== undefined && value !== null) {
      out[key] = value;
    }
  }
  return out;
}

export default { measureText, measureTextSync };
