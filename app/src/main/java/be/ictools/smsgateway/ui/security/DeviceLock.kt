package be.ictools.smsgateway.ui.security

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal

enum class UnlockRequirement {
    /** Nothing saved yet: the first configuration needs no unlock. */
    NONE,

    /** Saved credentials: biometrics or the device PIN/pattern/password first. */
    DEVICE_CREDENTIAL,

    /** Saved credentials but the phone has no screen lock: nothing to check against. */
    NO_DEVICE_LOCK,
}

object ConfigEditGate {
    fun requirement(hasStoredCredentials: Boolean, deviceSecure: Boolean): UnlockRequirement = when {
        !hasStoredCredentials -> UnlockRequirement.NONE
        deviceSecure -> UnlockRequirement.DEVICE_CREDENTIAL
        else -> UnlockRequirement.NO_DEVICE_LOCK
    }
}

/** Asks for the device lock (biometrics or credentials) with the platform BiometricPrompt. */
object DeviceLock {
    fun isDeviceSecure(context: Context): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    fun authenticate(
        activity: Activity,
        title: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val builder = BiometricPrompt.Builder(activity).setTitle(title)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setDeviceCredentialAllowed(true)
        }
        builder.build().authenticate(
            CancellationSignal(),
            activity.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    onError(errString?.toString() ?: "Déverrouillage annulé")
                }
            },
        )
    }
}
