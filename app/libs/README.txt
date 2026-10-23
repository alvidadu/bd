Local jars used by APK Builder:

  ecj.jar      - Eclipse Compiler for Java 3.41.0 (Java 17 compiler). Used from here, nothing is
                 downloaded.
  sdklib.jar   - Android sdklib (legacy, 2017).
  apksig.jar   - APK v1/v2/v3 signing + verification.
  dx-10.0.0.jar- legacy DX classes (used for runtime libs).

  r8_1.jar     - R8/D8 dexer (Java 17 needs D8). Used from here, nothing is downloaded.

Do NOT add the old ecj-3.16.0.jar - it cannot compile Java 17 - and never keep two ecj jars.
