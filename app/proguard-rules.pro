# Trackly R8 / ProGuard rules.
# Room, WorkManager, DataStore and Play Billing ship their own consumer rules.

# Keep readable stack traces in Play Console crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Domain enums are persisted by name (Room / DataStore) and read back with valueOf().
-keepclassmembers enum com.example.trackly.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Manifest-registered components are kept automatically by AAPT2 rules.
