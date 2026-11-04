package org.koocheh

import android.app.Application

class KoochehApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
