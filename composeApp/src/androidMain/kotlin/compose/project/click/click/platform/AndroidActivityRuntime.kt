package compose.project.click.click.platform

import android.app.Activity
import android.content.Context
import java.lang.ref.WeakReference

/** Tracks the foreground [Activity] for platform helpers (e.g. Google Sign-In). */
object AndroidActivityRuntime {
    private var applicationContext: Context? = null
    private var currentActivityRef: WeakReference<Activity>? = null

    fun init(
        context: Context,
        activity: Activity? = null,
    ) {
        applicationContext = context.applicationContext
        if (activity != null) {
            currentActivityRef = WeakReference(activity)
        }
    }

    fun currentActivity(): Activity? = currentActivityRef?.get()

    fun appContext(): Context? = applicationContext
}
