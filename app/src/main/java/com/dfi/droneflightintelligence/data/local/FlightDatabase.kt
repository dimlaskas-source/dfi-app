package com.dfi.droneflightintelligence.data.local

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "flight_sessions")
data class FlightSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val startedAtMs: Long,
    val endedAtMs: Long? = null,
    val maxAltitudeMeters: Double? = null,
    val maxDistanceMeters: Double? = null,
    val notes: String = "DJI Lito 1 / RC-N3"
)

@Entity(
    tableName = "flight_points",
    foreignKeys = [
        ForeignKey(
            entity = FlightSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class FlightPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val speedMs: Double?,
    val batteryPercent: Int?,
    val timestampMs: Long
)

@Dao
interface FlightDao {
    @Insert
    suspend fun insertSession(session: FlightSessionEntity): Long

    @Update
    suspend fun updateSession(session: FlightSessionEntity)

    @Insert
    suspend fun insertPoint(point: FlightPointEntity)

    @Query("SELECT * FROM flight_sessions ORDER BY startedAtMs DESC")
    fun observeSessions(): Flow<List<FlightSessionEntity>>

    @Query("SELECT * FROM flight_points WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    suspend fun getPointsForSession(sessionId: Long): List<FlightPointEntity>

    @Query("DELETE FROM flight_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)
}

@Database(
    entities = [FlightSessionEntity::class, FlightPointEntity::class],
    version = 1,
    exportSchema = false
)
abstract class FlightDatabase : RoomDatabase() {
    abstract fun flightDao(): FlightDao

    companion object {
        @Volatile
        private var INSTANCE: FlightDatabase? = null

        fun getInstance(context: Context): FlightDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FlightDatabase::class.java,
                    "dfi_flights.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
