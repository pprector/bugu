# Release R8 规则:保守保留序列化、WorkManager 与推送 SDK 的运行时入口。
# kotlinx-serialization 生成 serializer/Companion;当前代码使用显式 serializer,规则作为防回归保险。
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# WorkManager 通过类名反射实例化 Worker,不能混淆/裁剪构造入口。
-keep class com.bugu.schedule.ReportWorker { *; }

# WorkManager 内部使用 Room;生成的 WorkDatabase_Impl 由 Room 反射调用无参构造。
# 实测 R8 裁剪后会在 androidx.startup 初始化阶段抛 NoSuchMethodException。
-keep class androidx.work.impl.WorkDatabase_Impl { *; }
-keep class androidx.work.impl.WorkDatabase_Impl$* { *; }
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# 极光 SDK 组件在 Manifest/内部反射中使用,保留其类与入口;避免 R8 裁剪后推送通道失效。
-keep class cn.jpush.** { *; }
-keep class cn.jiguang.** { *; }
-dontwarn cn.jpush.**
-dontwarn cn.jiguang.**
