package com.example.runtime

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Bridge that runs in whichever profile Clonix was started in.
 *
 * The personal-side Clonix cannot start another app inside the work profile directly, but
 * it can ask Android to start *its own* activity there. Once this activity runs inside the
 * work profile, a normal local `getLaunchIntentForPackage` resolves to the work copy of the
 * app, so the clone opens natively without any cross-profile permission for other packages.
 *
 * It stays invisible and finishes immediately.
 */
class CloneLauncherActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = intent.getStringExtra(EXTRA_TARGET_PACKAGE).orEmpty()
        if (target.isNotBlank() && target != packageName) {
            val launchIntent = runCatching { packageManager.getLaunchIntentForPackage(target) }.getOrNull()
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { startActivity(launchIntent) }
            }
        }
        finish()
    }

    companion object {
        const val EXTRA_TARGET_PACKAGE = "extra_target_package"
    }
}
