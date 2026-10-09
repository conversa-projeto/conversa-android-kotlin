package com.conversa.app.core.data.preferencias

import com.conversa.app.core.datastore.PreferenciasStore
import com.conversa.app.core.model.PreferenciaTema
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Preferências do aparelho escolhidas nas Configurações (8.5). Sobrevivem ao logout. */
@Singleton
class PreferenciasRepositorio @Inject constructor(private val store: PreferenciasStore) {
    val tema: Flow<PreferenciaTema> = store.tema.map { PreferenciaTema.de(it) }.distinctUntilChanged()

    suspend fun alterarTema(tema: PreferenciaTema) = store.salvarTema(tema.chave)
}
