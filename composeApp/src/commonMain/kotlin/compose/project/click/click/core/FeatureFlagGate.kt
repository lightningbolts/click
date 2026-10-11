package compose.project.click.click.core

object FeatureFlagGate { var isEnabled: (String) -> Boolean = { false } }
