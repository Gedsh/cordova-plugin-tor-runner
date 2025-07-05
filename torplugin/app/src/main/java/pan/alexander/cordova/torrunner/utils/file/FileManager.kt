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

package pan.alexander.cordova.torrunner.utils.file

import pan.alexander.cordova.torrunner.utils.logger.Logger.loge
import java.io.File
import java.io.FileWriter
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileManager @Inject constructor() {

    fun createFolder(folder: File): Boolean = try {
        if (!folder.exists()) folder.mkdirs() else true
    } catch (e: Exception) {
        loge("FileManager createFolder ${folder.path}", e)
        false
    }

    fun deleteFolder(folder: File): Boolean = try {
        if (folder.isDirectory) {
            folder.listFiles()?.forEach {
                if (it.isDirectory) deleteFolder(it)
                else it.delete()
            }
            folder.delete()
        }
        true
    } catch (e: Exception) {
        loge("FileManager deleteFolder ${folder.path}", e)
        false
    }

    fun copyFolder(source: File, destination: File): Boolean = try {
        if (!source.isDirectory) {
            throw IOException("File ${source.name} is not a directory")
        }
        if (!destination.exists()) destination.mkdirs()

        source.listFiles()?.forEach { file ->
            val destFile = File(destination, file.name)
            if (file.isDirectory) copyFolder(file, destFile)
            else file.copyTo(destFile, overwrite = true)
        }
        true
    } catch (e: Exception) {
        loge("FileManager copyFolder ${source.path} to ${destination.path}", e)
        false
    }

    fun moveFolder(source: File, destination: File): Boolean = try {
        val success = copyFolder(source, destination)
        if (success) deleteFolder(source)
        success
    } catch (e: Exception) {
        loge("FileManager moveFolder ${source.path} to ${destination.path}", e)
        false
    }

    fun createFile(file: File, content: String = ""): Boolean =
        try {
            if (!file.exists()) {
                file.parentFile?.mkdirs()
                file.createNewFile()
            }
            if (content.isNotEmpty()) {
                file.writeText(content)
            }
            true
        } catch (e: IOException) {
            loge("FileManager createFile ${file.path}", e)
            false
        }

    fun deleteFile(file: File): Boolean = try {
        file.delete()
    } catch (e: Exception) {
        loge("FileManager deleteFile ${file.path}", e)
        false
    }

    fun copyFile(source: File, destination: File): Boolean =
        try {
            destination.parentFile?.mkdirs()
            source.copyTo(destination, overwrite = true)
            true
        } catch (e: IOException) {
            loge("FileManager copyFile ${source.path} to ${destination.path}", e)
            false
        }

    fun moveFile(source: File, destination: File): Boolean =
        try {
            copyFile(source, destination) && source.delete()
        } catch (e: Exception) {
            loge("FileManager moveFile ${source.path} to ${destination.path}", e)
            false
        }

    fun readFile(path: String): List<String> =
        try {
            val file = File(path)
            readFile(file)
        } catch (e: Exception) {
            loge("FileManager readFile $path", e)
            emptyList()
        }

    fun readFile(file: File): List<String> =
        try {
            if (file.exists()) file.readLines() else emptyList()
        } catch (e: IOException) {
            loge("FileManager readFile ${file.path}", e)
            emptyList()
        }

    fun rewriteFile(path: String, content: List<String>): Boolean =
        try {
            val file = File(path)
            if (file.isFile) {
                createFile(file)
            }
            rewriteFile(file, content)
        } catch (e: Exception) {
            loge("FileManager rewriteFile $path", e)
            false
        }

    fun rewriteFile(file: File, content: List<String>): Boolean =
        try {
            if (file.isFile) {
                createFile(file)
            }
            file.printWriter().use { out ->
                content.forEach(out::println)
            }
            true
        } catch (e: IOException) {
            loge("FileManager rewriteFile ${file.path}", e)
            false
        }

    fun appendToFile(file: File, content: String): Boolean =
        try {
            FileWriter(file, true).buffered().use { writer ->
                content.forEach { line -> writer.write(line + System.lineSeparator()) }
            }
            true
        } catch (e: IOException) {
            loge("FileManager appendToFile ${file.path}", e)
            false
        }
}
