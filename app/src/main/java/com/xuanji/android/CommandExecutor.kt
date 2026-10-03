package com.xuanji.android

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal fun shizukuProcessNameSuffix() = "xuanji-shell"

internal suspend fun <T> runOwnedBlocking(cleanup: () -> Unit, operation: () -> T): T = coroutineScope {
    val task = async(Dispatchers.IO) { operation() }
    try {
        task.await()
    } finally {
        withContext(NonCancellable + Dispatchers.IO) {
            try { cleanup() } finally { task.join() }
        }
    }
}

data class CommandStatus(val root: String = "未检查", val shizuku: String = "未连接")

class CommandExecutor(private val context: Context) {
    private companion object {
        const val TAG = "CommandExecutor"
        val shizukuMutex = Mutex()
    }
    val status = MutableStateFlow(CommandStatus())
    private var rootGranted = false

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, _ -> refreshShizuku() }
    private val binderReceived = Shizuku.OnBinderReceivedListener { refreshShizuku() }
    private val binderDead = Shizuku.OnBinderDeadListener { refreshShizuku() }

    init {
        Shizuku.addRequestPermissionResultListener(permissionListener)
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        refreshShizuku()
    }

    fun refreshShizuku() {
        val label = when {
            !Shizuku.pingBinder() -> "未运行"
            runCatching { Shizuku.checkSelfPermission() }.getOrNull() == PackageManager.PERMISSION_GRANTED -> "已授权"
            else -> "待授权"
        }
        status.value = status.value.copy(shizuku = label)
    }

    fun requestShizuku() {
        if (Shizuku.pingBinder() && status.value.shizuku != "已授权") Shizuku.requestPermission(3100)
        else refreshShizuku()
    }

    suspend fun checkRoot() = withContext(Dispatchers.IO) {
        val granted = runCatching { runInterruptible { runShell("id", ShellBackend.ROOT, 10) } }
            .onFailure { if (it is CancellationException) throw it }
            .getOrNull()?.let { !it.timedOut && it.exitCode == 0 && "uid=0" in it.output } == true
        rootGranted = granted
        status.value = status.value.copy(root = if (granted) "已授权" else "不可用或未授权")
    }

    suspend fun execute(command: String, backend: ShellBackend? = null): ShellResult = withContext(Dispatchers.IO) {
        require(command.isNotBlank()) { "命令不能为空" }
        refreshShizuku()
        if (backend == null) checkRoot()
        val chosen = backend ?: selectPrivilegedBackend(rootGranted, status.value.shizuku == "已授权")
            ?: error("需要先授权 root 或 Shizuku")
        when (chosen) {
            ShellBackend.ROOT -> {
                check(rootGranted) { "root 尚未授权" }
                runInterruptible { runShell(command, chosen) }
            }
            ShellBackend.SHIZUKU -> {
                check(status.value.shizuku == "已授权") { "Shizuku 尚未授权" }
                shizukuMutex.withLock { executeShizuku(command) }
            }
            ShellBackend.APP -> runInterruptible { runShell(command, chosen) }
        }
    }

    private suspend fun executeShizuku(command: String): ShellResult {
        val executionContext = currentCoroutineContext()
        val latch = CountDownLatch(1)
        var binder: IBinder? = null
        var bound = false
        var closed = false
        val bindingLock = Any()
        val args = Shizuku.UserServiceArgs(ComponentName(context, ShizukuShellService::class.java))
            .daemon(false).version(PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0)).toInt())
            .processNameSuffix(shizukuProcessNameSuffix())
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) { binder = service; latch.countDown() }
            override fun onServiceDisconnected(name: ComponentName) { binder = null; latch.countDown() }
        }
        fun closeBinding() = synchronized(bindingLock) {
            if (!closed) {
                closed = true
                if (bound) runCatching { Shizuku.unbindUserService(args, connection, true) }
                    .onFailure { Log.w(TAG, "Shizuku 服务清理失败", it) }
                latch.countDown()
            }
        }
        return runOwnedBlocking(::closeBinding) {
            try {
                synchronized(bindingLock) {
                    executionContext.ensureActive()
                    check(!closed) { "Shizuku 连接已关闭" }
                    Shizuku.bindUserService(args, connection)
                    bound = true
                }
                val connected = latch.await(10, TimeUnit.SECONDS)
                executionContext.ensureActive()
                check(connected && binder != null) { "Shizuku 服务连接失败" }
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                try {
                    data.writeString(command)
                    check(binder!!.transact(ShizukuShellService.EXECUTE, data, reply, 0)) { "Shizuku 命令未执行" }
                    reply.readException()
                    ShellResult(ShellBackend.SHIZUKU, reply.readInt(), reply.readString().orEmpty(), reply.readInt() != 0)
                } finally {
                    data.recycle()
                    reply.recycle()
                }
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                Log.e(TAG, "Shizuku 命令执行失败", t)
                throw t
            }
        }
    }

    fun close() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        Shizuku.removeBinderReceivedListener(binderReceived)
        Shizuku.removeBinderDeadListener(binderDead)
    }
}
