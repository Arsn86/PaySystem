package ru.dz.pay.system.helpers;

import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class FileManager {
    private static final SimpleDateFormat fileDateFormat = new SimpleDateFormat("dd.MM.yyyy_HHmmssSSS");

    private static FileWriter writer;

    private static final FileManager instance = new FileManager();

    private FileManager() {
    }

    public static FileManager getInstance() {
        return instance;
    }

    public boolean init(String type) {
        try {
            close();
            String fileName = "data/file_" + type + "_" + fileDateFormat.format(new Date()) + ".log";
            writer = new FileWriter(fileName);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public synchronized boolean writeString(String s) {
        if (writer != null) {
            try {
                writer.write(s + "\n");
                writer.flush();
                return true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    public void close() {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
