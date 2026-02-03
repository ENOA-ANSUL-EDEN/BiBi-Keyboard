# ==========================================
# 开源版 ProGuard 规则（main 源集）
# ==========================================

# 保留优化，禁用混淆，便于调试与社区协作
-dontobfuscate
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 3

# 精简日志
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
}

# 输出报告
-verbose
-printconfiguration build/outputs/proguard/configuration.txt
-printusage build/outputs/proguard/unused.txt

# 保留注解与源码信息
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ==========================================
# Android 组件
# ==========================================

-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# Parcelable
-keep class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ==========================================
# kotlinx.serialization
# ==========================================

-keepattributes InnerClasses
-dontnote kotlinx.serialization.**

-keep,includedescriptorclasses class com.brycewg.asrkb.**$$serializer { *; }
-keepclassmembers class com.brycewg.asrkb.** {
    *** Companion;
}
-keepclasseswithmembers class com.brycewg.asrkb.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# 序列化数据类（main 源集）
-keep class com.brycewg.asrkb.store.PromptPreset { *; }
-keep class com.brycewg.asrkb.store.SpeechPreset { *; }
-keep class com.brycewg.asrkb.store.AsrHistoryStore$* { *; }

# ==========================================
# 依赖库
# ==========================================

# Sherpa-ONNX JNI
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# DashScope SDK
-keep class com.alibaba.dashscope.** { *; }
-dontwarn com.alibaba.dashscope.**

# Lombok
-dontwarn lombok.**
-dontwarn org.projectlombok.**

# OkHttp & WebSocket
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Sardine WebDAV (SimpleXml)
-dontwarn org.simpleframework.**
-keep class org.simpleframework.** { *; }
-keepclassmembers class * {
    @org.simpleframework.xml.* *;
}
-keep class com.thegrizzlylabs.sardineandroid.** { *; }

# Kotlin 协程/元数据
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-keep class kotlin.Metadata { *; }
-keepclassmembers class ** {
    @kotlin.Metadata *;
}

# ==========================================
# 项目核心入口（main 源集）
# ==========================================

-keep class com.brycewg.asrkb.ime.AsrKeyboardService { *; }
-keep class com.brycewg.asrkb.ui.floating.FloatingAsrService { *; }

# ASR 接口与实现
-keep interface com.brycewg.asrkb.asr.AsrEngine { *; }
-keep interface com.brycewg.asrkb.asr.StreamingAsrEngine { *; }
-keep interface com.brycewg.asrkb.asr.StreamingAsrEngine$Listener { *; }

-keep class * implements com.brycewg.asrkb.asr.AsrEngine {
    public <init>(...);
    public *;
}

# BuildConfig
-keep class com.brycewg.asrkb.BuildConfig { *; }

# Shizuku：PrivilegedKeepAliveStarter 通过反射调用 Shizuku.newProcess
-keepclassmembers class rikka.shizuku.Shizuku {
    *** newProcess(java.lang.String[], java.lang.String[], java.lang.String);
}
