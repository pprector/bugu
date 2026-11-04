package com.bugu.schedule

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bugu.ApiFactory
import com.bugu.SharedPrefsTokenStore
import com.bugu.client.api.GeitaApiException
import java.util.concurrent.TimeUnit

/**
 * 周期同步兜底:每 15 分钟拉一次服务端做全量重排。
 * 不是实时触发;服务端没有 fireAt 推送,最坏会比新 fire 晚约 15 分钟补上。
 * 无 token 或 401 时直接成功返回,避免旧账号在后台无限重试。
 */
class ReminderSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): androidx.work.ListenableWorker.Result {
        val token = SharedPrefsTokenStore(applicationContext).get()
        if (token.isNullOrBlank()) return androidx.work.ListenableWorker.Result.success()
        val api = ApiFactory.create(applicationContext)
        return try {
            ReminderSync.sync(applicationContext, api)
            androidx.work.ListenableWorker.Result.success()
        } catch (e: GeitaApiException) {
            if (e.code == "UNAUTHORIZED") {
                androidx.work.ListenableWorker.Result.success()
            } else {
                androidx.work.ListenableWorker.Result.retry()
            }
        } catch (_: Exception) {
            androidx.work.ListenableWorker.Result.retry()
        }
    }

    companion object {
        const val TAG = "bugu-reminder-sync"
        private const val WORK_NAME = TAG

        /** 周期最短 15 分钟;UPDATE 保证只有一条同名任务,应用重启/重复调用不会叠加。 */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReminderSyncWorker>(15, TimeUnit.MINUTES)
                .addTag(TAG)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
