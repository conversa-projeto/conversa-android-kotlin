package com.conversa.app.core.ui.componentes

import androidx.compose.runtime.staticCompositionLocalOf
import com.conversa.app.core.model.PermissoesDeUso

/**
 * As permissões de uso de quem está logado (GER-09), dadas na raiz de cada Activity: cada tela
 * esconde o que a pessoa não pode, como o web. Sem quem dê, tudo pode.
 */
val LocalPermissoesDeUso = staticCompositionLocalOf { PermissoesDeUso() }
