package com.adastra.app

import android.app.Application
import com.adastra.app.data.Repository
import com.adastra.app.news.NewsRepository
import com.adastra.app.notify.NewsAlarm
import com.adastra.app.notify.Reminder
import com.adastra.app.notify.TimerNotice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

class AdAstraApp : Application() {
    lateinit var repo: Repository
        private set
    lateinit var news: NewsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repo = Repository(File(filesDir, "adastra.json"))
        news = NewsRepository(File(filesDir, "news.json"))
        Reminder.createChannel(this)
        NewsAlarm.createChannel(this)
        TimerNotice.createChannel(this)
        com.adastra.app.notify.Focus.resume(this)
        // Keep the running-timer notification in step with the data, however the timer changes.
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            repo.data.map { it.timer }.distinctUntilChanged().collect { TimerNotice.sync(this@AdAstraApp, it) }
        }
    }
}
