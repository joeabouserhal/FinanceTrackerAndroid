# R8 rules for the release build (minify + shrinkResources enabled).
# Consumer rules shipped by Room, WorkManager, Ktor, supabase-kt,
# kotlinx.coroutines, androidx.credentials and androidx.lifecycle are applied
# automatically; these are the app-specific and belt-and-braces additions.

# Reflection-friendly attributes used by serialization / auth libraries.
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeInvisibleAnnotations

# kotlinx.serialization — official recommended rules. The sync engine parses
# raw JsonObject (compiler-plugin serializers), but keeping generated
# serializers costs nothing and future-proofs @Serializable DTOs.
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.joeabouserhal.financetracker.**$$serializer { *; }
-keepclassmembers class com.joeabouserhal.financetracker.** { *** Companion; }
-keepclasseswithmembers class com.joeabouserhal.financetracker.** { kotlinx.serialization.KSerializer serializer(...); }

# Google Credential Manager (Google ID token parsing).
-keep class com.google.android.libraries.identity.googleid.** { *; }

# Room entities are plain data classes (no reflection at runtime), but keep
# them defensively so the LWW sync mappers and DAOs can never be stripped.
-keepclassmembers class com.joeabouserhal.financetracker.data.local.entities.** { *; }

# Supabase session persistence (SharedPreferences-backed).
-keep class com.russhwolf.settings.** { *; }

# Keep BuildConfig fields used by SupabaseConfig.
-keep class com.joeabouserhal.financetracker.BuildConfig { *; }
