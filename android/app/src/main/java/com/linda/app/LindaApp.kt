package com.linda.app

import android.app.Application
import com.linda.app.core.data.LindaDatabase

/**
 * Application class. It owns the one Room database so every screen, and later
 * the SMS receiver, shares the same instance.
 */
class LindaApp : Application() {
    val database: LindaDatabase by lazy { LindaDatabase.create(this) }
}
