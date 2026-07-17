package com.guangxia.filmtools.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "cameras")
data class CameraEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val model: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "film_rolls",
    foreignKeys = [ForeignKey(
        entity = CameraEntity::class,
        parentColumns = ["id"],
        childColumns = ["cameraId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("cameraId")],
)
data class FilmRollEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cameraId: Long,
    val filmType: String,
    val iso: Int,
    val loadedEpochDay: Long,
    val unloadedEpochDay: Long? = null,
    val notes: String = "",
)

data class CameraWithRolls(
    @Embedded val camera: CameraEntity,
    @Relation(parentColumn = "id", entityColumn = "cameraId") val rolls: List<FilmRollEntity>,
) {
    val currentRoll get() = rolls.firstOrNull { it.unloadedEpochDay == null }
    val history get() = rolls.sortedByDescending { it.loadedEpochDay }
}
