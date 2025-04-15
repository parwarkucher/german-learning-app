package com.parwar.german_learning.data.dao

import androidx.room.*
import com.parwar.german_learning.data.models.Dialog
import kotlinx.coroutines.flow.Flow

@Dao
interface DialogDao {
    @Query("SELECT * FROM dialogs")
    fun getAllDialogs(): Flow<List<Dialog>>

    @Insert
    suspend fun insertDialog(dialog: Dialog)

    @Update
    suspend fun updateDialog(dialog: Dialog)

    @Delete
    suspend fun deleteDialog(dialog: Dialog)

    @Query("SELECT * FROM dialogs WHERE id = :id")
    suspend fun getDialogById(id: Long): Dialog?
}
