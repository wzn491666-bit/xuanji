package com.xuanji.android

import android.os.Binder
import android.os.Parcel
import kotlin.system.exitProcess

/** Runs inside Shizuku's shell or root process; only command output crosses Binder. */
class ShizukuShellService : Binder() {
    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code == DESTROY) {
            ShellProcesses.destroyAll()
            exitProcess(0)
        }
        if (code != EXECUTE) return super.onTransact(code, data, reply, flags)
        val command = data.readString() ?: return false
        val result = runShell(command, ShellBackend.SHIZUKU)
        reply?.apply {
            writeNoException()
            writeInt(result.exitCode)
            writeString(result.output)
            writeInt(if (result.timedOut) 1 else 0)
        }
        return true
    }

    companion object {
        const val EXECUTE = 1
        const val DESTROY = 16777115
    }
}
