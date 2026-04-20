package compose.project.click.click.platform

actual object InteractiveBackKeyboardFollow {
    actual fun setDismissProgress(progress: Float) {
        // System IME cannot be interactively translated from Compose on Android.
    }

    actual fun reset() {}
}
