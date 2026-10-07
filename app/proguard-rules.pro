# Regras do R8 para o release.
# kotlinx.serialization: os serializers gerados são mantidos pelas regras que a
# própria biblioteca publica; aqui só o que não vem nelas.

# Retrofit (interfaces e anotações usadas por reflexão)
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# DTOs do contrato (nomes dos campos vêm de @SerialName, mas mantemos por segurança)
-keep class com.conversa.app.core.network.dto.** { *; }
