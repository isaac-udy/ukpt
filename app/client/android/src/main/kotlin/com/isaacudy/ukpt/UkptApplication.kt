package com.isaacudy.ukpt

import android.app.Application

class UkptApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        UkptNavigation.installNavigationController(this)
    }
}
