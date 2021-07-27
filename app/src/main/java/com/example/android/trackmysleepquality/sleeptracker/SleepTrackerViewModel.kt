/*
 * Copyright 2018, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.trackmysleepquality.sleeptracker

import android.app.Application
import androidx.lifecycle.*
import com.example.android.trackmysleepquality.database.SleepDatabaseDao
import com.example.android.trackmysleepquality.database.SleepNight
import com.example.android.trackmysleepquality.formatNights
import kotlinx.coroutines.*

/**
 * ViewModel for SleepTrackerFragment.
 */
class SleepTrackerViewModel(
        val database: SleepDatabaseDao,
        application: Application) : AndroidViewModel(application) {

    private var viewModelJob: Job = Job()

    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel()
    }

    //For explicitly saying that UI coroutines will run in the main thread
    private val uiScope = CoroutineScope(Dispatchers.Main + viewModelJob)

    private var tonight = MutableLiveData<SleepNight?>()

    private val _navigateToSleepQuality = MutableLiveData<SleepNight>()
    val navigateToSleepQuality: LiveData<SleepNight>
        get() = _navigateToSleepQuality

    private val _showSnackBarEvent = MutableLiveData<Boolean>()
    val showSnackBarEvent: LiveData<Boolean>
        get() = _showSnackBarEvent

    fun doneShowingSnackBar() {
        _showSnackBarEvent.value = false
    }

    /*
    * Transforms.map listens when the LiveData changes,
    * and them it can perform something with the result
    * */

    private val nights = database.getAllNights()

    val nightsString = Transformations.map(nights) {nights ->
        formatNights(nights, application.resources)
    }
    val startButtonVisible = Transformations.map(tonight) {
        it == null //If there's no tonight
    }
    val stopButtonVisible = Transformations.map(tonight) {
        it != null //If there's tonight
    }
    val clearButtonVisible = Transformations.map(nights) {
        it?.isNotEmpty()
    }

    init {
        initializeTonight()
    }

    fun doneNavigating() {
        _navigateToSleepQuality.value = null
    }

    /*Assign a value for tonight*/
    private fun initializeTonight() {
        uiScope.launch {
            tonight.value = getTonightFromDatabase()
        }
    }
    //Suspend function for initializeTonight
    private suspend fun getTonightFromDatabase(): SleepNight? {
        return withContext(Dispatchers.IO) {
            var night = database.getTonight()

            //It means this night has already ended
            if (night?.endTimeMillis != night?.startTimeMillis) {
                night = null
            }
            night
        }
    }

    /*Start a new night and create the coroutine for that*/
    fun onStartTracking() {
        uiScope.launch {
            val newSleepNight = SleepNight()

            insertNight(newSleepNight)

            tonight.value = getTonightFromDatabase()
        }
    }
    //Suspend function for onStartTracking

    private suspend fun insertNight(newSleepNight: SleepNight) {
        withContext(Dispatchers.IO) {
            database.insert(newSleepNight)
        }
    }

    /*Stop tracking current night*/
    fun onStopTracking() {
        uiScope.launch {
            //In case tonight is null, nothing has to be done
            val oldNight = tonight.value ?: return@launch //

            oldNight.endTimeMillis = System.currentTimeMillis()
            _navigateToSleepQuality.value = oldNight
            update(oldNight)
        }
    }
    //Suspend function to update old night in database
    private suspend fun update(oldNight: SleepNight) {
        withContext(Dispatchers.IO){
            database.update(oldNight)
        }
    }

    /*Clear all nights from database*/
    fun onClear() {
        uiScope.launch {
            clear()
            tonight.value = null
            _showSnackBarEvent.value = true
        }
    }
    //Suspend method from onClear, to clear nights from DB
    private suspend fun clear() {
        withContext(Dispatchers.IO) {
            database.clear()
        }
    }
}

