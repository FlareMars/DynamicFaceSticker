package com.flarejaven.example.jnithread.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Created by root on 17-3-14.
 */

public class FileUtils {

    public static String readToString(File file) throws IOException {
        if (file != null) {
            InputStreamReader isr = new InputStreamReader(new FileInputStream(file));
            BufferedReader br = new BufferedReader(isr);
            String line;
            StringBuilder builder = new StringBuilder();
            while((line = br.readLine()) != null) {
                builder.append(line);
            }
            br.close();
            isr.close();

            return builder.toString();
        }
        return "";
    }
}
