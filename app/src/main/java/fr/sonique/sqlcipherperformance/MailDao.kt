package fr.sonique.sqlcipherperformance

import androidx.room.*

@Dao
interface MailDao {
	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun insertMails(mails: List<Mail>)

	@Query("SELECT rowid, subject, body FROM mail WHERE subject MATCH :find OR body MATCH :find LIMIT :limit")
	suspend fun find(find: String, limit: Long): List<Mail>

	@Query("DELETE FROM mail")
	suspend fun deleteAll()
}