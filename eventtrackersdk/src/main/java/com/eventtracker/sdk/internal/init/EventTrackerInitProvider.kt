package com.eventtracker.sdk.internal.init

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.util.Log
import com.eventtracker.sdk.EventTrackerSDK
import com.eventtracker.sdk.internal.config.SdkInitConfigReader
import com.eventtracker.sdk.internal.config.SdkInitValues
import java.io.FileNotFoundException

/**
 * Auto-initializes [EventTrackerSDK] on process start, so the host app needs zero code to get
 * tracking running — the same "framework-agnostic, drop-in" contract [EventTrackerSDK]'s own doc
 * comment already promises. The platform instantiates every manifest-declared `ContentProvider`
 * (this one is declared in this library's own manifest, so it merges into any host app
 * automatically) before any application code runs — before even
 * `Application.attachBaseContext`/`onCreate` — which is what guarantees [EventTrackerSDK.init]
 * has always already completed by the time host app code executes. A host app no longer needs its
 * own explicit `init()` call.
 *
 * Initial `retentionDays`/`maxEventCount` come from an optional JSON asset the host app may ship
 * at `assets/`[SdkInitConfigReader.ASSET_FILE_NAME] — the same "drop a config file in, no code
 * required" shape as Firebase's `google-services.json`. If that file doesn't exist (or fails to
 * parse), the SDK's own built-in defaults are used instead — a missing or broken config file must
 * never crash app startup.
 *
 * A host app that needs to change these values at runtime (e.g. a user-facing settings screen
 * backed by its own persistence) calls [EventTrackerSDK.updateConfig] — this provider only ever
 * supplies the *initial* values for the very first process start.
 */
internal class EventTrackerInitProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        val context = context ?: return false
        val values = readInitValues(context)
        EventTrackerSDK.init(context, values.retentionDays, values.maxEventCount)
        return true
    }

    private fun readInitValues(context: Context): SdkInitValues {
        val text = try {
            context.assets.open(SdkInitConfigReader.ASSET_FILE_NAME).use { it.bufferedReader().readText() }
        } catch (e: FileNotFoundException) {
            // No config file shipped — perfectly normal, the SDK works with defaults out of the box.
            return SdkInitValues.DEFAULTS
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read ${SdkInitConfigReader.ASSET_FILE_NAME}, falling back to defaults", e)
            return SdkInitValues.DEFAULTS
        }
        return SdkInitConfigReader.parse(text)
    }

    // No actual content to serve — this provider exists solely for its onCreate() lifecycle hook.
    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0

    companion object {
        private const val TAG = "EventTrackerInitProvider"
    }
}
