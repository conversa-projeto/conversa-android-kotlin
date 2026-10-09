package com.conversa.app.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Sistema e Acessos (TODO 8.5), com as regras do web. */
class SistemaTest {
    private val original = ParametrosSistema(
        fcmProjetoId = "projeto",
        fcmEmail = "conta@projeto.test",
        fcmChaveConfigurada = true,
        turnForcarRelay = true,
        transcritorUrl = "http://transcritor:8000",
        transcritorIdioma = "pt",
        gravacaoDias = 30,
        s3Bucket = "anexos",
    )

    @Test
    fun `so vai o que mudou, e a chave so se foi digitada`() {
        assertThat(alteracaoDosParametros(original, original, novaChave = "  ")).isNull()

        val editado = original.copy(turnForcarRelay = false, transcritorUrl = "", gravacaoDias = 0)
        assertThat(alteracaoDosParametros(original, editado, novaChave = ""))
            .isEqualTo(AlteracaoParametros(turnForcarRelay = false, transcritorUrl = "", gravacaoDias = 0))

        assertThat(alteracaoDosParametros(original, original, novaChave = "-----BEGIN PRIVATE KEY-----"))
            .isEqualTo(AlteracaoParametros(fcmChave = "-----BEGIN PRIVATE KEY-----"))
    }

    @Test
    fun `dias de gravacao entre 0 e 36500`() {
        assertThat(diasDeGravacao("0")).isEqualTo(0)
        assertThat(diasDeGravacao(" 365 ")).isEqualTo(365)
        assertThat(diasDeGravacao("36500")).isEqualTo(36_500)
        assertThat(diasDeGravacao("36501")).isNull()
        assertThat(diasDeGravacao("-1")).isNull()
        assertThat(diasDeGravacao("dez")).isNull()
    }

    private val acessos = Acessos(
        permissoes = listOf(PermissaoSistema("parametros", "Ver e alterar"), PermissaoSistema("permissoes", "Conceder e retirar")),
        usuarios = listOf(
            UsuarioAcessos(1, "Ana Souza", "ana", emptySet()),
            UsuarioAcessos(2, "Bruno", "b.lima", setOf("parametros")),
        ),
        modoAberto = true,
    )

    @Test
    fun `busca por nome ou login`() {
        assertThat(filtrarUsuarios(acessos.usuarios, "souza").map { it.id }).containsExactly(1L)
        assertThat(filtrarUsuarios(acessos.usuarios, "LIMA").map { it.id }).containsExactly(2L)
        assertThat(filtrarUsuarios(acessos.usuarios, " ")).hasSize(2)
    }

    @Test
    fun `marcar Acessos para alguem encerra o modo aberto, Sistema nao`() {
        val comSistema = acessos.comPermissao(1, CodigoPermissao.PARAMETROS, concedida = true)
        assertThat(comSistema.usuarios[0].permissoes).containsExactly("parametros")
        assertThat(comSistema.modoAberto).isTrue()

        val comAcessos = comSistema.comPermissao(1, CodigoPermissao.PERMISSOES, concedida = true)
        assertThat(comAcessos.modoAberto).isFalse()

        assertThat(comAcessos.comPermissao(2, CodigoPermissao.PARAMETROS, concedida = false).usuarios[1].permissoes).isEmpty()
    }
}
