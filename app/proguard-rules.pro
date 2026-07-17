# Keep Room entities and DAOs
-keep class com.callguard.storage.** { *; }

# Keep data classes used by Gson/JSON (if any)
-keep class com.callguard.model.** { *; }

# Keep AccessibilityService
-keep class com.callguard.call.CallAccessibilityService { *; }

# Keep CallScreeningService  
-keep class com.callguard.call.CallScreeningService { *; }
