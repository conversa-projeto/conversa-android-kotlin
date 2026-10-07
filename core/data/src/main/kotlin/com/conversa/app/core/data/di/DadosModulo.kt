package com.conversa.app.core.data.di

import com.conversa.app.core.data.ServidorRepositorio
import com.conversa.app.core.data.SessaoRepositorio
import com.conversa.app.core.network.auth.TokenProvider
import com.conversa.app.core.network.config.ServerConfigProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DadosModulo {
    @Binds
    abstract fun configServidor(repositorio: ServidorRepositorio): ServerConfigProvider

    @Binds
    abstract fun token(repositorio: SessaoRepositorio): TokenProvider
}
