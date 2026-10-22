# Attend Tag

An Android app that makes a phone act as the NFC tag on a classroom door, for testing the Attend attendance app.
It uses Android Host Card Emulation (HCE) to emulate an NFC Forum Type 4 Tag, standing in for the NTAG 424 DNA tag we do not have yet.

It is a separate app from the Attend student app on purpose.
It holds the tag key, and a tag key must never ship inside the student app.

## The contract

The phone acts as one of three demo doors, chosen on the app's screen.
The Attend seed data registers the same three doors.

| Door | UID | Key (hex) | Room |
|---|---|---|---|
| LT2 Door A | `04A1B2C3D45E80` | `6b1f0c2a9d3e4f5061728394a5b6c7d8` | LT2 (Lecture Theatre 2) |
| LT2 Door B | `04A1B2C3D45E81` | `7c2a1d3b0e4f5a6172839405b6c7d8e9` | LT2 (Lecture Theatre 2) |
| 2.506 Door | `04A1B2C3D45E82` | `8d3b2e4c1f5a6b7283940516c7d8e9fa` | 2.506 (cohort classroom) |

These are public demo keys, not secrets; they sign nothing real.
LT2 Door A is selected until you pick another door, and the choice survives app restarts.

On every read the tag serves an NDEF message with one URI record (TNF well-known, type `U`, prefix code `0x04` for `https://`):

```
https://attend.example/t?uid=04A1B2C3D45E80&ctr=<counter, 6 lowercase hex digits>&cmac=<MAC, 16 uppercase hex digits>
```

The MAC is the first 8 bytes of HMAC-SHA256(key bytes, UTF-8 of `<UID>:<ctr>`), written as uppercase hex.

| Door | Counter | Signed message | cmac |
|---|---|---|---|
| LT2 Door A | 42 | `04A1B2C3D45E80:00002a` | `806CDCC0C638BB5C` |
| LT2 Door A | 43 | `04A1B2C3D45E80:00002b` | `CE8035914E933928` |
| LT2 Door B | 42 | `04A1B2C3D45E81:00002a` | `51B205217C81A631` |
| 2.506 Door | 42 | `04A1B2C3D45E82:00002a` | `7E03165A333FF1D2` |

Each door has its own counter.
Every counter starts at 42, because the Attend mock's seeded data already uses 41.
A counter goes up by one for each read of its door, that is each time a reader selects the NDEF file, and it survives app restarts.
Switching doors does not touch the other doors' counters.
If a reader selects the NDEF file twice while the phones stay together, it gets the same reading both times; the next tap gets the next counter.
The reader parses `ctr` with `parseInt(ctr, 16)` and compares `uid` and `cmac` case-insensitively.

## Install

Every push builds a debug APK in GitHub Actions.
Open the latest green run under the repo's Actions tab, download the `attend-tag-emulator-debug-apk` artifact, unzip it, and install `app-debug.apk` on an Android phone with NFC (allow installs from unknown sources when asked).
With a USB cable you can also run `adb install app-debug.apk`.

To build with EAS instead, run `npx eas-cli@latest build -p android --profile preview`, which produces an installable APK.

## Test tap against the Attend app

1. On the tag phone (this app): turn NFC on, open Attend Tag, pick the door under "Act as door", and keep the screen on and unlocked.
2. On the student phone (iPhone or Android): open Attend and start device capture.
3. Hold the backs of the two phones together until the student phone reacts.
4. The tag phone's screen shows the counter it served, the reads served and the time of the last read.

The Attend app should accept the reading and record a tap with counter 42, then 43 on the next tap, and so on, for the door you picked.

If Android offers a choice of apps for the tag, pick Attend Tag; another installed app has registered the same NFC application.

## Replay demo

1. Do one normal tap so the tag has a previous reading.
2. Turn on "Replay last reading".
3. Tap again: the tag serves the previous reading again, with the same counter and MAC.
4. The Attend app should reject it as a replay, because it has already seen that counter.

The switch turns itself off after one replayed read.
Replay and "Reset counter" act on the selected door only.
"Reset counter" starts that door again from 42, which the Attend app will also treat as a replay unless its data is reset too.

## How it is built

- `App.tsx` is the one screen.
- `modules/tag-emulator` is a local Expo module in Kotlin.
- `modules/tag-emulator/android/.../nfc/protocol` holds the Type 4 Tag APDU handling, the demo doors, the door choice, the counters and the MAC in plain Kotlin with no Android dependencies.
- `TagApduService` is a thin `HostApduService` wrapper around it, and `TagEmulatorModule` is the bridge to JavaScript.
- `modules/tag-emulator/app.plugin.js` is the config plugin that adds the service, the NFC permission and the HCE feature to the Android manifest.
- `protocol-check` is a small JVM program that plays the APDUs a phone reader sends and checks every answer, the test vectors, replay mode, persistence, the door picker and the status words for bad commands.

Run the checks with:

```
pnpm install
pnpm typecheck
pnpm lint
cd protocol-check && ./gradlew run
```

The protocol check writes the full APDU exchange to `protocol-check/build/protocol-transcript.txt`; CI uploads it as the `protocol-transcript` artifact.
The `android/` folder is generated by `npx expo prebuild -p android` and is not committed.

## Limits

- HMAC-SHA256 stands in for the AES-CMAC that a real NTAG 424 DNA computes, so the Attend server will need a different MAC check for real tags.
- An emulated tag shows the reader a random hardware UID on every tap, so the UID comes from the NDEF data, not from the NFC layer.
- Android only: iPhones cannot emulate NFC tags, but an iPhone can be the reading phone.
- The phone must have NFC with Host Card Emulation, with NFC on and the screen on; the app says so if the phone cannot do it.

## License

MIT
