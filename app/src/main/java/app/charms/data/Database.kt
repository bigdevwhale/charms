package app.charms.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * One of the child's everyday deeds: a page of the grimoire. It lights up at [magicHour]
 * (minutes after midnight); charmed before that it comes out in gold.
 */
@Entity
data class Deed(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    /** [app.charms.domain.Part] name: which spell of the day it belongs to. */
    val part: String,
    val magicHour: Int,
    /** Days of the week it is asked for: bit 0 = Monday … bit 6 = Sunday. */
    val days: Int = 0b1111111,
    /** Charmed with a picture from the phone's camera, or just sealed with a touch. */
    val photo: Boolean = true,
    /** What to put in the picture ("an empty plate"). */
    val hint: String = "",
    /** Index into [app.charms.art.Sigils]. */
    val sigil: Int = 0,
    /** Index into [app.charms.domain.Creature]: the deed's patron. */
    val creature: Int = 0,
    val sort: Int = 0,
    val archived: Boolean = false,
)

/** A deed charmed on a day. [tier]: 3 gold, 2 silver, 1 ink. */
@Entity(indices = [Index(value = ["deedId", "day"], unique = true), Index("day")])
data class Charm(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deedId: Long,
    /** Epoch day. */
    val day: Long,
    val at: Long,
    val tier: Int,
    /** The engraving in the vault, or "" for a seal. */
    val file: String = "",
    /** The mentor saw the picture wasn't the deed: the charm counts for nothing. */
    val blot: Boolean = false,
    /** A creature that came to visit this engraving (three golds in a row), or -1. */
    val visitor: Int = -1,
)

/** The mentor had to ring the bell for a deed on a day: whatever comes after is ink. */
@Entity(indices = [Index(value = ["deedId", "day"], unique = true)])
data class Bell(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deedId: Long,
    val day: Long,
    val at: Long,
)

/** A real reward on the potion shelf. [level]: 1 small, 2 middling, 3 great. */
@Entity
data class Potion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val reward: String,
    val level: Int,
    /** Index into the bottle colours. */
    val hue: Int = 0,
    val archived: Boolean = false,
)

/** A week's cauldron brewed into a potion. */
@Entity(indices = [Index(value = ["week"], unique = true)])
data class Brew(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Epoch day of the week's Monday. */
    val week: Long,
    val potionId: Long,
    val level: Int,
    val at: Long,
    /** The mentor has given the reward. */
    val given: Boolean = false,
)

/** A day when the mana ran out (ill, a hard day): nothing is asked, nothing is lost. */
@Entity
data class Rest(@PrimaryKey val day: Long)

@Dao
interface CharmsDao {
    @Query("SELECT * FROM Deed WHERE archived = 0 ORDER BY magicHour, sort, id")
    fun deedsFlow(): Flow<List<Deed>>

    @Query("SELECT * FROM Deed ORDER BY magicHour, sort, id")
    fun allDeedsFlow(): Flow<List<Deed>>

    @Query("SELECT * FROM Deed ORDER BY magicHour, sort, id")
    suspend fun allDeeds(): List<Deed>

    @Query("SELECT * FROM Deed WHERE archived = 0 ORDER BY magicHour, sort, id")
    suspend fun deeds(): List<Deed>

    @Query("SELECT * FROM Deed WHERE id = :id")
    suspend fun deed(id: Long): Deed?

    @Insert
    suspend fun insert(d: Deed): Long

    @Update
    suspend fun update(d: Deed)

    @Query("SELECT * FROM Charm ORDER BY day DESC, at DESC")
    fun charmsFlow(): Flow<List<Charm>>

    @Query("SELECT * FROM Charm")
    suspend fun charms(): List<Charm>

    @Query("SELECT * FROM Charm WHERE day >= :from AND day <= :to")
    suspend fun charms(from: Long, to: Long): List<Charm>

    @Query("SELECT * FROM Charm WHERE deedId = :deed AND day = :day")
    suspend fun charm(deed: Long, day: Long): Charm?

    @Query("SELECT * FROM Charm WHERE id = :id")
    suspend fun charm(id: Long): Charm?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(c: Charm): Long

    @Update
    suspend fun update(c: Charm)

    @Delete
    suspend fun delete(c: Charm)

    @Query("SELECT * FROM Bell")
    fun bellsFlow(): Flow<List<Bell>>

    @Query("SELECT * FROM Bell WHERE deedId = :deed AND day = :day")
    suspend fun bell(deed: Long, day: Long): Bell?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(b: Bell): Long

    @Query("SELECT * FROM Potion WHERE archived = 0 ORDER BY level, id")
    fun potionsFlow(): Flow<List<Potion>>

    @Query("SELECT * FROM Potion ORDER BY level, id")
    suspend fun allPotions(): List<Potion>

    @Insert
    suspend fun insert(p: Potion): Long

    @Update
    suspend fun update(p: Potion)

    @Query("SELECT * FROM Brew ORDER BY week DESC")
    fun brewsFlow(): Flow<List<Brew>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(b: Brew): Long

    @Update
    suspend fun update(b: Brew)

    @Query("SELECT * FROM Rest")
    fun restsFlow(): Flow<List<Rest>>

    @Query("SELECT * FROM Rest")
    suspend fun rests(): List<Rest>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(r: Rest)

    @Delete
    suspend fun delete(r: Rest)
}

@Database(entities = [Deed::class, Charm::class, Bell::class, Potion::class, Brew::class, Rest::class], version = 1, exportSchema = true)
abstract class CharmsDatabase : RoomDatabase() {
    abstract fun dao(): CharmsDao

    companion object {
        fun create(context: Context): CharmsDatabase =
            Room.databaseBuilder(context, CharmsDatabase::class.java, "charms.db").build()
    }
}
