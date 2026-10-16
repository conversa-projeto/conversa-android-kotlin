package com.conversa.app.core.data.conversas

import android.net.Uri
import com.conversa.app.core.data.anexos.AnexosRepositorio
import com.conversa.app.core.data.anexos.ImagemEmMemoria
import com.conversa.app.core.data.anexos.PreparadorAvatar
import com.conversa.app.core.model.TipoConteudo
import com.conversa.app.core.network.api.ConversaApi
import com.conversa.app.core.network.dto.AlterarConversaRequisicao
import com.conversa.app.core.network.http.chamarApi
import com.conversa.app.core.network.json.nuloExplicito
import javax.inject.Inject
import kotlinx.serialization.json.JsonPrimitive

/** Imagem ou emoji do grupo (CON-15, servidor `b8ea0c2`): um tira o outro. */
sealed interface AvatarGrupo {
    data class Imagem(val anexoId: Long) : AvatarGrupo

    data class Emoji(val emoji: String) : AvatarGrupo
}

/** A imagem escolhida não pôde ser lida (formato que o aparelho não abre, arquivo inacessível). */
class ImagemIlegivelException : Exception()

/**
 * Imagem ou emoji do grupo (FC-217), como o web (`PainelGrupo.vue`, `CreateGroupModal.vue`): a
 * imagem é recortada e reduzida a 256×256 em JPEG antes de subir (o mesmo preparo da foto de
 * perfil); `PATCH /conversa {id, avatar_anexo_id, emoji}` manda os dois campos, um deles nulo
 * (nulos os dois = "Remover"), e depois a lista é relida.
 */
class AvatarDoGrupo @Inject constructor(
    private val api: ConversaApi,
    private val anexos: AnexosRepositorio,
    private val conversas: ConversasRepositorio,
    private val preparador: PreparadorAvatar,
) {
    /** Prepara e sobe a imagem; devolve o id do anexo. */
    suspend fun enviarImagem(uri: Uri): Result<Long> {
        val jpeg = preparador.preparar(uri) ?: return Result.failure(ImagemIlegivelException())
        return anexos.enviar(ImagemEmMemoria(jpeg), TipoConteudo.IMAGEM).map { it.id }
    }

    /** Troca a imagem ou o emoji; nulo tira os dois. */
    suspend fun alterar(conversaId: Long, avatar: AvatarGrupo?): Result<Unit> = chamarApi {
        api.alterarConversa(
            AlterarConversaRequisicao(
                conversaId,
                avatarAnexoId = (avatar as? AvatarGrupo.Imagem)?.let { JsonPrimitive(it.anexoId) } ?: nuloExplicito,
                emoji = (avatar as? AvatarGrupo.Emoji)?.let { JsonPrimitive(it.emoji) } ?: nuloExplicito,
            ),
        )
    }.map { }.onSuccess { conversas.atualizar() }
}
