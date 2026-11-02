package compose.project.click.click.ui.chat // pragma: allowlist secret

import android.os.Build
import coil3.ComponentRegistry
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder

/** Animated GIF and (API 28+) animated WebP for KLIPY GIF messages. */
internal actual fun ComponentRegistry.Builder.addAnimatedImageDecoders() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        add(AnimatedImageDecoder.Factory())
    } else {
        add(GifDecoder.Factory())
    }
}
