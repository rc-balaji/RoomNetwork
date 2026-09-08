# Room Network Mapper keeps the debug build transparent. Release shrinking is
# enabled; platform and Compose metadata are retained by their own rules.
-keep class com.laconfianza.roommapper.model.** { *; }
-keep class com.google.ar.core.** { *; }
