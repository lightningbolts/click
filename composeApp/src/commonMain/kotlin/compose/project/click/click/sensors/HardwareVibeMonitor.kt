package compose.project.click.click.sensors

expect class HardwareVibeMonitor() {
    suspend fun takeSnapshot(): HardwareVibeSnapshot
}
