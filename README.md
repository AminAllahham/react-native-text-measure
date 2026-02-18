# react-native-text-measure

> Accurately measure text `width`, `height`, and `lineCount` using native layout engines — no view rendered.

| Platform | Engine | Accuracy |
|----------|--------|----------|
| iOS | `NSLayoutManager` (TextKit) | ✅ Identical to `UILabel` |
| Android | `StaticLayout` | ✅ Identical to `TextView` |

---

## Installation

```sh
npm install react-native-text-measure
# or
yarn add react-native-text-measure
```

### iOS
```sh
cd ios && pod install
```

### Android
Auto-linking handles it on RN >= 0.60. For manual linking see [`android/README.md`](android/README.md).

---

## Usage

```js
import { measureText, measureTextSync } from 'react-native-text-measure';

// ── Async (recommended) ───────────────────────────────────────────────────────
const { width, height, lineCount } = await measureText('Hello world', {
  fontSize: 20,
  fontFamily: 'Helvetica',
  fontWeight: 'normal',
  maxWidth: 300,
});

// ── Sync (use sparingly — blocks JS thread) ───────────────────────────────────
const result = measureTextSync('Hello', { fontSize: 16 });
```

---

## API

### `measureText(text, options)` → `Promise<Result>`
### `measureTextSync(text, options)` → `Result`

#### Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `fontSize` | `number` | `14` | Font size in dp/pt |
| `fontFamily` | `string` | system | Font family name |
| `fontWeight` | `string` | `'normal'` | `'normal'`, `'bold'`, `'100'`–`'900'` |
| `fontStyle` | `string` | `'normal'` | `'normal'` or `'italic'` |
| `letterSpacing` | `number` | `0` | Extra letter spacing in dp/pt |
| `lineHeight` | `number` | `0` | Explicit line height. `0` = natural |
| `maxWidth` | `number` | `0` | Wrap width. `0` = no wrap (single line) |
| `maxHeight` | `number` | `0` | Clamp returned height. `0` = unclamped |
| `numberOfLines` | `number` | `0` | Max lines. `0` = unlimited |
| `includeFontPadding` | `boolean` | `true` | **Android only.** Match your `<Text>` setting |

#### Result

```ts
{
  width: number;     // dp (Android) / pt (iOS)
  height: number;    // dp (Android) / pt (iOS)
  lineCount: number;
}
```

---

## Accuracy notes

- Results are **not estimates** — both engines perform a full layout pass.
- `letterSpacing` unit differs between platforms (see inline comments).
- `includeFontPadding` must match your `<Text>` component on Android.
- If `fontFamily` is not found, the system font is used silently — measurement will be accurate but for the wrong font.

---

## License

MIT