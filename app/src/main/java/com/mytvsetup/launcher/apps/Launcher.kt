package com.mytvsetup.launcher.apps

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

sealed interface LaunchResult {
    data object Ok : LaunchResult
    data class NotInstalled(val label: String) : LaunchResult
}

object Launcher {
    fun openApp(ctx: Context, pkg: String, label: String): LaunchResult = try {
        val pm = ctx.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg)
            ?: pm.getLaunchIntentForPackage(pkg)
        if (intent == null) {
            LaunchResult.NotInstalled(label)
        } else {
            ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            LaunchResult.Ok
        }
    } catch (_: ActivityNotFoundException) {
        LaunchResult.NotInstalled(label)
    } catch (_: SecurityException) {
        LaunchResult.NotInstalled(label)
    }

    /** [fresh] restarts the app's task: a running streaming app otherwise just resumes and drops the link. */
    fun openUrlIn(ctx: Context, pkg: String, url: String, label: String, fresh: Boolean = false): LaunchResult = try {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .setPackage(pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or if (fresh) Intent.FLAG_ACTIVITY_CLEAR_TASK else 0)
        )
        LaunchResult.Ok
    } catch (_: ActivityNotFoundException) {
        openApp(ctx, pkg, label)
    } catch (_: SecurityException) {
        openApp(ctx, pkg, label)
    }
}
