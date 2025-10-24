package com.example.conversaandroid.data.local.dao

import androidx.room.*
import com.seudominio.conversa.data.local.entities.UsuarioEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UsuarioDao {

    @Query("SELECT * FROM usuarios WHERE id = :id")
    suspend fun getUsuarioById(id: Int): UsuarioEntity?

    @Query("SELECT * FROM usuarios")
    fun getAllUsuarios(): Flow<List<UsuarioEntity>>

    @Query("SELECT * FROM usuarios WHERE id IN (:ids)")
    suspend fun getUsuariosByIds(ids: List<Int>): List<UsuarioEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsuario(usuario: UsuarioEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsuarios(usuarios: List<UsuarioEntity>)

    @Update
    suspend fun updateUsuario(usuario: UsuarioEntity)

    @Delete
    suspend fun deleteUsuario(usuario: UsuarioEntity)

    @Query("DELETE FROM usuarios")
    suspend fun deleteAllUsuarios()
}