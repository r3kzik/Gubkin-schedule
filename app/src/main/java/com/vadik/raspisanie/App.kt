package com.vadik.raspisanie

import android.app.Application
import com.vadik.raspisanie.data.GubkinApi
import com.vadik.raspisanie.data.Repository
import com.vadik.raspisanie.data.Storage
import com.vadik.raspisanie.work.AppSync
import com.vadik.raspisanie.work.Notifier
import com.vadik.raspisanie.work.RefreshWorker
import java.io.File

class App : Application() {

    lateinit var api: GubkinApi
        private set
    lateinit var repo: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        api = GubkinApi(File(filesDir, "cookies.txt"))
        repo = Repository(api, Storage(File(filesDir, "schedule")))
        Notifier.createChannels(this)
        RefreshWorker.schedule(this)
        Thread { AppSync.afterDataChange(this) }.start()
    }

    companion object {
        /** Группа, которую приложение ищет при первом запуске. */
        const val DEFAULT_GROUP = "КВ-26-02"
        /** Часть названия факультета для автопоиска группы. */
        const val DEFAULT_FACULTY_HINT = "безопасност"
    }
}
