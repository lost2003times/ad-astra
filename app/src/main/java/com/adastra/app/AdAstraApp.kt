package com.adastra.app

import android.app.Application
import com.adastra.app.data.Repository
import com.adastra.app.notify.Reminder
import java.io.File

class AdAstraApp : Application() {
    lateinit var repo: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        repo = Repository(File(filesDir, "adastra.json"))
        Reminder.createChannel(this)
    }
}
