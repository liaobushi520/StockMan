package com.liaobusi.stockman

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.system.exitProcess

object AppDataBackup {

    const val BACKUP_ZIP_NAME = "stockman_backup.zip"

    private val DOWNLOAD_WEIXIN_RELATIVE = "${Environment.DIRECTORY_DOWNLOADS}/WeiXin"

    private fun stockManDbFile(ctx: Context) = ctx.getDatabasePath("stock_man")

    private fun sharedPrefsAppXml(ctx: Context) =
        File(File(ctx.applicationInfo.dataDir, "shared_prefs"), "app.xml")

    fun checkpointStockDbIfExists(ctx: Context) {
        val f = stockManDbFile(ctx)
        if (!f.exists()) return
        try {
            SQLiteDatabase.openDatabase(
                f.absolutePath,
                null,
                SQLiteDatabase.OPEN_READWRITE
            ).use { db ->
                db.rawQuery("PRAGMA wal_checkpoint(FULL)", null)?.close()
            }
        } catch (_: Throwable) {
        }
    }

    private fun addZipEntry(zos: ZipOutputStream, entryName: String, file: File) {
        if (!file.exists()) return
        zos.putNextEntry(ZipEntry(entryName))
        FileInputStream(file).use { it.copyTo(zos) }
        zos.closeEntry()
    }

    private fun deleteOldMediaStoreBackups(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        ctx.contentResolver.delete(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
            arrayOf(BACKUP_ZIP_NAME, "%WeiXin%")
        )
    }

    private fun insertPendingBackupUri(ctx: Context): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        deleteOldMediaStoreBackups(ctx)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, BACKUP_ZIP_NAME)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
            put(MediaStore.MediaColumns.RELATIVE_PATH, DOWNLOAD_WEIXIN_RELATIVE)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        return ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
    }

    private fun finalizeMediaStoreItem(ctx: Context, uri: Uri) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }
        ctx.contentResolver.update(uri, values, null, null)
    }

    suspend fun backup(ctx: Context): String = withContext(Dispatchers.IO) {
        checkpointStockDbIfExists(ctx)
        val db = stockManDbFile(ctx)
        val sp = sharedPrefsAppXml(ctx)
        if (!db.exists() && !sp.exists()) {
            return@withContext "没有可备份的数据库或设置文件"
        }
        val tmp = File(ctx.cacheDir, "stockman_backup_work.zip")
        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(tmp))).use { zos ->
                if (db.exists()) {
                    addZipEntry(zos, "db/stock_man", db)
                    val parent = db.parentFile
                    if (parent != null) {
                        addZipEntry(zos, "db/stock_man-wal", File(parent, "${db.name}-wal"))
                        addZipEntry(zos, "db/stock_man-shm", File(parent, "${db.name}-shm"))
                    }
                }
                if (sp.exists()) {
                    addZipEntry(zos, "shared_prefs/app.xml", sp)
                }
            }
            if (!tmp.exists() || tmp.length() == 0L) {
                tmp.delete()
                return@withContext "备份打包失败"
            }
            val uri = insertPendingBackupUri(ctx)
                ?: return@withContext "无法创建下载目录中的备份文件（MediaStore）"
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(tmp).use { it.copyTo(out) }
                } ?: return@withContext "无法写入备份文件"
            } finally {
                finalizeMediaStoreItem(ctx, uri)
            }
            tmp.delete()
            "已备份到 下载/WeiXin/$BACKUP_ZIP_NAME"
        } catch (e: Exception) {
            tmp.delete()
            "备份失败：${e.message ?: e.javaClass.simpleName}"
        }
    }

    private fun doRestoreZipStream(ctx: Context, rawInput: InputStream): String? {
        val appCtx = ctx.applicationContext
        var closedDb = false
        var sawDb = false
        try {
            BufferedInputStream(rawInput).use { bis ->
                ZipInputStream(bis).use { zis ->
                    Injector.closeAppDatabaseForRestore()
                    closedDb = true
                    val dbFile = stockManDbFile(ctx)
                    val dbDir = dbFile.parentFile
                    val spOut = sharedPrefsAppXml(ctx)
                    while (true) {
                        val entry = zis.nextEntry ?: break
                        try {
                            when (entry.name) {
                                "db/stock_man" -> {
                                    dbDir?.mkdirs()
                                    File(dbFile.path + "-wal").delete()
                                    File(dbFile.path + "-shm").delete()
                                    FileOutputStream(dbFile).use { out -> zis.copyTo(out) }
                                    sawDb = true
                                }

                                "db/stock_man-wal" -> {
                                    if (dbDir != null) {
                                        FileOutputStream(File(dbDir, "${dbFile.name}-wal")).use { out ->
                                            zis.copyTo(out)
                                        }
                                    }
                                }

                                "db/stock_man-shm" -> {
                                    if (dbDir != null) {
                                        FileOutputStream(File(dbDir, "${dbFile.name}-shm")).use { out ->
                                            zis.copyTo(out)
                                        }
                                    }
                                }

                                "shared_prefs/app.xml" -> {
                                    spOut.parentFile?.mkdirs()
                                    FileOutputStream(spOut).use { out -> zis.copyTo(out) }
                                }
                            }
                        } finally {
                            zis.closeEntry()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (closedDb) Injector.reopenAppDatabaseIfNeeded(appCtx)
            return "还原失败：${e.message ?: e.javaClass.simpleName}"
        }
        if (!sawDb) {
            if (closedDb) Injector.reopenAppDatabaseIfNeeded(appCtx)
            return "备份包无效：缺少数据库文件"
        }
        return null
    }

    suspend fun restoreFromUri(ctx: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        val input = ctx.contentResolver.openInputStream(uri)
            ?: return@withContext "无法打开所选文件"
        input.use { stream ->
            doRestoreZipStream(ctx, stream)
        }
    }

    fun restartAppProcess(activity: Activity) {
        val intent = activity.packageManager.getLaunchIntentForPackage(activity.packageName)
            ?: Intent(activity, HomeActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        activity.startActivity(intent)
        activity.finishAffinity()
        exitProcess(0)
    }
}
