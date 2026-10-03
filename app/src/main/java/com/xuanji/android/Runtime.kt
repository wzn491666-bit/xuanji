package com.xuanji.android

import android.content.Context
import android.system.Os
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.FileVisitResult
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.GZIPInputStream

/**
 * 手机上的 Linux 运行环境（proot + Ubuntu arm64，里面预装了 Node.js 和天枢）。
 *
 * APK 的 assets/runtime/ 里带着：
 *   proot、loader、libtalloc.so.2、libandroid-shmem.so —— proot 和依赖
 *   rootfs.tgz                      —— Ubuntu 根目录，/opt/node 和 /opt/tianshu 已装好
 *   version                         —— 运行环境版本号，变了就重新解压
 */
class Runtime(private val context: Context) {

    private companion object {
        val installMutex = Mutex()
    }

    private val root = File(context.filesDir, "runtime")
    private val bin = File(root, "bin")
    val rootfs = File(root, "rootfs")
    private val tmp = File(context.cacheDir, "proot-tmp")
    private val versionFile = File(root, "installed-version")
    private val staging = File(context.filesDir, "runtime-staging")
    private val backup = File(context.filesDir, "runtime-backup")

    private fun bundledVersion() =
        context.assets.open("runtime/version").bufferedReader().use { it.readText().trim() }

    fun isInstalled(): Boolean {
        return runCatching {
            rootfs.isDirectory && File(bin, "proot").isFile && versionFile.readText() == bundledVersion()
        }.getOrDefault(false)
    }

    /** 解压运行环境。首次约需 1–3 分钟，onProgress 回报已解压的文件数。 */
    suspend fun install(onProgress: (Int) -> Unit) = installMutex.withLock {
        val installContext = currentCoroutineContext()
        val checkActive = { installContext.ensureActive() }
        checkActive()
        if (!root.exists() && backup.exists()) check(backup.renameTo(root)) { "无法恢复运行环境备份" }
        if (isInstalled()) return@withLock
        if (staging.exists()) check(staging.deleteRecursively()) { "无法清理上次未完成的运行环境暂存目录" }
        try {
            val stagedBin = File(staging, "bin")
            check(stagedBin.mkdirs()) { "无法创建运行环境暂存目录" }
            for (name in listOf("proot", "loader", "libtalloc.so.2", "libandroid-shmem.so")) {
                checkActive()
                val out = File(stagedBin, name)
                context.assets.open("runtime/$name").use { input -> out.outputStream().use { input.copyTo(it) } }
                check(out.setExecutable(true, true)) { "无法设置 $name 执行权限" }
            }
            val stagedRootfs = File(staging, "rootfs")
            extractRootfs(stagedRootfs, onProgress, checkActive)
            copyTreeNoFollow(File(rootfs, "root").toPath(), File(stagedRootfs, "root").toPath(), checkActive)
            // Ubuntu 基础镜像没有 DNS 配置，天枢访问 AI 接口需要它
            File(stagedRootfs, "etc/resolv.conf").writeText("nameserver 223.5.5.5\nnameserver 8.8.8.8\n")
            File(stagedRootfs, "etc/hosts").writeText("127.0.0.1 localhost\n::1 localhost\n")
            File(staging, "installed-version").writeText(bundledVersion())

            checkActive()
            // Complete the directory swap without a cancellation point between its renames.
            if (backup.exists()) check(backup.deleteRecursively()) { "无法清理旧运行环境备份" }
            if (root.exists()) check(root.renameTo(backup)) { "无法备份现有运行环境" }
            check(staging.renameTo(root)) { "无法启用新运行环境" }
            backup.deleteRecursively()
        } catch (t: Throwable) {
            staging.deleteRecursively()
            if (!root.exists() && backup.exists()) backup.renameTo(root)
            throw t
        }
    }

    private fun extractRootfs(destination: File, onProgress: (Int) -> Unit, checkActive: () -> Unit) {
        val canonicalRoot = destination.canonicalPath
        var count = 0
        val input = GZIPInputStream(context.assets.open("runtime/rootfs.tgz").buffered(1 shl 16))
        TarArchiveInputStream(input).use { tar ->
            while (true) {
                checkActive()
                val entry = tar.nextEntry ?: break
                val target = File(destination, entry.name)
                // 防止压缩包里的恶意路径（../）写到 rootfs 外面
                if (target.canonicalPath != canonicalRoot && !target.canonicalPath.startsWith("$canonicalRoot${File.separator}")) continue
                when {
                    entry.isDirectory -> target.mkdirs()
                    entry.isSymbolicLink -> {
                        target.parentFile?.mkdirs()
                        target.delete()
                        Files.createSymbolicLink(target.toPath(), File(entry.linkName).toPath())
                    }
                    entry.isLink -> { // 硬链接：Android 私有目录不允许，直接复制一份
                        target.parentFile?.mkdirs()
                        File(destination, entry.linkName).copyTo(target, overwrite = true)
                    }
                    entry.isFile -> {
                        target.parentFile?.mkdirs()
                        target.outputStream().use { output ->
                            copyStreamWithCancellation(tar, output, checkActive)
                        }
                    }
                    else -> continue // 设备文件等，proot 会从手机绑定进来
                }
                if (!entry.isSymbolicLink) Os.chmod(target.path, entry.mode and 0x1FF)
                if (++count % 500 == 0) onProgress(count)
            }
        }
    }

    /** Add app-owned ESM loading hooks; never rewrite upstream modules or project instructions. */
    suspend fun prepareAndroidEnvironment() = installMutex.withLock {
        check(isInstalled()) { "运行环境尚未安装" }
        val directory = File(rootfs, "opt/xuanji")
        check(!Files.isSymbolicLink(directory.toPath())) { "Android 环境目录不能是符号链接" }
        check(directory.canonicalPath.startsWith(rootfs.canonicalPath + File.separator)) { "Android 环境目录超出运行环境" }
        check(directory.isDirectory || directory.mkdirs()) { "无法创建 Android 环境目录" }
        for (name in listOf("register.mjs", "prompt-transform.mjs", "command-transform.mjs", "command-routes.mjs", "android-environment.txt")) {
            currentCoroutineContext().ensureActive()
            val bytes = context.assets.open("android-runtime/$name").use { it.readBytes() }
            val target = File(directory, name)
            if (target.isFile && !Files.isSymbolicLink(target.toPath()) && target.readBytes().contentEquals(bytes)) continue
            val temporary = Files.createTempFile(directory.toPath(), "xuanji-context-", ".tmp")
            try {
                Files.write(temporary, bytes)
                Files.move(temporary, target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } finally { Files.deleteIfExists(temporary) }
        }
    }

    /** 在 Ubuntu 里执行一条命令的 ProcessBuilder。 */
    fun command(shellCommand: String, env: Map<String, String> = emptyMap()): ProcessBuilder {
        tmp.mkdirs()
        val args = listOf(
            // 通过系统动态链接器启动 proot（与 Aether 相同的做法）
            "/system/bin/linker64", File(bin, "proot").path,
            "--link2symlink", "-0", "--kill-on-exit",
            "-r", rootfs.path,
            "-b", "/dev", "-b", "/proc", "-b", "/sys",
            // 手机存储挂到 /sdcard，AI 就能读写手机文件
            "-b", "/storage/emulated/0:/sdcard",
            "-w", "/root",
            "/bin/bash", "-lc", shellCommand,
        )
        return ProcessBuilder(args).redirectErrorStream(true).apply {
            environment().apply {
                clear()
                put("HOME", "/root")
                put("PATH", "/opt/node/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin")
                put("LANG", "C.UTF-8")
                put("TERM", "xterm-256color")
                put("LD_LIBRARY_PATH", bin.path)
                put("PROOT_LOADER", File(bin, "loader").path)
                put("PROOT_TMP_DIR", tmp.path)
                putAll(env)
            }
        }
    }
}

internal fun copyTreeNoFollow(source: Path, destination: Path, checkActive: () -> Unit = {}) {
    checkActive()
    if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) return
    Files.walkFileTree(source, object : SimpleFileVisitor<Path>() {
        override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
            checkActive()
            val target = destination.resolve(source.relativize(dir))
            if (Files.isSymbolicLink(target)) Files.delete(target)
            Files.createDirectories(target)
            return FileVisitResult.CONTINUE
        }

        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            checkActive()
            val target = destination.resolve(source.relativize(file))
            if (attrs.isSymbolicLink) {
                Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING, LinkOption.NOFOLLOW_LINKS)
            } else {
                Files.deleteIfExists(target)
                Files.newInputStream(file).use { input ->
                    Files.newOutputStream(target).use { output -> copyStreamWithCancellation(input, output, checkActive) }
                }
                runCatching { Files.setPosixFilePermissions(target, Files.getPosixFilePermissions(file)) }
            }
            return FileVisitResult.CONTINUE
        }
    })
}

internal fun copyStreamWithCancellation(input: InputStream, output: OutputStream, checkActive: () -> Unit) {
    val buffer = ByteArray(64 * 1024)
    while (true) {
        checkActive()
        val size = input.read(buffer)
        if (size < 0) return
        output.write(buffer, 0, size)
    }
}
