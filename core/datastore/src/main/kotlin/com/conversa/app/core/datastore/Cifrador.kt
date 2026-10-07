package com.conversa.app.core.datastore

import android.content.Context
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager

/** Cifra e decifra bytes. Separado em interface para os testes usarem um falso. */
interface Cifrador {
    fun cifrar(dados: ByteArray): ByteArray

    fun decifrar(dados: ByteArray): ByteArray
}

/**
 * AES-256-GCM (Tink) com a chave mestra no Android Keystore. A chave nunca sai
 * do aparelho, então um backup ou uma cópia do arquivo não servem em outro lugar.
 */
class TinkCifrador(context: Context) : Cifrador {
    private val aead: Aead by lazy {
        AeadConfig.register()
        AndroidKeysetManager.Builder()
            .withSharedPref(context, CONJUNTO_CHAVES, ARQUIVO_PREFERENCIAS)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(CHAVE_MESTRA)
            .build()
            .keysetHandle
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    override fun cifrar(dados: ByteArray): ByteArray = aead.encrypt(dados, ASSOCIADO)

    override fun decifrar(dados: ByteArray): ByteArray = aead.decrypt(dados, ASSOCIADO)

    private companion object {
        const val CONJUNTO_CHAVES = "conversa_sessao_chaves"
        const val ARQUIVO_PREFERENCIAS = "conversa_cripto"
        const val CHAVE_MESTRA = "android-keystore://conversa_chave_mestra"
        val ASSOCIADO = "conversa.sessao".toByteArray()
    }
}
