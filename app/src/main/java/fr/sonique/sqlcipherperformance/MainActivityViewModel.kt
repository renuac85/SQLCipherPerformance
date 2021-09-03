/*
 * Copyright (C) 2020 Sonique Software.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.sonique.sqlcipherperformance

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.*

class MainActivityViewModel(private val context: Context) : ViewModel() {

	companion object {
		const val DEFAULT_LENGTH = 1000
		const val RETURN_INTERRUPTED = -1L
	}

	private var ioScope = CoroutineScope(Dispatchers.IO)

	internal var encrypted = false
	internal var encryptedWithMemorySecurity = false

	internal var runAll = false

	private var length = DEFAULT_LENGTH

	private val db: AppDatabase
		get() {
			return AppDatabase.getInstance(context, encrypted, encryptedWithMemorySecurity)
		}

	private var _querySize = MutableLiveData<Int>().apply { postValue(DEFAULT_LENGTH) }
	private var _results = MutableLiveData<String>()
	private var _enableUI = MutableLiveData<Boolean>()

	val querySize: LiveData<Int> = _querySize
	val results: LiveData<String> = _results
	val enableUI: LiveData<Boolean> = _enableUI

	private fun setUIEnabled(enabled: Boolean) {
		_enableUI.postValue(enabled)
	}

	internal fun updateQuerySize(querySize: Int) {
		length = querySize
		_querySize.postValue(length)
	}

	private fun appendResults(string: String) {
		_results.postValue(_results.value + string + "\n")
	}

	private fun clearResults() {
		// force the update immediately, so if append is called too fast
		// the content is gone anyway
		_results.value = ""
		_results.postValue("")
	}

	private fun runInsertsTests() {
		ioScope = CoroutineScope(Dispatchers.IO)
		ioScope.launch {
			if (runAll) {
				encrypted = false
				encryptedWithMemorySecurity = false
				val noEncryption = runInsertRounds()
				if (noEncryption == RETURN_INTERRUPTED) {
					return@launch
				}

				encrypted = true
				val encrypted = runInsertRounds()
				if (encrypted == RETURN_INTERRUPTED) {
					return@launch
				}

				encryptedWithMemorySecurity = true
				val encryptedWithMemorySecurity = runInsertRounds()
				if (encryptedWithMemorySecurity == RETURN_INTERRUPTED) {
					return@launch
				}

				val encDiff =
					percentageDifferenceWithBase(noEncryption.toDouble(), encrypted.toDouble())
				val encWithMemDiff = percentageDifferenceWithBase(
					noEncryption.toDouble(),
					encryptedWithMemorySecurity.toDouble()
				)

				appendResults(
					"\n\n" +
							"Inserts\n" +
							"No Encryption (base):      ${noEncryption}ms \n" +
							"Encrypted:                 ${encrypted}ms ${encDiff}%\n" +
							"Encrypted+Memory Security: ${encryptedWithMemorySecurity}ms ${encWithMemDiff}%\n"
				)
			} else {
				runInsertRounds()
			}


			setUIEnabled(true)

		}
	}

	private fun percentageDifferenceWithBase(base: Double, actual: Double): String {
		// calculate the difference between base number (no-encryption)
		// with actual (encryption)
		return String.format("%.2f", actual / base * 100.0 - 100.0)
	}

	private fun runSelectsIndexedTests() {
		ioScope = CoroutineScope(Dispatchers.IO)
		ioScope.launch {

			if (runAll) {
				encrypted = false
				encryptedWithMemorySecurity = false
				cleanStartForSelect()
				val noEncryption = runSelectIndexed()
				if (noEncryption == RETURN_INTERRUPTED) {
					return@launch
				}

				encrypted = true
				cleanStartForSelect()
				val encrypted = runSelectIndexed()
				if (encrypted == RETURN_INTERRUPTED) {
					return@launch
				}

				encryptedWithMemorySecurity = true
				cleanStartForSelect()
				val encryptedWithMemorySecurity = runSelectIndexed()
				if (encryptedWithMemorySecurity == RETURN_INTERRUPTED) {
					return@launch
				}

				val encDiff =
					percentageDifferenceWithBase(noEncryption.toDouble(), encrypted.toDouble())
				val encWithMemDiff = percentageDifferenceWithBase(
					noEncryption.toDouble(),
					encryptedWithMemorySecurity.toDouble()
				)

				appendResults(
					"\n\n" +
							"Selects indexed \n" +
							"No Encryption (base):      ${noEncryption}ms \n" +
							"Encrypted:                 ${encrypted}ms ${encDiff}%\n" +
							"Encrypted+Memory Security: ${encryptedWithMemorySecurity}ms ${encWithMemDiff}%\n"
				)

			} else {
				cleanStartForSelect()
				runSelectIndexed()
			}

			setUIEnabled(true)
		}
	}

	internal fun onCancelClicked() {
		ioScope.cancel()
		appendResults(
			"\n\n" +
					"User canceled the action\n" +
					"STOPPING...\n\n"
		)
		setUIEnabled(true)
	}

	internal fun onInsertsClicked() {
		setUIEnabled(false)
		clearResults()
		runInsertsTests()
	}

	internal fun onSelectIndexedClicked() {
		setUIEnabled(false)
		clearResults()
		runSelectsIndexedTests()
	}

	internal fun onSelectNoIndexClicked() {
		setUIEnabled(false)
		clearResults()
	}


	private suspend fun cleanStartForSelect() {
		deleteAll()
		insertData(0)
	}

	private suspend fun runInsertRounds(): Long {
		deleteAll()

		val rounds = 10
		var totalDuration = 0L
		for (i in 1..rounds) {
			if (ioScope.isActive) {
				totalDuration += insertData(i)
			} else {
				setUIEnabled(true)
				return RETURN_INTERRUPTED
			}
		}

		appendResults("Average: ${(totalDuration / rounds)}ms\n\n")

		return (totalDuration / rounds)
	}

	private suspend fun runSelectIndexed(): Long {
		val tutanotaTime = measure {
			for (i in 1..100) {
				val result = db.mailDao().find("tutanota", 10)
				if (result.isEmpty()) {
					Log.e("ViewModel", "EMPTY RESULT FOR tutanota")
				}
			}
		}
		appendResults("Select common word 100 times word with len $length: ${tutanotaTime}ms")


		val otherTime = measure {
			for (i in 1..100) {
				val term = "count$i"
				val result = db.mailDao().find(term, 10)
				if (result.isEmpty()) {
					Log.e("ViewModel", "EMPTY RESULT FOR $term")
				}
			}
		}
		appendResults("Select unique word 100 times with len $length: ${otherTime}ms")
		return tutanotaTime + otherTime
	}

	private suspend fun deleteAll() {
		if (ioScope.isActive) {
			appendResults("Clearing...")
			db.mailDao().deleteAll()

			appendResults(
				"Starting (encrypted: $encrypted, with memory encrypt: $encryptedWithMemorySecurity)...\n"
			)
		} else {
			setUIEnabled(true)
		}
	}

	private suspend fun insertData(round: Int): Long {
		// Build the Data Set first, so it is not counted into the SQL time
		val mailList = mutableListOf<Mail>()
		val batchTimes = mutableListOf<Long>()
		val time = measure {
			for (i in 0..length) {
				mailList.add(makeMail(i))
				if (i != 0 && i % 500 == 0) {
					val batchTime = measure {
						db.mailDao().insertMails(mailList)
					}
					batchTimes.add(batchTime)
					mailList.clear()
				}
			}
		}

		appendResults("Insert $length, round: $round : ${time}ms (avg. per 500: ${batchTimes.average()})")

		return time
	}

	private fun makeMail(i: Int): Mail {
		val subject = "Do you know Tutanota? count$i"
		val body = """Hi, look at tgit dhis:
	The challenge
	Apart from telephone, email is the most com­mon way of commu­nication. Nevertheless, the level of security, flexibility and user-friendli­ness of current email systems does not fit to­day's needs, especially in business life. Many com­panies take the risk of sending confiden­tial data unencrypted via the internet. During the transmission process, emails can be inter­cepted and read or even manipulated – just like a postcard. Furthermore, current email systems can not ensure who originally sent the message. The problem is even compounde­d by the use of cloud com­puting.
	
	The Solution
	The solution for this challenge is Tutanota. Tu­tanota is the first absolutely secure, flexi­ble and easy-to-use webmail system in the world.
	
	Secure
	Tutanota offers an end to end encryption from the sender to the receiver. Only the user has access to his key. Therefore, Tutanota is abso­lutely secure. Tutanota protects your valu­able customer data, trade secrets, sales contacts and thereby your future busi­ness.
	
	Flexible
	Users can access Tutanota from anywhere via browser or smartphone. Being a cloud applica­tion Tutanota gives you all the advan­tages of cloud computing like high availability, scalabili­ty and cost reduction.
	
	Easy
	Tutanota has a wide set of features like search, sort, contacts, calendar and task man­agement – all of this data is fully en­crypted as well. At the same time it is easy and intu­itive to use.
	
	 Some links to urlify test@example.com www.tutanota.de https://tutanota.com http://mail.tutanota.com"""
		return Mail(
			rowid = i.toLong(),
			subject = subject,
			body = body,
		)
	}

	override fun onCleared() {
		ioScope.cancel()
		super.onCleared()
	}
}

private inline fun measure(action: () -> Unit): Long {
	val start = SystemClock.elapsedRealtime()

	action()

	val end = SystemClock.elapsedRealtime()
	return end - start
}