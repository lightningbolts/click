@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package compose.project.click.click.data.repository

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AuthenticationServices.ASAuthorization
import platform.AuthenticationServices.ASAuthorizationAppleIDCredential
import platform.AuthenticationServices.ASAuthorizationAppleIDProvider
import platform.AuthenticationServices.ASAuthorizationController
import platform.AuthenticationServices.ASAuthorizationControllerDelegateProtocol
import platform.AuthenticationServices.ASAuthorizationControllerPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASAuthorizationScopeEmail
import platform.AuthenticationServices.ASAuthorizationScopeFullName
import platform.Foundation.NSError
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import kotlin.coroutines.resume

private class NativeAppleSignInDelegate(
    private val onResult: (Result<NativeAppleSignInPayload>) -> Unit,
) : NSObject(),
    ASAuthorizationControllerDelegateProtocol,
    ASAuthorizationControllerPresentationContextProvidingProtocol {

    override fun authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithAuthorization: ASAuthorization,
    ) {
        val credential = didCompleteWithAuthorization.credential as? ASAuthorizationAppleIDCredential
        if (credential == null) {
            onResult(Result.failure(IllegalStateException("Apple credential was unavailable.")))
            return
        }

        val token = credential.identityToken
            ?.let { NSString.create(data = it, encoding = NSUTF8StringEncoding)?.toString() }
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        if (token == null) {
            onResult(Result.failure(IllegalStateException("Apple ID token was unavailable.")))
            return
        }

        onResult(Result.success(NativeAppleSignInPayload(idToken = token, nonce = null)))
    }

    override fun authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithError: NSError,
    ) {
        val localized = didCompleteWithError.localizedDescription?.takeIf { it.isNotBlank() }
        val domain = didCompleteWithError.domain
        val code = didCompleteWithError.code
        val message = localized
            ?.let { "$it ($domain/$code)" }
            ?: "Apple sign-in failed ($domain/$code)."
        onResult(Result.failure(Exception(message)))
    }

    override fun presentationAnchorForAuthorizationController(controller: ASAuthorizationController): ASPresentationAnchor {
        return UIApplication.sharedApplication.keyWindow ?: UIWindow()
    }
}

actual suspend fun requestNativeAppleSignInPayload(): Result<NativeAppleSignInPayload?> =
    suspendCancellableCoroutine { cont ->
        val request = ASAuthorizationAppleIDProvider().createRequest().apply {
            requestedScopes = listOf(ASAuthorizationScopeFullName, ASAuthorizationScopeEmail)
        }

        val controller = ASAuthorizationController(listOf(request))
        var settled = false
        val delegate = NativeAppleSignInDelegate { result ->
            if (settled) return@NativeAppleSignInDelegate
            settled = true
            controller.delegate = null
            controller.presentationContextProvider = null
            cont.resume(result)
        }

        controller.delegate = delegate
        controller.presentationContextProvider = delegate
        controller.performRequests()

        cont.invokeOnCancellation {
            settled = true
            controller.delegate = null
            controller.presentationContextProvider = null
        }
    }
