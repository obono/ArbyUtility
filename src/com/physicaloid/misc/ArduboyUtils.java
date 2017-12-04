/*
 * Copyright (C) 2017 OBONO
 * http://d.hatena.ne.jp/OBONO/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.physicaloid.misc;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.json.JSONArray;
import org.json.JSONObject;

public class ArduboyUtils {

    private static final int BUFFER_SIZE = 1024 * 1024;

    public static byte[] extractHexFromArduboy(File arduboyFile) {
        byte[] ret;
        try {
            byte[] infoData = extractFileFromZip(arduboyFile, "info.json");
            JSONObject infoJson = new JSONObject(new String(infoData, "UTF-8"));
            JSONArray binariesJson = infoJson.getJSONArray("binaries");
            JSONObject binaryJson = (JSONObject) binariesJson.get(0);
            String hexFileName = binaryJson.getString("filename");
            ret = extractFileFromZip(arduboyFile, hexFileName);
        } catch (Exception e) {
            e.printStackTrace();
            ret = null;
        }
        return ret;
    }

    public static byte[] extractFileFromZip(File zipFile, String fname) {
        byte[] ret = null;
        ZipInputStream zin = null;
        try {
            zin = new ZipInputStream(new FileInputStream(zipFile));
            for (ZipEntry entry = zin.getNextEntry(); entry != null; entry = zin.getNextEntry()) {
                if (entry.getName().equals(fname)) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int length;
                    while ((length = zin.read(buffer)) >= 0) {
                        out.write(buffer, 0, length);  
                    }
                    out.close();
                    ret = out.toByteArray();
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            ret = null;
        } finally {
            try {
                zin.close();
            } catch (Exception e) {
                // do nothing
            }
        }
        return ret;
    }
}
