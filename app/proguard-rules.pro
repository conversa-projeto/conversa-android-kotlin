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

# Registro do app (files/registro/): as pilhas de erro e as tags do Timber precisam dos nomes das
# classes e das linhas para fazer sentido sem o mapping do R8. Só não renomeia; o R8 ainda tira o que não é usado.
-keepattributes SourceFile, LineNumberTable
-keepnames class com.conversa.app.** { *; }
