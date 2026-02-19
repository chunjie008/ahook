package com.wzh.ai

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "hook_logs.db"
        private const val DATABASE_VERSION = 3 // 升级版本号

        // 表和列的名称
        const val TABLE_LOGS = "logs"
        const val COL_ID = "_id"
        const val COL_TIMESTAMP = "timestamp"
        const val COL_LOG_NAME = "log_name"
        const val COL_PACKAGE_NAME = "package_name"
        const val COL_APP_NAME = "app_name"
        const val COL_KEY_TYPE = "key_type"
        const val COL_KEY_STRING = "key_string"
        const val COL_KEY_HEX = "key_hex"
        const val COL_KEY_BASE64 = "key_base64"
        const val COL_INPUT_STRING = "input_string"
        const val COL_INPUT_HEX = "input_hex"
        const val COL_INPUT_BASE64 = "input_base64"
        const val COL_OUTPUT_STRING = "output_string"
        const val COL_OUTPUT_HEX = "output_hex"
        const val COL_OUTPUT_BASE64 = "output_base64"
        const val COL_STACK_TRACE = "stack_trace"
        const val COL_IV_STRING = "iv_string"
        const val COL_IV_HEX = "iv_hex"
        const val COL_IV_BASE64 = "iv_base64"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = """
            CREATE TABLE $TABLE_LOGS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TIMESTAMP TEXT NOT NULL,
                $COL_LOG_NAME TEXT NOT NULL,
                $COL_PACKAGE_NAME TEXT,
                $COL_APP_NAME TEXT,
                $COL_KEY_TYPE TEXT,
                $COL_KEY_STRING TEXT,
                $COL_KEY_HEX TEXT,
                $COL_KEY_BASE64 TEXT,
                $COL_INPUT_STRING TEXT,
                $COL_INPUT_HEX TEXT,
                $COL_INPUT_BASE64 TEXT,
                $COL_OUTPUT_STRING TEXT,
                $COL_OUTPUT_HEX TEXT,
                $COL_OUTPUT_BASE64 TEXT,
                $COL_STACK_TRACE TEXT,
                $COL_IV_STRING TEXT,
                $COL_IV_HEX TEXT,
                $COL_IV_BASE64 TEXT
            )
        """
        db.execSQL(createTableQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $TABLE_LOGS ADD COLUMN $COL_STACK_TRACE TEXT;")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE $TABLE_LOGS ADD COLUMN $COL_IV_STRING TEXT;")
            db.execSQL("ALTER TABLE $TABLE_LOGS ADD COLUMN $COL_IV_HEX TEXT;")
            db.execSQL("ALTER TABLE $TABLE_LOGS ADD COLUMN $COL_IV_BASE64 TEXT;")
        }
    }
}
