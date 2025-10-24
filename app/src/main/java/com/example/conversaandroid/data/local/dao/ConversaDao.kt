package com.example.conversaandroid.data.local.dao

import androidx.room.*
import com.seudominio.conversa.data.local.entities.ConversaEntity
import com.seudominio.conversa.data.local.entities.ConversaUsuarioEntity
import com.seudominio.conversa.data.local.entities.ConversaWithUsuarios
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversaDao {

    @Query("SELECT * FROM conversas ORDER BY ultimaMensagemData DESC")
    fun getAllConversas(): Flow<List<ConversaEntity>>

    @Query("SELECT * FROM conversas WHERE id = :id")
    suspend fun getConversaById(id: Int): ConversaEntity?

    @Transaction
    @Query("SELECT * FROM conversas WHERE id = :id")
    suspend fun getConversaWithUsuarios(id: Int): ConversaWithUsuarios?

    @Transaction
    @Query("SELECT * FROM conversas ORDER BY ultimaMensagemData DESC")
    fun getAllConversasWithUsuarios(): Flow<List<ConversaWithUsuarios>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversa(conversa: ConversaEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversas(conversas: List<ConversaEntity>)

    @Update
    suspend fun updateConversa(conversa: ConversaEntity)

    @Delete
    suspend fun deleteConversa(conversa: ConversaEntity)

    @Query("DELETE FROM conversas")
    suspend fun deleteAllConversas()

    @Query("UPDATE conversas SET mensagensSemVisualizar = :count WHERE id = :conversaId")
    suspend fun updateMensagensSemVisualizar(conversaId: Int, count: Int)

    @Query("UPDATE conversas SET ultimaMensagem = :texto, ultimaMensagemData = :data, ultimaMensagemId = :mensagemId WHERE id = :conversaId")
    suspend fun updateUltimaMensagem(conversaId: Int, texto: String, data: Long, mensagemId: Int)

    // ConversaUsuario operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversaUsuario(conversaUsuario: ConversaUsuarioEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversaUsuarios(conversaUsuarios: List<ConversaUsuarioEntity>)

    @Query("DELETE FROM conversa_usuarios WHERE conversaId = :conversaId")
    suspend fun deleteConversaUsuarios(conversaId: Int)
}