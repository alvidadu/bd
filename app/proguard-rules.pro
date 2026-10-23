# Keep the compiler / dexer / signer libraries intact (they are used by name).
-keep class org.eclipse.jdt.** { *; }
-keep class com.android.dx.** { *; }
-keep class com.android.dex.** { *; }
-keep class com.android.apksig.** { *; }
-dontwarn **
