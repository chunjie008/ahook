package com.wzh.ai

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri

class LogContentProvider : ContentProvider() {

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var preferencesUtil: PreferencesUtil

    companion object {
        const val AUTHORITY = "com.wzh.ai.provider"
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/logs")

        private const val LOGS = 1
        private const val LOG_ID = 2

        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "logs", LOGS)
            addURI(AUTHORITY, "logs/#", LOG_ID)
        }
    }

    override fun onCreate(): Boolean {
        dbHelper = DatabaseHelper(context!!)
        preferencesUtil = PreferencesUtil(context!!)
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val db = dbHelper.readableDatabase
        val cursor = when (uriMatcher.match(uri)) {
            LOGS -> db.query(DatabaseHelper.TABLE_LOGS, projection, selection, selectionArgs, null, null, sortOrder)
            LOG_ID -> {
                val id = uri.lastPathSegment!!
                db.query(DatabaseHelper.TABLE_LOGS, projection, "${DatabaseHelper.COL_ID} = ?", arrayOf(id), null, null, sortOrder)
            }
            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }
        cursor.setNotificationUri(context!!.contentResolver, uri)
        return cursor
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val db = dbHelper.writableDatabase
        val id = when (uriMatcher.match(uri)) {
            LOGS -> db.insert(DatabaseHelper.TABLE_LOGS, null, values)
            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }
        if (id > 0) {
            context!!.contentResolver.notifyChange(uri, null)

            // 插入成功后，推送日志到 MQTT
            if (preferencesUtil.isMqttEnabled()) {
                val logItem = valuesToLogItem(values)
                if (logItem != null) {
                    MQTTManager.getInstance().sendLog(logItem)
                }
            }

            return ContentUris.withAppendedId(CONTENT_URI, id)
        }
        return null
    }

    private fun valuesToLogItem(values: ContentValues?): LogItem? {
        if (values == null) return null

        return LogItem(
            timestamp = values.getAsString(DatabaseHelper.COL_TIMESTAMP) ?: "",
            logName = values.getAsString(DatabaseHelper.COL_LOG_NAME) ?: "",
            packageName = values.getAsString(DatabaseHelper.COL_PACKAGE_NAME),
            appName = values.getAsString(DatabaseHelper.COL_APP_NAME),
            keyType = values.getAsString(DatabaseHelper.COL_KEY_TYPE),
            keyString = values.getAsString(DatabaseHelper.COL_KEY_STRING),
            keyHex = values.getAsString(DatabaseHelper.COL_KEY_HEX),
            keyBase64 = values.getAsString(DatabaseHelper.COL_KEY_BASE64),
            ivString = values.getAsString(DatabaseHelper.COL_IV_STRING),
            ivHex = values.getAsString(DatabaseHelper.COL_IV_HEX),
            ivBase64 = values.getAsString(DatabaseHelper.COL_IV_BASE64),
            inputString = values.getAsString(DatabaseHelper.COL_INPUT_STRING),
            inputHex = values.getAsString(DatabaseHelper.COL_INPUT_HEX),
            inputBase64 = values.getAsString(DatabaseHelper.COL_INPUT_BASE64),
            outputString = values.getAsString(DatabaseHelper.COL_OUTPUT_STRING),
            outputHex = values.getAsString(DatabaseHelper.COL_OUTPUT_HEX),
            outputBase64 = values.getAsString(DatabaseHelper.COL_OUTPUT_BASE64),
            stackTrace = values.getAsString(DatabaseHelper.COL_STACK_TRACE)
        )
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        val db = dbHelper.writableDatabase
        val rowsDeleted: Int
        when (uriMatcher.match(uri)) {
            LOGS -> {
                rowsDeleted = db.delete(DatabaseHelper.TABLE_LOGS, selection, selectionArgs)
            }
            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }
        if (rowsDeleted > 0) {
            context!!.contentResolver.notifyChange(uri, null)
        }
        return rowsDeleted
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        // Not implemented for this app
        return 0
    }

    override fun getType(uri: Uri): String? {
        return null
    }
}