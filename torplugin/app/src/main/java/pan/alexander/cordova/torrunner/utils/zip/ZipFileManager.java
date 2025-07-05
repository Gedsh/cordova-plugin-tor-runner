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

package pan.alexander.cordova.torrunner.utils.zip;

import static pan.alexander.cordova.torrunner.utils.logger.Logger.loge;
import static pan.alexander.cordova.torrunner.utils.logger.Logger.logw;

import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.inject.Inject;

import dalvik.system.ZipPathValidator;

/**
 * @noinspection IOStreamConstructor
 */
public class ZipFileManager {

    @Inject
    public ZipFileManager() {
    }

    public boolean extractZipFromInputStream(InputStream inputStream, String outputPathDir) {
        try (ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {

            File outputFile = new File(removeEndSlash(outputPathDir));

            if (!outputFile.isDirectory()) {
                if (!outputFile.mkdir()) {
                    throw new IllegalStateException("ZipFileManager cannot create output dir " + outputPathDir);
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ZipPathValidator.clearCallback();
            }

            ZipEntry zipEntry = zipInputStream.getNextEntry();

            while (zipEntry != null) {

                if (zipEntry.isDirectory()) {
                    String fileName = zipEntry.getName();
                    File fileFullName = new File(outputPathDir + "/" + removeEndSlash(fileName));

                    if (!fileFullName.isDirectory()) {
                        if (!fileFullName.mkdirs()) {
                            throw new IllegalStateException("ZipFileManager cannot create output dirs structure: dir " + fileFullName.getAbsolutePath());
                        }
                    }
                } else {
                    String fileName = zipEntry.getName();
                    File fileFullName = new File(outputPathDir + "/" + removeEndSlash(fileName));
                    File fileParent = new File(removeEndSlash(Objects.requireNonNull(fileFullName.getParent())));

                    if (!fileParent.isDirectory()) {
                        if (!fileParent.mkdirs()) {
                            throw new IllegalStateException("ZipFileManager cannot create output dirs structure: dir " + fileParent.getAbsolutePath());
                        }
                    }

                    try (OutputStream outputStream = new FileOutputStream(fileFullName)) {
                        copyData(zipInputStream, outputStream);
                    }
                }

                zipEntry = zipInputStream.getNextEntry();
            }
            return true;
        } catch (Exception e) {
            loge("ZipFileManager extractZipFromInputStream", e, true);
            return false;
        }
    }

    public boolean extractZip(String zipPath, String outputPathDir) {
        File inputFile = new File(zipPath);

        if (!inputFile.exists()) {
            loge("ZipFileManager input file missing " + zipPath);
            return false;
        }

        try (InputStream inputStream = new FileInputStream(inputFile)) {
            extractZipFromInputStream(inputStream, outputPathDir);
            return true;
        } catch (Exception e) {
            loge("ZipFileManager extractZip " + zipPath, e);
            return false;
        }
    }

    public boolean createZip(String zipPath, String... inputSource) {
        List<File> inputSources = new ArrayList<>();
        for (String source : inputSource) {
            inputSources.add(new File(source));
        }

        File outputFile = new File(zipPath);
        String outputOuterDir = outputFile.getParent();
        if (outputOuterDir == null) {
            loge("ZipFileManager extractZip outer dir does not exist " + zipPath);
            return false;
        }

        File outputFileDir = new File(removeEndSlash(outputOuterDir));

        if (!outputFileDir.isDirectory()) {
            if (outputFileDir.mkdirs()) {
                loge("ZipFileManager cannot create output dir " + outputFileDir.getAbsolutePath());
                return false;
            }
        }

        try (ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(zipPath))) {
            for (File inputFile : inputSources) {
                String inputOuterDir = inputFile.getParent();
                if (inputOuterDir != null) {
                    addZipEntry(
                            zipOutputStream,
                            removeEndSlash(inputOuterDir),
                            inputFile.getName());
                } else {
                    logw("ZipFileManager input outer dir does not exist " + inputFile.getAbsolutePath());
                }
            }
            return true;
        } catch (Exception e) {
            loge("ZipFileManager createZip " + zipPath, e);
            return false;
        }
    }

    private void addZipEntry(ZipOutputStream zipOutputStream, String inputPath, String fileName) throws Exception {

        String fullPath = inputPath + "/" + fileName;

        File inputFile = new File(fullPath);
        if (inputFile.isDirectory()) {

            File[] files = inputFile.listFiles();

            if (files != null) {
                for (File file : files) {
                    String nextFileName = file.getAbsolutePath().replace(inputPath + "/", "");
                    addZipEntry(zipOutputStream, inputPath, nextFileName);
                }
            }
        } else if (inputFile.isFile()) {
            try (InputStream inputStream = new FileInputStream(fullPath)) {
                ZipEntry zipEntry = new ZipEntry(fileName);
                zipOutputStream.putNextEntry(zipEntry);
                copyData(inputStream, zipOutputStream);
                zipOutputStream.closeEntry();
            }
        } else {
            throw new IllegalStateException("createZip input fault: input no file and no dir " + fullPath);
        }
    }

    private void copyData(InputStream in, OutputStream out) throws Exception {
        byte[] buffer = new byte[8 * 1024];
        int len;
        while ((len = in.read(buffer)) > 0) {
            out.write(buffer, 0, len);
        }
    }

    private String removeEndSlash(String path) {
        if (path.trim().endsWith("/")) {
            path = path.substring(0, path.lastIndexOf("/"));
        }
        return path;
    }
}
