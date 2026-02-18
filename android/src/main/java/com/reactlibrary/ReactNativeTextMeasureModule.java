package com.reactlibrary;

import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * ReactNativeTextMeasureModule
 *
 * Measures text bounding dimensions (width, height, lineCount) using Android's
 * StaticLayout — the same engine that TextView uses. No View is created or rendered.
 *
 * All input values are in density-independent pixels (dp).
 * All output values are also in dp, matching React Native's coordinate system.
 *
 * JS API:
 *   measureText(text, options)      → Promise<{ width, height, lineCount }>
 *   measureTextSync(text, options)  → { width, height, lineCount }
 */
public class ReactNativeTextMeasureModule extends ReactContextBaseJavaModule {

    private final ReactApplicationContext reactContext;

    public ReactNativeTextMeasureModule(ReactApplicationContext context) {
        super(context);
        this.reactContext = context;
    }

    @NonNull
    @Override
    public String getName() {
        // Must match NativeModules.ReactNativeTextMeasure on the JS side
        return "ReactNativeTextMeasure";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // JS-callable: async (recommended)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Async measurement. Resolves with { width, height, lineCount }.
     * Runs on the React Native background thread pool — does not block JS.
     */
    @ReactMethod
    public void measureText(String text, ReadableMap options, Promise promise) {
        try {
            promise.resolve(performMeasure(text, options));
        } catch (Exception e) {
            promise.reject("MEASURE_ERROR", e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // JS-callable: synchronous
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Synchronous measurement. Returns { width, height, lineCount } directly.
     *
     * ⚠️  Runs on the JS thread. Use only when async is not an option.
     *     For old architecture: isBlockingSynchronousMethod = true is required.
     *     For new architecture (TurboModules): implement NativeReactNativeTextMeasureSpec.
     */
    @ReactMethod(isBlockingSynchronousMethod = true)
    public WritableMap measureTextSync(String text, ReadableMap options) {
        try {
            return performMeasure(text, options);
        } catch (Exception e) {
            WritableMap error = Arguments.createMap();
            error.putString("error", e.getMessage());
            return error;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Core measurement — called by both async and sync entry points
    // ─────────────────────────────────────────────────────────────────────────

    private WritableMap performMeasure(@Nullable String text, @Nullable ReadableMap options) {
        if (text == null) text = "";
        if (options == null) options = Arguments.createMap();

        // ── 1. Parse options ─────────────────────────────────────────────────
        float   fontSize         = getFloat(options, "fontSize",         14f);
        String  fontFamily       = getString(options, "fontFamily",       null);
        String  fontWeight       = getString(options, "fontWeight",       "normal");
        String  fontStyle        = getString(options, "fontStyle",        "normal");
        float   letterSpacing    = getFloat(options, "letterSpacing",     0f);
        float   lineHeight       = getFloat(options, "lineHeight",        0f);
        float   maxWidth         = getFloat(options, "maxWidth",          0f);
        float   maxHeight        = getFloat(options, "maxHeight",         0f);
        int     numberOfLines    = getInt(options,   "numberOfLines",     0);
        boolean includeFontPad   = getBool(options,  "includeFontPadding", true);

        // ── 2. dp → px conversion ─────────────────────────────────────────────
        // React Native uses dp everywhere; Android Paint/StaticLayout needs px.
        float density      = reactContext.getResources().getDisplayMetrics().density;
        float fontSizePx   = fontSize   * density;
        float lineHeightPx = lineHeight * density;
        int   maxWidthPx   = (maxWidth  > 0) ? Math.round(maxWidth  * density) : Integer.MAX_VALUE;
        int   maxHeightPx  = (maxHeight > 0) ? Math.round(maxHeight * density) : Integer.MAX_VALUE;

        // ── 3. Resolve Typeface ───────────────────────────────────────────────
        boolean isBold   = "bold".equalsIgnoreCase(fontWeight) || numericWeightIsBold(fontWeight);
        boolean isItalic = "italic".equalsIgnoreCase(fontStyle);

        int typefaceStyle;
        if (isBold && isItalic) typefaceStyle = Typeface.BOLD_ITALIC;
        else if (isBold)        typefaceStyle = Typeface.BOLD;
        else if (isItalic)      typefaceStyle = Typeface.ITALIC;
        else                    typefaceStyle = Typeface.NORMAL;

        Typeface typeface = resolveTypeface(fontFamily, typefaceStyle);

        // ── 4. Build TextPaint ────────────────────────────────────────────────
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(fontSizePx);
        paint.setTypeface(typeface);

        // letterSpacing: Android uses EM fractions; convert from dp.
        // letterSpacing_em = letterSpacing_px / fontSizePx
        // We treat the user's value as dp, so first dp→px, then →em.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && letterSpacing != 0f) {
            float letterSpacingPx = letterSpacing * density;
            paint.setLetterSpacing(letterSpacingPx / fontSizePx);
        }

        // ── 5. Build StaticLayout ─────────────────────────────────────────────
        //
        //  StaticLayout is the exact engine TextView uses for static (non-editable) text.
        //  It performs full Unicode line-breaking, glyph shaping, and produces the real
        //  pixel-accurate dimensions — not an estimate.
        //
        StaticLayout layout = buildStaticLayout(
                text, paint, maxWidthPx,
                lineHeightPx, numberOfLines, includeFontPad
        );

        // ── 6. Extract dimensions ─────────────────────────────────────────────
        int    lineCount      = layout.getLineCount();
        float  measuredHeight = layout.getHeight();
        float  measuredWidth  = 0f;

        for (int i = 0; i < lineCount; i++) {
            float lineWidth = layout.getLineWidth(i);
            if (lineWidth > measuredWidth) measuredWidth = lineWidth;
        }

        // Clamp height if maxHeight was provided
        if (maxHeightPx != Integer.MAX_VALUE && measuredHeight > maxHeightPx) {
            measuredHeight = maxHeightPx;
        }

        // ── 7. px → dp for output ─────────────────────────────────────────────
        WritableMap result = Arguments.createMap();
        result.putDouble("width",     measuredWidth  / density);
        result.putDouble("height",    measuredHeight / density);
        result.putInt(   "lineCount", lineCount);
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // StaticLayout builder — handles API version differences
    // ─────────────────────────────────────────────────────────────────────────

    @SuppressWarnings("deprecation")
    private StaticLayout buildStaticLayout(
            String text,
            TextPaint paint,
            int maxWidthPx,
            float lineHeightPx,
            int numberOfLines,
            boolean includeFontPad
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // API 23+ — use the Builder (clean, full-featured)
            StaticLayout.Builder builder = StaticLayout.Builder
                    .obtain(text, 0, text.length(), paint, maxWidthPx)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setIncludePad(includeFontPad)
                    .setMaxLines(numberOfLines > 0 ? numberOfLines : Integer.MAX_VALUE)
                    .setEllipsize(numberOfLines > 0 ? TextUtils.TruncateAt.END : null);

            if (lineHeightPx > 0) {
                // Android line spacing: total_line_height = naturalLineHeight * mult + add
                // We want total = lineHeightPx, so: add = lineHeightPx - natural, mult = 1
                float naturalLineHeight = -paint.ascent() + paint.descent();
                builder.setLineSpacing(lineHeightPx - naturalLineHeight, 1f);
            } else {
                builder.setLineSpacing(0f, 1f);
            }

            return builder.build();

        } else {
            // API 21–22 fallback — deprecated constructor but still accurate
            float spacingAdd  = 0f;
            float spacingMult = 1f;

            if (lineHeightPx > 0) {
                float naturalLineHeight = -paint.ascent() + paint.descent();
                spacingAdd = lineHeightPx - naturalLineHeight;
            }

            return new StaticLayout(
                    text, 0, text.length(),
                    paint, maxWidthPx,
                    Layout.Alignment.ALIGN_NORMAL,
                    spacingMult, spacingAdd,
                    includeFontPad,
                    numberOfLines > 0 ? TextUtils.TruncateAt.END : null,
                    maxWidthPx
            );
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Typeface resolution
    // ─────────────────────────────────────────────────────────────────────────

    private Typeface resolveTypeface(@Nullable String fontFamily, int style) {
        if (fontFamily != null && !fontFamily.isEmpty()) {
            // 1. Try assets/fonts/<fontFamily>.ttf  (common RN convention)
            try {
                Typeface fromAssets = Typeface.createFromAsset(
                        reactContext.getAssets(),
                        "fonts/" + fontFamily + ".ttf"
                );
                if (fromAssets != null) {
                    return Typeface.create(fromAssets, style);
                }
            } catch (Exception ignored) { /* not in assets */ }

            // 2. Try as a system font name (e.g. "sans-serif", "monospace", "Roboto")
            try {
                Typeface systemFont = Typeface.create(fontFamily, style);
                if (systemFont != null) return systemFont;
            } catch (Exception ignored) { /* unknown family */ }
        }

        // 3. Default system font with the requested style
        return Typeface.create((String) null, style);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ReadableMap helpers (null-safe)
    // ─────────────────────────────────────────────────────────────────────────

    private float getFloat(ReadableMap map, String key, float def) {
        if (map.hasKey(key) && !map.isNull(key)) return (float) map.getDouble(key);
        return def;
    }

    private int getInt(ReadableMap map, String key, int def) {
        if (map.hasKey(key) && !map.isNull(key)) return map.getInt(key);
        return def;
    }

    private boolean getBool(ReadableMap map, String key, boolean def) {
        if (map.hasKey(key) && !map.isNull(key)) return map.getBoolean(key);
        return def;
    }

    @Nullable
    private String getString(ReadableMap map, String key, @Nullable String def) {
        if (map.hasKey(key) && !map.isNull(key)) return map.getString(key);
        return def;
    }

    private boolean numericWeightIsBold(String weight) {
        try {
            return Integer.parseInt(weight.trim()) >= 600;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}