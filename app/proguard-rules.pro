# 智记 Release 混淆规则
#
# 总原则：**运行时靠反射或字符串找类的东西，一个都不能混淆掉**。
# 下面每一段都对应一个「混淆后必崩」的已知模式。

# ---------------- kotlinx.serialization ----------------
# 序列化靠编译期生成的 serializer，混淆后 Companion 与 $$serializer 必须保留，
# 否则解析模型返回的 JSON 时会抛 SerializationException。
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.ainote.** {
    *** Companion;
}
-keepclasseswithmembers class com.ainote.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.ainote.**$$serializer { *; }

# ---------------- Room ----------------
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.paging.**

# ---------------- Hilt / Dagger ----------------
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.internal.GeneratedComponent { *; }
-dontwarn dagger.hilt.**

# ---------------- OkHttp / Okio ----------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---------------- ML Kit ----------------
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**

# ---------------- 枚举 ----------------
# Room 用枚举的 name 存库；名字被混淆会让历史数据读不出来。
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepnames enum com.ainote.**

# ---------------- 数据模型 ----------------
# 这些类既进 Room 也进序列化，整体保留最稳妥。
-keep class com.ainote.core.database.entity.** { *; }
-keep class com.ainote.core.ai.model.** { *; }

# ---------------- 其他 ----------------
-dontwarn androidx.compose.**
-dontwarn kotlinx.coroutines.**
