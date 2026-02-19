package com.wzh.ai

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri

class LogContentProvider : ContentProvider() {

    private lateinit var dbHelper: DatabaseHelper

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
            return ContentUris.withAppendedId(CONTENT_URI, id)
        }
        return null
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