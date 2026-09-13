# FFmpegKit：JNI 回调按类名反射，类与方法不能被 R8 改名/删除
# （AAR 自带 consumer rules，这里再加一层保险）
-keep class com.antonkarpenko.ffmpegkit.** { *; }
-keep class com.antonkarpenko.ffmpegkit.*$* { *; }

# 崩溃堆栈保留源码行号，便于对照 mapping.txt 排查
-keepattributes SourceFile,LineNumberTable
