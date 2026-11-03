# Shizuku starts this class by name in a separate process.
-keep class com.loop.launcher.privileged.PrivilegedService { <init>(...); }
-keep class com.loop.launcher.privileged.IPrivilegedService { *; }
-keep class com.loop.launcher.privileged.IPrivilegedService$* { *; }
