package com.conversa.app.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.conversa.app.core.datastore.ArquivoSessao
import com.conversa.app.core.datastore.Cifrador
import com.conversa.app.core.datastore.PreferenciasStore
import com.conversa.app.core.datastore.SessaoSerializer
import com.conversa.app.core.datastore.SessaoStore
import com.conversa.app.core.datastore.TinkCifrador
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ArmazenamentoModulo {
    @Provides
    @Singleton
    fun cifrador(@ApplicationContext context: Context): Cifrador = TinkCifrador(context)

    @Provides
    @Singleton
    fun sessaoStore(@ApplicationContext context: Context, cifrador: Cifrador): SessaoStore = SessaoStore(
        DataStoreFactory.create(
            serializer = SessaoSerializer(cifrador),
            // Arquivo ilegível (ex.: chave do Keystore perdida) = sem sessão; a pessoa entra de novo.
            corruptionHandler = ReplaceFileCorruptionHandler { ArquivoSessao() },
            produceFile = { context.dataStoreFile("sessao.bin") },
        ),
    )

    @Provides
    @Singleton
    fun preferenciasStore(@ApplicationContext context: Context): PreferenciasStore = PreferenciasStore(
        PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile("preferencias") }),
    )
}
