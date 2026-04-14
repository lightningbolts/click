package compose.project.click.click.proximity

import platform.Foundation.NSProcessInfo

actual fun isSimulatorOrEmulatorRuntime(): Boolean {
    val env = NSProcessInfo.processInfo.environment
    return env["SIMULATOR_DEVICE_NAME"] != null || env["SIMULATOR_UDID"] != null
}
