package dev.motioncare.app

import android.app.Application

class MotionCareApplication : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }
}
