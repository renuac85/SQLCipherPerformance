package fr.sonique.sqlcipherperformance

import androidx.room.*

@Entity(tableName = "mail")
@Fts4
data class Mail (
	@PrimaryKey
	val rowid: Long,
	val subject: String,
	val body: String,
)