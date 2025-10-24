package com.example.conversaandroid.data.local.dao

import androidx.room.*
import com.seudominio.conversa.data.local.entities.ConteudoEntity
import com.seudominio.conversa.data.local.entities.MensagemEntity
import com.seudominio.conversa.data.local.entities.MensagemWithConteudos
import kotlinx.coroutines.flow.Flow

@Dao
interface MensagemDao {

    @Transaction
    @Query("SELECT * FROM mensagens WHERE conversaId = :conversaId ORDER BY inserida DESC LIMIT :limit")
    suspend fun getMensagensByConversa(conversaId: Int, limit: Int = 50): List<MensagemWithConteudos>

    @Transaction
    @Query("SELECT * FROM mensagens WHERE conversaId = :conversaId ORDER BY inserida DESC")
    fun getMensagensFlowByConversa(conversaId: Int): Flow<List<MensagemWithConteudos>>

    @Query("SELECT * FROM mensagens WHERE id = :id")
    suspend fun getMensagemById(id: Int): MensagemEntity?

    @Transaction
    @Query("SELECT * FROM mensagens WHERE id = :id")
    suspend fun getMensagemWithConteudos(id: Int): MensagemWithConteudos?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMensagem(mensagem: MensagemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMensagens(mensagens: List<MensagemEntity>)

    @Update
    suspend fun updateMensagem(mensagem: MensagemEntity)

    @Delete
    suspend fun deleteMensagem(mensagem: MensagemEntity)

    @Query("DELETE FROM mensagens WHERE conversaId = :conversaId")
    suspend fun deleteMensagensByConversa(conversaId: Int)

    @Query("UPDATE mensagens SET recebida = 1 WHERE id = :mensagemId")
    suspend fun marcarComoRecebida(mensagemId: Int)

    @Query("UPDATE mensagens SET visualizada = 1 WHERE id = :mensagemId")
    suspend fun marcarComoVisualizada(mensagemId: Int)

    @Query("UPDATE mensagens SET visualizada = 1 WHERE conversaId = :conversaId AND remetenteId != :usuarioId")
    suspend fun marcarMensagensComoVisualizadas(conversaId: Int, usuarioId: Int)

    // Conteudo operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConteudo(conteudo: ConteudoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConteudos(conteudos: List<ConteudoEntity>)

    @Query("SELECT * FROM conteudos WHERE mensagemId = :mensagemId ORDER BY ordem")
    suspend fun getConteudosByMensagem(mensagemId: Int): List<ConteudoEntity>

    @Delete
    suspend fun deleteConteudo(conteudo: ConteudoEntity)
}