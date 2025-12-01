package compose.project.click.click.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.runtime.*
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
    zoom: Double,
    onPinTapped: (MapPin) -> Unit
) {
    val context = LocalContext.current
    var lastPinsHashCode by remember { mutableStateOf(pins.hashCode()) }
    var lastZoom by remember { mutableStateOf(zoom) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val webView = ctx.createWebView()
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true
            webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
            webView.settings.builtInZoomControls = true
            webView.settings.displayZoomControls = false
            webView.settings.setSupportZoom(true)
            webView.webChromeClient = object : WebChromeClient() {}

            val bridge = MapJsBridge { index ->
                if (index in pins.indices) {
                    onPinTapped(pins[index])
                }
            }
            webView.addJavascriptInterface(bridge, "AndroidBridge")

            // Initial load
            val html = htmlForPins(pins, zoom)
            webView.loadDataWithBaseURL(
                "https://demotiles.maplibre.org/",
                html,
                "text/html",
                "UTF-8",
                null
            )

            lastPinsHashCode = pins.hashCode()
            lastZoom = zoom
            webView
        },
        update = { webView ->
            if (pins.hashCode() != lastPinsHashCode) {
                webView.loadDataWithBaseURL(
                    "https://demotiles.maplibre.org/",
                    htmlForPins(pins, zoom),
                    "text/html",
                    "UTF-8",
                    null
                )
                lastPinsHashCode = pins.hashCode()
                lastZoom = zoom
            } else if (zoom != lastZoom) {
                webView.evaluateJavascript("if(window.setZoom) window.setZoom($zoom)", null)
                lastZoom = zoom
            }
        },
        onRelease = { webView ->
            webView.removeJavascriptInterface("AndroidBridge")
            webView.loadUrl("about:blank")
            webView.clearHistory()
            webView.destroy()
        }
    )
}

private fun htmlForPins(pins: List<MapPin>, zoom: Double): String {
    val center = pins.firstOrNull()?.let { "[${it.longitude}, ${it.latitude}]" } ?: "[-73.9855, 40.7580]"
    val z = zoom.coerceIn(0.0, 22.0)
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
              .marker { width: 20px; height: 20px; border-radius: 50%; background: ${"#1e88e5"}; border: 2px solid white; box-shadow: 0 0 4px rgba(0,0,0,0.25); }
              .marker.nearby { background: ${"#42a5f5"}; }
              .mapboxgl-ctrl-group { box-shadow: 0 2px 8px rgba(0,0,0,0.12); border-radius: 12px; overflow: hidden; }
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
                zoom: $z,
                minZoom: 0,
                maxZoom: 22
              });
              map.addControl(new maplibregl.NavigationControl({ visualizePitch: true }), 'top-left');

              if (navigator.geolocation) {
                navigator.geolocation.getCurrentPosition((pos) => {
                  const user = [pos.coords.longitude, pos.coords.latitude];
                  const el = document.createElement('div');
                  el.className = 'marker nearby';
                  new maplibregl.Marker({ element: el }).setLngLat(user).addTo(map);
                });
              }

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

              window.setZoom = (z) => {
                map.flyTo({ zoom: z, duration: 300 });
              };
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
