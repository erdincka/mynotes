package com.mynotes.sync

import android.content.Context
import com.microsoft.identity.client.AcquireTokenParameters
import com.microsoft.identity.client.AcquireTokenSilentParameters
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.IPublicClientApplication
import com.microsoft.identity.client.ISingleAccountPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.SilentAuthenticationCallback
import com.microsoft.identity.client.exception.MsalException
import com.mynotes.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Wraps MSAL single-account sign-in for OneDrive access.
 *
 * Setup required before use:
 *  1. Register your app at https://portal.azure.com and note the Application (client) ID.
 *  2. Add a Mobile/Desktop redirect URI:
 *       msauth://com.mynotes/<base64-encoded-SHA1-of-signing-cert>
 *     Get the hash with: keytool -exportcert -alias androiddebugkey \
 *       -keystore ~/.android/debug.keystore | openssl sha1 -binary | openssl base64
 *  3. Update res/raw/auth_config_single_account.json with the client_id and redirect_uri.
 *  4. Update the intent-filter in AndroidManifest.xml with the same path (cert hash).
 */
@Singleton
class OneDriveAuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var msalApp: ISingleAccountPublicClientApplication? = null

    private suspend fun app(): ISingleAccountPublicClientApplication {
        return msalApp ?: withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                PublicClientApplication.createSingleAccountPublicClientApplication(
                    context,
                    R.raw.auth_config_single_account,
                    object : IPublicClientApplication.ISingleAccountApplicationCreatedListener {
                        override fun onCreated(app: ISingleAccountPublicClientApplication) {
                            cont.resume(app)
                        }
                        override fun onError(e: MsalException) {
                            cont.resumeWithException(e)
                        }
                    }
                )
            }
        }.also { msalApp = it }
    }

    /**
     * Launches an interactive sign-in via Chrome Custom Tab.
     * Must be called from the UI thread (passes the Activity for the tab launch).
     */
    suspend fun signIn(activity: androidx.activity.ComponentActivity): Result<String> = try {
        val msalInstance = app()
        val result = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<IAuthenticationResult> { cont ->
                msalInstance.acquireToken(
                    AcquireTokenParameters.Builder()
                        .startAuthorizationFromActivity(activity)
                        .withScopes(SCOPES)
                        .withCallback(object : AuthenticationCallback {
                            override fun onSuccess(r: IAuthenticationResult) = cont.resume(r)
                            override fun onError(e: MsalException) = cont.resumeWithException(e)
                            override fun onCancel() =
                                cont.resumeWithException(CancellationException("Sign-in cancelled"))
                        })
                        .build()
                )
            }
        }
        Timber.i("OneDriveAuthManager: signed in as ${result.account.username}")
        Result.success(result.accessToken)
    } catch (e: CancellationException) {
        Result.failure(e)
    } catch (e: Exception) {
        Timber.e(e, "OneDriveAuthManager: interactive sign-in failed")
        Result.failure(e)
    }

    /**
     * Silently refreshes the access token using MSAL's cache.
     * Returns failure if no account is signed in or the cache is expired.
     */
    suspend fun silentSignIn(): Result<String> = try {
        val msalInstance = app()
        val account = withContext(Dispatchers.Main) {
            msalInstance.currentAccount.currentAccount
        } ?: return Result.failure(IllegalStateException("No account signed in"))

        val result = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<IAuthenticationResult> { cont ->
                msalInstance.acquireTokenSilentAsync(
                    AcquireTokenSilentParameters.Builder()
                        .forAccount(account)
                        .fromAuthority(account.authority)
                        .withScopes(SCOPES)
                        .withCallback(object : SilentAuthenticationCallback {
                            override fun onSuccess(r: IAuthenticationResult) = cont.resume(r)
                            override fun onError(e: MsalException) = cont.resumeWithException(e)
                        })
                        .build()
                )
            }
        }
        Result.success(result.accessToken)
    } catch (e: Exception) {
        Timber.e(e, "OneDriveAuthManager: silent sign-in failed")
        Result.failure(e)
    }

    /** Returns true if MSAL has a signed-in account in its cache. */
    suspend fun isSignedIn(): Boolean = try {
        withContext(Dispatchers.Main) {
            app().currentAccount.currentAccount != null
        }
    } catch (e: Exception) {
        false
    }

    suspend fun signOut(): Result<Unit> = try {
        val msalInstance = app()
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                msalInstance.signOut(object : ISingleAccountPublicClientApplication.SignOutCallback {
                    override fun onSignOut() = cont.resume(Unit)
                    override fun onError(e: MsalException) = cont.resumeWithException(e)
                })
            }
        }
        Timber.i("OneDriveAuthManager: signed out")
        Result.success(Unit)
    } catch (e: Exception) {
        Timber.e(e, "OneDriveAuthManager: sign-out failed")
        Result.failure(e)
    }

    companion object {
        private val SCOPES = listOf("Files.ReadWrite", "offline_access")
    }
}
