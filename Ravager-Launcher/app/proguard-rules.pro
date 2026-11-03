# Shizuku starts this class by name in a separate process.
-keep class com.ravager.launcher.privileged.PrivilegedService { <init>(...); }
-keep class com.ravager.launcher.privileged.IPrivilegedService { *; }
-keep class com.ravager.launcher.privileged.IPrivilegedService$* { *; }
