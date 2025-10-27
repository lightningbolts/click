package compose.project.click.click.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

private fun Context.createWebView(): WebView = WebView(this)

private class MapJsBridge(
    private val onPinTapped: (Int) -> Unit
) {
    @JavascriptInterface
    fun onPinTapped(index: Int) {
        onPinTapped(index)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun PlatformMap(
    modifier: Modifier,
    pins: List<MapPin>,
    onPinTapped: (MapPin) -> Unit
) {
    val context = LocalContext.current

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val webView = ctx.createWebView()
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true
            webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
            webView.webChromeClient = WebChromeClient()

            val bridge = MapJsBridge { index ->
                if (index in pins.indices) {
                    onPinTapped(pins[index])
                }
            }
            webView.addJavascriptInterface(bridge, "AndroidBridge")

            // Initial load
            webView.loadDataWithBaseURL(
                "https://demotiles.maplibre.org/",
                htmlForPins(pins),
                "text/html",
                "UTF-8",
                null
            )
            webView
        },
        update = { webView ->
            // Reload full HTML when pins change (simple and robust)
            webView.loadDataWithBaseURL(
                "https://demotiles.maplibre.org/",
                htmlForPins(pins),
                "text/html",
                "UTF-8",
                null
            )
        },
        onRelease = { webView ->
            webView.removeJavascriptInterface("AndroidBridge")
            webView.loadUrl("about:blank")
            webView.clearHistory()
            webView.destroy()
        }
    )

    // Lifecycle handled by onRelease
    DisposableEffect(Unit) {
        onDispose { /* no-op */ }
    }
}

private fun htmlForPins(pins: List<MapPin>): String {
    val center = pins.firstOrNull()?.let { "[${it.longitude}, ${it.latitude}]" } ?: "[-73.9855, 40.7580]"
    val zoom = if (pins.isNotEmpty()) 12 else 10
    val pinsJson = pins.mapIndexed { index, p ->
        """{"title": "${p.title.escapeJson()}", "lat": ${p.latitude}, "lng": ${p.longitude}, "idx": $index, "nearby": ${p.isNearby}}"""
    }.joinToString(prefix = "[", postfix = "]")

    return """
        <!doctype html>
        <html>
          <head>
            <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, user-scalable=no\" />
            <link href=\"https://unpkg.com/maplibre-gl@3.6.1/dist/maplibre-gl.css\" rel=\"stylesheet\" />
            <style>
              html, body, #map { height:100%; width:100%; margin:0; padding:0; }
              .marker { width: 20px; height: 20px; border-radius: 50%; background: #1e88e5; border: 2px solid white; box-shadow: 0 0 4px rgba(0,0,0,0.3); }
              .marker.nearby { background: #42a5f5; }
            </style>
          </head>
          <body>
            <div id=\"map\"></div>
            <script src=\"https://unpkg.com/maplibre-gl@3.6.1/dist/maplibre-gl.js\"></script>
            <script>
              const pins = $pinsJson;
              const map = new maplibregl.Map({
                container: 'map',
                style: 'https://demotiles.maplibre.org/style.json',
                center: $center,
                zoom: $zoom
              });
              map.addControl(new maplibregl.NavigationControl({ visualizePitch: true }));

              map.on('load', () => {
                pins.forEach(p => {
                  const el = document.createElement('div');
                  el.className = 'marker' + (p.nearby ? ' nearby' : '');
                  el.addEventListener('click', () => {
                    if (window.AndroidBridge && AndroidBridge.onPinTapped) {
                      AndroidBridge.onPinTapped(p.idx);
                    }
                  });
                  new maplibregl.Marker({ element: el })
                    .setLngLat([p.lng, p.lat])
                    .setPopup(new maplibregl.Popup({ offset: 12 }).setText(p.title))
                    .addTo(map);
                });
              });
            </script>
          </body>
        </html>
    """.trimIndent()
}

private fun String.escapeJson(): String =
    this.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
