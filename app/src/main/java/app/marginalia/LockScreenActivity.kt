package app.marginalia

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import app.marginalia.ui.LocalApp
import app.marginalia.ui.LockScreen
import app.marginalia.ui.MarginaliaTheme

/**
 * The lock screen player. It shows over the keyguard while audio plays, like a music app's own lock screen.
 * Look and feel come from Settings > Lock screen.
 */
class LockScreenActivity : ComponentActivity() {
    private val unlocked = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        enableEdgeToEdge()
        val app = application as MarginaliaApp
        app.player.connectService(this)
        ContextCompat.registerReceiver(this, unlocked, IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_NOT_EXPORTED)
        setContent {
            val settings by app.settings.state.collectAsState()
            MarginaliaTheme(settings) {
                CompositionLocalProvider(LocalApp provides app) {
                    LockScreen(app, onUnlock = ::unlock, onClose = { finish() })
                }
            }
        }
    }

    /** Asks Android to show the PIN or fingerprint prompt, then leaves. */
    private fun unlock() {
        val km = getSystemService(KeyguardManager::class.java)
        if (!km.isKeyguardLocked) { finish(); return }
        km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() = finish()
        })
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(unlocked) }
        super.onDestroy()
    }

    companion object {
        /** Shows the lock screen. Android may refuse from the background unless "display over other apps" is allowed. */
        fun launch(context: Context) {
            val i = Intent(context, LockScreenActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            runCatching { context.startActivity(i) }
        }
    }
}
