# R8 rules for the release build (Task 17, #48).

# LiteRT-LM 0.16.1 ships no consumer rules. Its native library calls back into these classes by
# name (JNI), and it uses Gson and kotlin-reflect on its own types, so keep the whole package.
-keep class com.google.ai.edge.litertlm.** { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keep class kotlin.Metadata { *; }

# Keep line numbers in the local crash log (core:common CrashLogWriter); hide the source file name.
-keepattributes LineNumberTable
-renamesourcefileattribute SourceFile
