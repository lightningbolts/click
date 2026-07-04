package compose.project.click.click.ui.components.native

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class NativeUiExpectActualAuditTest {

    private val moduleRoot: File by lazy {
        val cwd = File(System.getProperty("user.dir") ?: ".")
        when {
            cwd.resolve("src/commonMain").isDirectory -> cwd
            cwd.resolve("composeApp/src/commonMain").isDirectory -> cwd.resolve("composeApp")
            else -> cwd
        }
    }

    private val expectBases = listOf(
        "NativeNavButton",
        "NativeContextMenuBox",
        "NativeTextInputRow",
        "NativeCallPreviewHost",
    )

    @Test
    fun eachNativeExpectHasIosAndAndroidActual() {
        expectBases.forEach { baseName ->
            val expectFile = moduleRoot.resolve(
                "src/commonMain/kotlin/compose/project/click/click/ui/components/native/$baseName.kt",
            )
            val iosActual = moduleRoot.resolve(
                "src/iosMain/kotlin/compose/project/click/click/ui/components/native/$baseName.ios.kt",
            )
            val androidActual = moduleRoot.resolve(
                "src/androidMain/kotlin/compose/project/click/click/ui/components/native/$baseName.android.kt",
            )
            assertTrue(expectFile.exists(), "Missing expect for $baseName")
            assertTrue(iosActual.exists(), "Missing iOS actual for $baseName")
            assertTrue(androidActual.exists(), "Missing Android actual for $baseName")
            assertTrue(expectFile.readText().contains("expect fun"), "expect fun missing in $baseName")
            assertTrue(iosActual.readText().contains("actual fun"), "iOS actual fun missing for $baseName")
            assertTrue(androidActual.readText().contains("actual fun"), "Android actual fun missing for $baseName")
        }
    }
}
