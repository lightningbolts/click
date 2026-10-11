@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import compose.project.click.click.openInAppBrowser // pragma: allowlist secret

/** Click's legal pages (iOS `LegalLinks`). */
object LegalLinks {
    const val TERMS_URL = "https://joinclick.co/terms"
    const val PRIVACY_URL = "https://joinclick.co/privacy"
}

/**
 * "By continuing, you agree to Click's Terms and Privacy Policy…" under the sign-in and sign-up
 * actions (iOS `AuthView`, App Store 1.2 / Play UGC policy).
 */
@Composable
fun LegalAgreementText(modifier: Modifier = Modifier) {
    val linkStyle =
        TextLinkStyles(
            style = SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold),
        )
    val text =
        remember(linkStyle) {
            buildAnnotatedString {
                append("By continuing, you agree to Click's ")
                withLink(LinkAnnotation.Clickable("terms", linkStyle) { openInAppBrowser(LegalLinks.TERMS_URL) }) {
                    append("Terms")
                }
                append(" and ")
                withLink(LinkAnnotation.Clickable("privacy", linkStyle) { openInAppBrowser(LegalLinks.PRIVACY_URL) }) {
                    append("Privacy Policy")
                }
                append(". Click has zero tolerance for objectionable content or abusive users.")
            }
        }
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}
