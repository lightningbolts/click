package compose.project.click.click.util

import kotlinx.coroutines.CoroutineDispatcher

/** Dispatcher used for chat media fetch/decrypt work across KMP targets. */
expect val chatMediaDispatcher: CoroutineDispatcher
