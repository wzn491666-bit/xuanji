package com.xuanji.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import java.io.Reader

/**
 * 前台服务：在 proot Ubuntu 里跑 `tianshu serve`，锁屏/切后台也不会被杀。
 * 天枢崩溃时 5 秒后自动重启。
 */
class TianshuService : Service() {

    enum class State { STOPPED, INSTALLING, STARTING, RUNNING, CRASHED }

    companion object {
        private const val TAG = "TianshuService"
        const val PORT = 3100

        // 全局状态，UI 直接订阅（服务和界面在同一进程）
        val state = MutableStateFlow(State.STOPPED)
        val installProgress = MutableStateFlow(0)
        val log = MutableStateFlow(listOf<String>())
        val androidMcpStatus = MutableStateFlow(AndroidMcpStatus())
        val androidActionApproval = AndroidActionApprovalGate()
        private val sensitiveValues = ConcurrentHashMap.newKeySet<String>()

        fun addSensitiveValue(value: String) {
            if (value.length >= 8) sensitiveValues.add(value)
        }

        /** 本机访问天枢 HTTP 接口用的令牌，每次启动随机生成。 */
        @Volatile var token: String = ""
            private set

        @Volatile var androidMcpToken: String = ""
            private set

        fun start(context: Context) =
            context.startForegroundService(Intent(context, TianshuService::class.java))

        fun stop(context: Context) =
            context.stopService(Intent(context, TianshuService::class.java))
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var runJob: Job? = null
    private val process = AtomicReference<Process?>()
    private var androidMcp: AndroidMcpServer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        addSensitiveValue(SecureSettings.loadApiKey(this))
        androidActionApproval.start()
        val statusText = androidActionApproval.pending.value?.let { "等待操作审批：${it.title}" } ?: when (state.value) {
            State.RUNNING -> "天枢运行中"
            State.INSTALLING -> "运行环境安装中…"
            State.CRASHED -> "天枢异常退出，正在重启…"
            else -> "天枢启动中…"
        }
        startForeground(1, notification(statusText))
        ensureAndroidMcp()
        if (runJob?.isActive != true) runJob = scope.launch { runLoop() }
        return START_STICKY
    }

    private suspend fun runLoop() {
        val runtime = Runtime(this)
        if (!runtime.isInstalled()) {
            state.value = State.INSTALLING
            runCatching { runtime.install { installProgress.value = it } }.onFailure {
                if (it is CancellationException) throw it
                Log.e(TAG, "运行环境解压失败", it)
                appendLog("运行环境解压失败：${it.message}")
                state.value = State.CRASHED
                updateNotification("运行环境安装失败")
                return
            }
        }
        while (currentCoroutineContext().isActive) {
            token = UUID.randomUUID().toString()
            addSensitiveValue(token)
            state.value = State.STARTING
            updateNotification("天枢启动中…")
            var ownedProcess: Process? = null
            try {
                currentCoroutineContext().ensureActive()
                runtime.prepareAndroidEnvironment()
                val p = runtime.command(
                    // 没授予文件权限时 /sdcard 进不去，退回到 /root
                    "cd /sdcard 2>/dev/null || cd /root; exec tianshu serve --port $PORT",
                    mapOf("RIVET_SERVER_TOKEN" to token, "NODE_OPTIONS" to "--import=/opt/xuanji/register.mjs"),
                ).start()
                ownedProcess = p
                process.set(p)
                val runContext = currentCoroutineContext()
                runContext.ensureActive()
                p.inputStream.bufferedReader().use { reader ->
                    readBoundedLogLines(reader, checkActive = { runContext.ensureActive() }) { line ->
                        appendLog(line)
                        if ("Runtime API listening" in line) {
                            state.value = State.RUNNING
                            updateNotification("天枢运行中")
                        }
                    }
                }
                appendLog("天枢进程退出，退出码 ${p.waitFor()}")
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                Log.e(TAG, "天枢启动或运行失败", t)
                appendLog("天枢启动或运行失败：${t.message}")
            } finally {
                ownedProcess?.let { p ->
                    p.destroyForcibly()
                    process.compareAndSet(p, null)
                }
            }
            if (!currentCoroutineContext().isActive) break
            state.value = State.CRASHED
            updateNotification("天枢异常退出，正在重启…")
            delay(5_000)
        }
    }

    private fun ensureAndroidMcp() {
        if (androidMcp != null) return
        val bridge = AndroidMcpServer(this, androidActionApproval)
        androidMcp = bridge
        androidMcpToken = bridge.token
        addSensitiveValue(androidMcpToken)
        scope.launch { bridge.status.collect { androidMcpStatus.value = it } }
        scope.launch {
            androidActionApproval.pending.collect { request ->
                if (request != null) updateNotification("等待操作审批：${request.title}")
                else if (state.value == State.RUNNING) updateNotification("天枢运行中")
            }
        }
        bridge.start()
    }

    private fun appendLog(line: String) {
        log.value = (log.value + redactLogLine(line, sensitiveValues)).takeLast(500)
    }

    override fun onDestroy() {
        androidActionApproval.stop()
        androidMcp?.stop()
        androidMcp = null
        androidMcpStatus.value = AndroidMcpStatus()
        androidMcpToken = ""
        scope.cancel()
        process.getAndSet(null)?.destroyForcibly()
        token = ""
        state.value = State.STOPPED
        super.onDestroy()
    }

    private fun notification(text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("tianshu", "天枢运行状态", NotificationManager.IMPORTANCE_LOW)
        )
        return Notification.Builder(this, "tianshu")
            .setContentTitle("璇玑")
            .setContentText(text)
            .setContentIntent(PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ))
            .setSmallIcon(android.R.drawable.star_on)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) =
        getSystemService(NotificationManager::class.java).notify(1, notification(text))
}

internal fun readBoundedLogLines(
    reader: Reader,
    maxLineChars: Int = 8_192,
    checkActive: () -> Unit,
    onLine: (String) -> Unit,
) {
    require(maxLineChars > 0)
    val buffer = CharArray(4_096)
    val line = StringBuilder()
    var discarding = false
    while (true) {
        checkActive()
        val size = reader.read(buffer)
        if (size < 0) break
        for (index in 0 until size) {
            val char = buffer[index]
            if (char == '\n') {
                if (!discarding) onLine(line.toString().trimEnd('\r'))
                line.setLength(0)
                discarding = false
            } else if (!discarding) {
                if (line.length < maxLineChars) line.append(char)
                else {
                    onLine("$line [truncated]")
                    line.setLength(0)
                    discarding = true
                }
            }
        }
    }
    if (line.isNotEmpty()) onLine(line.toString().trimEnd('\r'))
}





