# ==========================================
# OSS 版本：仅优化体积，不混淆类名/方法名
# ==========================================

# 禁用混淆，保持所有类名和成员名
-dontobfuscate

# 开启优化（字节码层面）
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 3

# 移除日志（可选，减小体积）
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
}

# 打印优化信息（调试用）
-verbose
-printconfiguration build/outputs/proguard/oss-configuration.txt
-printusage build/outputs/proguard/oss-unused.txt
