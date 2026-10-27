# sing-box (gomobile) — классы вызываются из Go через JNI, их нельзя переименовывать
-keep class go.** { *; }
-keep class io.nekohasekai.** { *; }
-keep class com.apexvpn.client.vpn.** { *; }
-dontwarn go.**
-dontwarn io.nekohasekai.**
