/*
    This file is part of Cordova Plugin Tor Runner.

    Cordova Plugin Tor Runner is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Cordova Plugin Tor Runner is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Cordova Plugin Tor Runner.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2025 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.cordova.torrunner.domain.installer

import android.annotation.SuppressLint
import androidx.annotation.WorkerThread
import pan.alexander.cordova.torrunner.domain.configuration.ConfigurationRepository
import pan.alexander.cordova.torrunner.domain.core.CoreState
import pan.alexander.cordova.torrunner.domain.core.CoreStatus
import pan.alexander.cordova.torrunner.utils.file.FileManager
import pan.alexander.cordova.torrunner.utils.logger.Logger.loge
import pan.alexander.cordova.torrunner.utils.logger.Logger.logi
import pan.alexander.cordova.torrunner.utils.zip.ZipFileManager
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Installer @Inject constructor(
    private val configurationRepository: ConfigurationRepository,
    private val coreStatus: CoreStatus,
    private val fileManager: FileManager,
    private val zipManager: dagger.Lazy<ZipFileManager>
) {

    @Volatile
    var installing = AtomicBoolean(false)

    @WorkerThread
    fun installTorIfRequired(): Boolean {
        if (installing.compareAndSet(false, true)) {
            try {
                if (!configurationRepository.isTorConfigurationAvailable()) {
                    return installTorConfiguration()
                }
            } catch (e: Exception) {
                loge("Installer installTorIfRequired", e)
                return false
            } finally {
                installing.set(false)
            }
        }
        return true
    }

    @WorkerThread
    fun reinstallTor(): Boolean {
        if (installing.compareAndSet(false, true)) {
            try {
                if (!configurationRepository.isTorConfigurationAvailable()) {
                    return installTorConfiguration()
                }
            } catch (e: Exception) {
                loge("Installer reinstallTor", e)
                return false
            } finally {
                installing.set(false)
            }
        }
        return true
    }


    private fun installTorConfiguration(): Boolean {
        logi("Start adding Tor configuration")
        var success = removeInstallationDirs()
        if (success) {
            logi("Old installation folders have been deleted")
            success = extractTorConfigurationFiles()
        }
        if (success) {
            logi("Tor configuration files have been extracted")
            success = adjustTorConfigurationPaths()
        }
        if (success) {
            logi("Tor paths have been adjusted")
            createLogsDir()
        }
        if (success) {
            logi("Logs folder has been created")
            logi("Configuring Tor is successful")
        } else {
            coreStatus.torState == CoreState.FAULT
            loge("Configuring Tor is failed")
        }
        return success
    }

    private fun removeInstallationDirs() =
        fileManager.deleteFolder(
            File(configurationRepository.getTorConfigurationDir())
        )

    private fun createLogsDir() =
        fileManager.createFolder(File(configurationRepository.getLogsDir()))

    private fun extractTorConfigurationFiles() =
        zipManager.get().extractZipFromInputStream(
            configurationRepository.getTorAssetStream(),
            configurationRepository.getAppDataDir()
        )

    @SuppressLint("SdCardPath")
    private fun adjustTorConfigurationPaths(): Boolean {
        fileManager.readFile(File(configurationRepository.getTorConfPath())).let {
            val torConf = it.toMutableList()
            var savingRequired = false
            for (i in torConf.indices) {
                val line = torConf[i]
                if (line.contains("/data/user/0/pocketnet.app")) {
                    val correctedLine = line.replace(
                        Regex("/data/user/0/pocketnet.app.*?/"),
                        "${configurationRepository.getAppDataDir()}/"
                    )
                    if (line != correctedLine) {
                        torConf[i] = correctedLine
                        savingRequired = true
                    }
                }
            }
            if (savingRequired) {
                fileManager.rewriteFile(File(configurationRepository.getTorConfPath()), torConf)
            }
        }
        return true
    }
}
