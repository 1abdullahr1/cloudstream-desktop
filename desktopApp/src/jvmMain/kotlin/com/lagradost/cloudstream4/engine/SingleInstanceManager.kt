package com.lagradost.cloudstream4.engine

import com.lagradost.cloudstream4.FilePreferenceStore
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock

object SingleInstanceManager {
    private var lockChannel: FileChannel? = null
    private var fileLock: FileLock? = null

    /**
     * Attempts to acquire a single-instance file lock.
     * Returns true if this is the only running instance, false if another instance is already running.
     */
    fun acquireLock(): Boolean {
        return try {
            val lockFile = File(FilePreferenceStore.appDirectory, ".app.lock")
            lockFile.parentFile?.mkdirs()
            val raf = RandomAccessFile(lockFile, "rw")
            lockChannel = raf.channel
            fileLock = lockChannel?.tryLock()

            if (fileLock != null) {
                Runtime.getRuntime().addShutdownHook(Thread {
                    releaseLock()
                })
                true
            } else {
                false
            }
        } catch (_: Throwable) {
            // If lock cannot be determined, allow process to continue
            true
        }
    }

    fun releaseLock() {
        try {
            fileLock?.release()
            fileLock = null
            lockChannel?.close()
            lockChannel = null
        } catch (_: Throwable) {}
    }
}
