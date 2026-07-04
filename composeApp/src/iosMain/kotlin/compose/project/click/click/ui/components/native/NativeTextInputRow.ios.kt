@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package compose.project.click.click.ui.components.native

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UITextView
import platform.UIKit.UITextViewDelegateProtocol
import platform.darwin.NSObject

@Composable
actual fun NativeTextInputRow(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    placeholder: String,
    enabled: Boolean,
    singleLine: Boolean,
    maxLines: Int,
    keyboardOptions: KeyboardOptions,
    focusRequester: FocusRequester?,
) {
    val onValueChangeState by rememberUpdatedState(onValueChange)
    val enabledState by rememberUpdatedState(enabled)
    val singleLineState by rememberUpdatedState(singleLine)
    val cornerRadius = 20.0

    val textDelegate = remember {
        object : NSObject(), UITextViewDelegateProtocol {
            override fun textViewDidChange(textView: UITextView) {
                runOnMainQueue {
                    val text = textView.text ?: return@runOnMainQueue
                    onValueChangeState(text)
                }
            }
        }
    }

    val focusModifier = if (focusRequester != null) {
        Modifier.focusRequester(focusRequester)
    } else {
        Modifier
    }

    UIKitView(
        modifier = modifier
            .then(focusModifier)
            .fillMaxWidth()
            .heightIn(min = 44.dp),
        properties = nativeChromeUIKitInteropProperties(isInteractive = enabled),
        factory = {
            runOnMainQueueSync {
                val chrome = createLiquidGlassChromeView(cornerRadius, interactive = true)
                val textView = UITextView().apply {
                    applyTransparentChromeHost(this)
                    translatesAutoresizingMaskIntoConstraints = false
                    font = UIFont.systemFontOfSize(16.0)
                    textColor = UIColor.blackColor
                    setScrollEnabled(!singleLineState)
                    setDelegate(textDelegate)
                }
                chrome.contentView.addSubview(textView)
                NSLayoutConstraint.activateConstraints(
                    listOf(
                        textView.leadingAnchor.constraintEqualToAnchor(chrome.contentView.leadingAnchor, constant = 10.0),
                        textView.trailingAnchor.constraintEqualToAnchor(chrome.contentView.trailingAnchor, constant = -10.0),
                        textView.topAnchor.constraintEqualToAnchor(chrome.contentView.topAnchor, constant = 6.0),
                        textView.bottomAnchor.constraintEqualToAnchor(chrome.contentView.bottomAnchor, constant = -6.0),
                    ),
                )
                chrome
            }
        },
        update = { chrome ->
            runOnMainQueue {
                val textView = chrome.contentView.subviews
                    .filterIsInstance<UITextView>()
                    .firstOrNull() ?: return@runOnMainQueue
                if (textView.text != value) {
                    textView.text = value
                }
                textView.setEditable(enabledState)
                textView.setUserInteractionEnabled(enabledState)
                textView.alpha = if (enabledState) 1.0 else 0.55
                textView.setScrollEnabled(!singleLineState)
                applyTransparentChromeHost(textView)
                chrome.applyChromeCornerRadius(cornerRadius)
            }
        },
    )
}
