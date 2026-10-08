package com.conversa.app.feature.chat

import com.conversa.app.core.model.Contato
import com.conversa.app.core.model.Conversa
import com.conversa.app.core.model.TipoConversa
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Destinos do "Encaminhar" (como o `ForwardMessageModal.vue`). */
class EncaminharTest {
    private fun conversa(id: Long, tipo: TipoConversa, descricao: String? = null, nome: String? = null, destinatario: Long? = null) =
        Conversa(id, tipo, descricao, nome, destinatario, 0, null, null, 0, null, null, null)

    private val conversas = listOf(
        conversa(1, TipoConversa.GRUPO, descricao = "Equipe"),
        conversa(2, TipoConversa.DIRETA, nome = "Bruno", destinatario = 8),
        conversa(3, TipoConversa.GRUPO, descricao = " "),
    )
    private val contatos = listOf(
        Contato(8, "Bruno", "bruno", "bruno@exemplo.com", null),
        Contato(9, "Carla", "carla", "carla@exemplo.com", null),
    )

    @Test
    fun `conversas menos a de origem, depois os contatos sem direta`() {
        val destinos = destinosParaEncaminhar(conversas, contatos, origem = 1)

        assertThat(destinos.map { it.chave }).containsExactly("conversa-2", "conversa-3", "contato-9").inOrder()
        assertThat((destinos[1] as DestinoEncaminhar.ParaConversa).nome).isNull()
    }

    @Test
    fun `busca no nome e no email, sem diferenciar maiusculas`() {
        val destinos = destinosParaEncaminhar(conversas, contatos, origem = 0)

        assertThat(filtrarDestinos(destinos, "EQUI").map { it.chave }).containsExactly("conversa-1")
        assertThat(filtrarDestinos(destinos, "carla@").map { it.chave }).containsExactly("contato-9")
        assertThat(filtrarDestinos(destinos, "  ")).hasSize(destinos.size)
    }
}
