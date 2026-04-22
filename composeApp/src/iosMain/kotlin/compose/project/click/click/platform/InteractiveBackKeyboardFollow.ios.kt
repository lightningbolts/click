package compose.project.click.click.platform

/**
 * Intentional no-op on iOS.
 *
 * Translating non-content [UIWindow]s to follow interactive back was repeatedly breaking UIKit’s
 * keyboard lifecycle (dismiss animation smooth once, then abrupt; odd simulator jank). The
 * interactive-back route already slides in Compose; IME teardown is handled via focus in the
 * navigation layer instead of mutating system keyboard windows.
 */
actual object InteractiveBackKeyboardFollow {
    actual fun setSwipeTranslationX(translationXPx: Float) = Unit

    actual fun reset() = Unit
}
