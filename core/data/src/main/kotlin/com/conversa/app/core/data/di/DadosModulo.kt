package com.conversa.app.core.data.di

import com.conversa.app.core.data.ServidorRepositorio
import com.conversa.app.core.data.SessaoRepositorio
import com.conversa.app.core.data.anexos.FontesArquivo
import com.conversa.app.core.data.anexos.FontesArquivoAndroid
import com.conversa.app.core.data.autenticacao.InfoDispositivo
import com.conversa.app.core.data.autenticacao.InfoDispositivoAndroid
import com.conversa.app.core.data.chamadas.ChamadasRemotas
import com.conversa.app.core.data.chamadas.ChamadasRepositorio
import com.conversa.app.core.data.mensagens.AgendadorEnvio
import com.conversa.app.core.data.mensagens.AgendadorEnvioWorkManager
import com.conversa.app.core.data.preferencias.PreferenciasRepositorio
import com.conversa.app.core.model.FonteConfigChamada
import com.conversa.app.core.network.auth.TokenProvider
import com.conversa.app.core.network.config.ServerConfigProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

@Module
@InstallIn(SingletonComponent::class)
abstract class DadosModulo {
    @Binds
    abstract fun configServidor(repositorio: ServidorRepositorio): ServerConfigProvider

    @Binds
    abstract fun token(repositorio: SessaoRepositorio): TokenProvider

    @Binds
    abstract fun agendadorEnvio(agendador: AgendadorEnvioWorkManager): AgendadorEnvio

    @Binds
    abstract fun fontesArquivo(fontes: FontesArquivoAndroid): FontesArquivo

    @Binds
    abstract fun infoDispositivo(info: InfoDispositivoAndroid): InfoDispositivo

    @Binds
    abstract fun chamadasRemotas(repositorio: ChamadasRepositorio): ChamadasRemotas

    @Binds
    abstract fun configChamada(repositorio: PreferenciasRepositorio): FonteConfigChamada

    companion object {
        /** Relógio injetável (testes controlam a hora). */
        @Provides
        fun relogio(): Clock = Clock.systemDefaultZone()
    }
}
