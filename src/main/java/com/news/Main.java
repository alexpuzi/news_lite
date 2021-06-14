package com.news;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.logging.*;

public class Main {
    static SimpleDateFormat date_format = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
    static String directoryPath = "C:\\Users\\Public\\Documents\\News\\";
    static String settingsPath = directoryPath + "config.txt";
    static String logPath = directoryPath + "log.txt";
    public static final Logger LOGGER = Logger.getLogger("");

    // создание директорий и файлов
    static {
        File directory = new File(directoryPath);
        File fav_file = new File(settingsPath);
        File log_file = new File(logPath);

        try {
            if (!directory.exists()) directory.mkdirs();
            if (!fav_file.exists()) fav_file.createNewFile();
            if (!log_file.exists()) log_file.createNewFile();
            // запись лога в файл
            Handler handler = new FileHandler(logPath, true);
            handler.setLevel(Level.ALL);
            handler.setEncoding("UTF-8");
            handler.setFormatter(new SimpleFormatter());
            LOGGER.addHandler(handler);
            LOGGER.getHandlers()[0].setFormatter(new Formatter() {
                @Override
                public String format(LogRecord record) {
                    return record.getLevel() + " " + record.getMessage() + " " + date_format.format(record.getMillis()) + "\n";
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }

        // создание файлов SQLite
        File sqliteIsExists = new File(directoryPath + "sqlite3.exe");
        if (!sqliteIsExists.exists()) {
            Common.copyFiles(Main.class.getResource("/sqlite3.exe"), directoryPath + "sqlite3.exe");
            Common.copyFiles(Main.class.getResource("/sqlite3.dll"), directoryPath + "sqlite3.dll");
            Common.copyFiles(Main.class.getResource("/sqlite3.def"), directoryPath + "sqlite3.def");
            Common.copyFiles(Main.class.getResource("/news.db"), directoryPath + "news.db");

        }
    }

    public static void main(String[] args) throws IOException {
        LOGGER.log(Level.INFO, "Application started");
        new Gui();
        Common.getSettingsFromFile();
        Gui.newsIntervalCbox.setEnabled(Gui.todayOrNotChbx.getState());
        SQLite.open();

        // проверка подключения к интернету
        if (!InternetAvailabilityChecker.isInternetAvailable()) {
            Common.console("status: no internet connection");
        }
    }
}
