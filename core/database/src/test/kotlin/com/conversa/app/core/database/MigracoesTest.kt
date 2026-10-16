package com.conversa.app.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * As migrações automáticas do Room contra os esquemas exportados em `schemas/` (FC-110): cada
 * versão chega à seguinte com o esquema que o Room espera, e o que estava gravado continua lá.
 * Usa o driver por arquivo: o do `SupportSQLite` não reconhece o caminho do Windows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigracoesTest {
    private val instrumentacao = InstrumentationRegistry.getInstrumentation()

    private fun ajudante(nome: String) = MigrationTestHelper(
        instrumentation = instrumentacao,
        file = instrumentacao.targetContext.getDatabasePath(nome),
        driver = AndroidSQLiteDriver(),
        databaseClass = ConversaBanco::class,
    )

    @Test
    fun `cada versao migra para a seguinte com o esquema esperado`() {
        for (versao in 1 until VERSAO_ATUAL) {
            val ajudante = ajudante("migracao-$versao.db")
            ajudante.createDatabase(versao).close()
            ajudante.runMigrationsAndValidate(versao + 1).close()
        }
    }

    @Test
    fun `da versao 1 ate a atual os dados ficam e as colunas novas tem o padrao`() {
        val ajudante = ajudante("migracao.db")
        ajudante.createDatabase(1).use { conexao ->
            conexao.execSQL("INSERT INTO conversa (id, tipo, ultimaMensagemId, naoLidas) VALUES (42, 2, 900, 3)")
            conexao.execSQL(
                "INSERT INTO mensagem (id, conversaId, remetenteId, remetente, inserida, dataEfetiva, recebida, visualizada, " +
                    "reproduzida, enviando, falhou) VALUES (900, 42, 7, 'Ana', 1000, 1000, 1, 0, 0, 0, 0)",
            )
            conexao.execSQL(
                "INSERT INTO conteudo (mensagemId, ordem, tipo, conteudo, nome, extensao, transcricaoStatus, transcricao) " +
                    "VALUES (900, 1, 1, 'oi', '', '', 0, '')",
            )
        }

        ajudante.runMigrationsAndValidate(VERSAO_ATUAL).use { conexao ->
            conexao.linha("SELECT naoLidas, emoji, chamada FROM conversa WHERE id = 42") {
                assertThat(it.getLong(0)).isEqualTo(3)
                assertThat(it.isNull(1)).isTrue()
                assertThat(it.getLong(2)).isEqualTo(0)
            }
            conexao.linha("SELECT remetente, postagemId FROM mensagem WHERE id = 900") {
                assertThat(it.getText(0)).isEqualTo("Ana")
                assertThat(it.isNull(1)).isTrue()
            }
            conexao.linha("SELECT conteudo, chatChamadaId FROM conteudo WHERE mensagemId = 900") {
                assertThat(it.getText(0)).isEqualTo("oi")
                assertThat(it.isNull(1)).isTrue()
            }
        }
    }

    /** Confere a única linha que a [consulta] devolve. */
    private fun SQLiteConnection.linha(consulta: String, conferir: (SQLiteStatement) -> Unit) {
        prepare(consulta).use { linha ->
            assertThat(linha.step()).isTrue()
            conferir(linha)
        }
    }

    private companion object {
        const val VERSAO_ATUAL = 8
    }
}
