package com.bugu.schedule

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bugu.ApiFactory
import com.bugu.client.api.GeitaApiException
import java.time.Duration

/**
 * 上报重试 Worker(E8-S4 / FR-07 断网补报):恢复联网后按退避重试队列,
 * 成功移除、409 标记已错过移除、其余按 attempts 退避重排。
 */
class ReportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): androidx.work.ListenableWorker.Result {
        val stores = PrefsStores(applicationContext)
        val queue = ReportQueue(stores.reportQueue)
        val api = ApiFactory.create(applicationContext)

        var retryNeeded = false
        for (item in queue.all()) {
            try {
                api.reportFire(item.fireId, item.idempotencyKey, item.source)
                queue.markDone(item.fireId)
            } catch (e: GeitaApiException) {
                when (e.code) {
                    "CONFLICT" -> queue.markMissed(item.fireId) // 已收口/已错过,停止重试
                    "UNAUTHORIZED" -> {
                        // 旧账号 token 已失效:清空队列,不在后台永久重试。
                        queue.clear()
                        return androidx.work.ListenableWorker.Result.success()
                    }
                    else -> {
                        queue.markRetry(item.fireId)
                        retryNeeded = true
                    }
                }
            } catch (e: Exception) {
                queue.markRetry(item.fireId)
                retryNeeded = true
            }
        }
        return if (retryNeeded) {
            androidx.work.ListenableWorker.Result.retry()
        } else {
            androidx.work.ListenableWorker.Result.success()
        }
    }

    companion object {
        const val TAG = "bugu-fire-report"
        private const val WORK_NAME = TAG

        /** 冷启动全量补报:立即跑一遍队列(幂等,空队列零开销)。 */
        fun flush(context: Context) {
            val request = OneTimeWorkRequestBuilder<ReportWorker>()
                .addTag(TAG)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        /** 队列非空时调度一次退避重试(30s 起,线性退避)。 */
        fun schedule(context: Context, fireId: Long) {
            val request = OneTimeWorkRequestBuilder<ReportWorker>()
                .addTag(TAG)
                .setInitialDelay(Duration.ofSeconds(30))
                .setBackoffCriteria(BackoffPolicy.LINEAR, Duration.ofSeconds(30))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "${WORK_NAME}:$fireId",
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        /** 登出/401:停止全部补报任务。 */
        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
        }
    }
}
