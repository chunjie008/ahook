package com.wzh.ai

import android.app.Service
import android.content.Intent
import android.database.Cursor
import android.os.IBinder
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject

class HttpServerService : Service() {

    private var httpServer: WebServer? = null
    private lateinit var dbHelper: DatabaseHelper

    companion object {
        const val TAG = "HttpServerService"
        const val PORT = 8888
    }

    override fun onCreate() {
        super.onCreate()
        dbHelper = DatabaseHelper(this)
        httpServer = WebServer(PORT, dbHelper)
        try {
            httpServer?.start()
            Log.d(TAG, "HTTP Server started on port $PORT")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting HTTP server: ${e.message}", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        httpServer?.stop()
        Log.d(TAG, "HTTP Server stopped.")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    inner class WebServer(port: Int, private val dbHelper: DatabaseHelper) : NanoHTTPD(port) {
        override fun serve(session: IHTTPSession): Response {
            val uri = session.uri
            Log.d(TAG, "Request URI: $uri")

            return when (uri) {
                "/query" -> handleQuery(session)
                "/logs" -> handleGetLogs(session)
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
            }
        }

        private fun handleQuery(session: IHTTPSession): Response {
            val params = session.parameters
            val query = params["query"]?.get(0)

            if (query == null) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Missing 'query' parameter")
            }

            Log.d(TAG, "Executing query: $query")

            return try {
                val db = dbHelper.readableDatabase
                val cursor: Cursor = db.rawQuery(query, null)
                val jsonArray = JSONArray()

                cursor.use {
                    while (it.moveToNext()) {
                        val jsonObject = JSONObject()
                        for (i in 0 until it.columnCount) {
                            val columnName = it.getColumnName(i)
                            val value = it.getString(i)
                            jsonObject.put(columnName, value)
                        }
                        jsonArray.put(jsonObject)
                    }
                }
                newFixedLengthResponse(Response.Status.OK, "application/json", jsonArray.toString())
            } catch (e: Exception) {
                Log.e(TAG, "Error executing query: ${e.message}", e)
                newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error executing query: ${e.message}")
            }
        }

        private fun handleGetLogs(session: IHTTPSession): Response {
            val db = dbHelper.readableDatabase
            val cursor: Cursor = db.query(DatabaseHelper.TABLE_LOGS, null, null, null, null, null, "${DatabaseHelper.COL_TIMESTAMP} DESC")
            val jsonArray = JSONArray()

            cursor.use {
                while (it.moveToNext()) {
                    val jsonObject = JSONObject()
                    for (i in 0 until it.columnCount) {
                        val columnName = it.getColumnName(i)
                        val value = it.getString(i)
                        jsonObject.put(columnName, value)
                    }
                    jsonArray.put(jsonObject)
                }
            }
            return newFixedLengthResponse(Response.Status.OK, "application/json", jsonArray.toString())
        }
    }
}
