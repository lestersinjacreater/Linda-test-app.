# Rules for R8, the shrinker that makes linda.apk small (CI builds it with assembleRelease).
# R8 removes code nothing calls and renames the rest. The libraries we use (Compose, Room, WorkManager, Navigation)
# ship their own keep rules, and the app uses no reflection, so nothing extra is needed yet.
# If the shrunk app crashes where the debug one does not, a keep rule for the missing class goes here.

# Keep line numbers in crash reports, so a stack trace from the shrunk build still points at the right line.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
