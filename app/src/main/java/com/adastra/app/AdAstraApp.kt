package com.adastra.app

import android.app.Application
import com.adastra.app.data.Repository
import com.adastra.app.news.NewsRepository
import com.adastra.app.notify.NewsAlarm
import com.adastra.app.notify.Reminder
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
    }
}
