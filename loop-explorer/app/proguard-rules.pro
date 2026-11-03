# Shizuku starts this class by name in a separate process.
-keep class com.loop.explorer.privileged.PrivilegedService { <init>(...); }
-keep class com.loop.explorer.privileged.IPrivilegedService { *; }
-keep class com.loop.explorer.privileged.IPrivilegedService$* { *; }
