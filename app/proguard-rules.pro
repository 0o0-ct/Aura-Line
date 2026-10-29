# AuraLine no usa reflexión sobre clases propias: las reglas por defecto de AGP/Compose bastan.
# Se conserva el modelo de dominio por si se serializa en el futuro.
-keep class com.auraline.core.domain.** { *; }
-dontwarn org.jetbrains.annotations.**

# Puente JNI: los nombres de los métodos nativos deben sobrevivir a R8.
-keepclasseswithmembernames class com.auraline.app.audio.NativeEngine { native <methods>; }
