package org.schabi.newpipe.util;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.EncryptionMethod;

import org.schabi.newpipe.MainActivity;

/**
 * Created by Christian Schabesberger on 28.01.18.
 * Copyright 2018 Christian Schabesberger <chris.schabesberger@mailbox.org>
 * ZipHelper.java is part of NewPipe
 *
 * License: GPL-3.0+
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

public class ZipHelper {

    private static final String TAG = "ZipHelper";
    private static final boolean DEBUG = MainActivity.DEBUG;

    /**
     * This function helps to create zip files.
     * Caution this will override the original file.
     * @param zipPath The zip path where the data should be stored in
     * @param file The path of the file that should be added to zip.
     * @param name The path of the file inside the zip.
     * @param password The password of zip file.
     * @throws Exception
     */
    public static void addFileToZip(String zipPath, String file, String name, char[] password) throws Exception {
        ZipParameters zipParameters = new ZipParameters();
        zipParameters.setFileNameInZip(name);
        ZipFile zipFile;
        if(password != null){
            zipParameters.setEncryptFiles(true);
            zipParameters.setEncryptionMethod(EncryptionMethod.AES);
            zipFile = new ZipFile(zipPath, password);
        }else{
            zipFile = new ZipFile(zipPath);
        }
        zipFile.addFile(file, zipParameters);
    }

    /**
     * This will extract data from Zipfiles.
     * Caution this will override the original file.
     * @param zipPath The path of the zip file.
     * @param name The path of the file inside the zip.
     * @param destDir The path of the directory on disk where the data should be extracted to.
     * @param password The password of zip file.
     * @return will return true if the file was found within the zip file
     * @throws Exception
     */
    public static boolean extractFileFromZip(String zipPath, String name, String destDir, char[] password) throws Exception {
        ZipFile zipFile;
        if(password != null){
            zipFile = new ZipFile(zipPath, password);
        }else{
            zipFile = new ZipFile(zipPath);
        }
        try {
            zipFile.extractFile(name, destDir);
        } catch (ZipException e) {
            if(("No file found with name " + name + " in zip file").equals(e.getMessage())){
                return false;
            }
            throw e;
        }
        return true;
    }
}
