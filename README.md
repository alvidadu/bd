# APK Builder — Real Native Android APK Builder

A complete, production-oriented on-device Android APK builder written entirely in Java.

## What's included

- **Real Java 17 compilation** — ECJ 3.41 (Eclipse Compiler for Java), Java 17 only
- **Real resource compilation** — AAPT2
- **Real DEX generation** — D8 (from r8, required for Java 17 bytecode)
- **Real APK packaging** — proper ZIP packaging
- **Real APK signing** — apksig v1/v2/v3 signing
- **Real zipalign** — proper 4-byte alignment
- **Real build logging** — every tool's actual output
- **String obfuscation** — optional StringFog base64 encoding
- **Optional R8/ProGuard** — shrinking & optimization (add r8.jar or proguard-base.jar to app/libs)

## Project structure

```
ApkBuilder/
├── app/
│   ├── libs/
│   │   ├── apksig.jar               ← APK signer
│   │   ├── dx-10.0.0.jar            ← DX dexer
│   │   └── proguard-base.jar (optional)
│   ├── src/main/
│   │   ├── java/com/apkbuilder/     ← Complete build pipeline
│   │   ├── res/
│   │   │   ├── layout/              ← UI screens
│   │   │   ├── drawable/            ← Launcher icon
│   │   │   ├── values/              ← Strings, colors, themes
│   │   │   └── xml/
│   │   ├── assets/
│   │   │   └── toolchain/
│   │   │       └── android.jar      ← Platform stub (SDK 34)
│   │   └── jniLibs/arm64-v8a/
│   │       ├── libaapt2.so          ← Resource compiler (aarch64)
│   │       └── libzipalign.so       ← ZIP aligner (aarch64)
│   └── build.gradle
├── build.gradle
├── settings.gradle
└── gradle.properties
```

## Building from source

```bash
./gradlew build
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Using APK Builder to create projects

1. Launch APK Builder on your Android device
2. Tap **+ New Project**
3. Enter app name, package name, version, SDK levels
4. Java version is fixed to **17** and the dexer to **D8**
5. Enable optional tools (String Fog, R8, ProGuard if jars present)
6. Tap **CREATE PROJECT**

## The real build pipeline

Every step produces real output from real tools:

1. **Validate** project config & files
2. **Compile resources** with AAPT2 → resources.apk, R.java
3. **Compile Java 17** with ECJ → .class files
4. **[Optional] String Fog** — encode string literals
5. **[Optional] R8/ProGuard** — shrink & optimize
6. **Dex** with D8 → classes.dex
7. **Package APK** — combine resources + dex + assets
8. **Sign APK** — v1/v2/v3 with debug keystore (or custom)
9. **ZipAlign** — 4-byte boundary alignment
10. **Verify** — final signature & alignment check

If any step fails, you see the real error from that tool—never a fake success message.

## Requirements

- Android 8.0+ (API 26+)
- 200+ MB free storage for build artifacts
- Building APK Builder itself needs internet once (Gradle downloads r8 from Maven)
- Java 17 JDK to build APK Builder with Gradle
- For ProGuard: add proguard-base.jar to app/libs and rebuild

## Key design

- **No external downloads** — all tools bundled or self-contained
- **No fake UI** — every button performs a real build stage
- **No WebView IDE** — native Android Activities + real widgets
- **Real error reporting** — compiler/tool errors with file:line:column
- **Offline first** — works without internet after initial setup

## Architecture

`BuildManager` orchestrates the pipeline:

- `Aapt2Runner` — runs AAPT2 compile+link
- `JavaCompiler` — ECJ in-process compilation
- `D8Runner` — DEX generation (`DxRunner` kept only as legacy code)
- `ApkPackager` — builds unsigned APK ZIP
- `ApkSignerRunner` — real v1/v2/v3 signing
- `ZipAlignRunner` — binary alignment
- `StringFog` — optional source-level obfuscation
- `ToolchainChecker` — validates toolchain status

All error output is parsed into structured `BuildError` objects (file, line, column, message).

## What's NOT faked

- The final APK is always a real, installable Android APK
- Every build stage logs actual tool output
- Missing tools report real errors, not false success
- File access uses real Storage Access Framework / File API
- APK signing uses real cryptographic libraries

---

Built for on-device development on Android's own hardware.
