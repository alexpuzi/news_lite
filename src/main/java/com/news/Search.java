package com.news;

import com.sun.syndication.feed.synd.SyndContent;
import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndFeed;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public class Search {
    static AtomicBoolean isStop = new AtomicBoolean(false);
    static AtomicBoolean isSearchNow = new AtomicBoolean(false);
    static AtomicBoolean isSearchFinished;
    static double searchTime;
    static int j = 1;
    static DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    static LocalDateTime now = LocalDateTime.now();
    static String today = dtf.format(now);
    static SimpleDateFormat date_format = new SimpleDateFormat("dd.MM.yyyy HH:mm");
    static ArrayList<String> dataForEmail = new ArrayList<>();
    static int newsCount = 0;

    //Main search
    public static void mainSearch(String pSearchType) {
        if (!isSearchNow.get()) {
            Common.console("status: search started");
            Main.LOGGER.log(Level.INFO, "search started");
            //выборка актуальных источников перед поиском из БД
            SQLite.selectSources("smi");
            isSearchNow.set(true);
            Gui.timeStart = System.currentTimeMillis();
            //Common.text = "";
            Gui.labelInfo.setText("");
            Search.j = 1;
            Gui.model.setRowCount(0);
            if (!Gui.wasClickInTableForAnalysis.get()) Gui.model_for_analysis.setRowCount(0);
            newsCount = 0;
            Gui.labelSum.setText("" + newsCount);
            Search.isStop.set(false);
            Gui.find_word = Gui.textField.getText().toLowerCase();

            if (pSearchType.equals("word")) {
            Gui.searchBtnTop.setVisible(false);
            Gui.stopBtnTop.setVisible(true);
            } else if (pSearchType.equals("words")) {
                Gui.searchBtnBottom.setVisible(false);
                Gui.stopBtnBottom.setVisible(true);
            }

            isSearchFinished = new AtomicBoolean(false);
            Common.statusLabel(isSearchFinished, "Searching");
            Gui.sendEmailBtn.setIcon(Gui.send);
            new Thread(Common::fill).start();
            try {
                // начало транзакции
                PreparedStatement st = SQLite.connection.prepareStatement("insert into news_dual(title) values (?)");
                String q_begin = "BEGIN TRANSACTION";
                Statement st_begin = SQLite.connection.createStatement();
                st_begin.executeUpdate(q_begin);

                SyndParser parser = new SyndParser();
                for (Common.smi_number = 0; Common.smi_number < Common.smi_link.size(); Common.smi_number++) {
                    try {
                        try {
                            if (isStop.get()) return;
                            SyndFeed feed = parser.parseFeed(Common.smi_link.get(Common.smi_number));
                            for (Object message : feed.getEntries()) {
                                j++;
                                SyndEntry entry = (SyndEntry) message;
                                SyndContent content = entry.getDescription();
                                String smi_source = Common.smi_source.get(Common.smi_number);
                                String title = entry.getTitle();
                                assert content != null;
                                String newsDescribe = content.getValue()
                                        .trim()
                                        .replace("<p>", "")
                                        .replace("</p>", "")
                                        .replace("<br />", "");
                                if (newsDescribe.contains("<img")
                                        || newsDescribe.contains("href")
                                        || newsDescribe.contains("<div")
                                        || newsDescribe.contains("&#34")
                                        || newsDescribe.contains("<p lang")
                                        || newsDescribe.contains("&quot")
                                        || newsDescribe.contains("<span")
                                        || newsDescribe.contains("<ol")
                                        || newsDescribe.equals("")
                                ) newsDescribe = title;
                                Date pubDate = entry.getPublishedDate();
                                String dateToEmail = date_format.format(pubDate);
                                String link = entry.getLink();

                                if (pSearchType.equals("word")) {
                                    if (entry.getTitle().toLowerCase().contains(Gui.find_word.toLowerCase()) && entry.getTitle().length() > 15) {

                                        //отсеиваем новости, которые уже были найдены ранее
                                        if (SQLite.isTitleExists(Common.sha256(entry.getTitle() + entry.getPublishedDate()))) {
                                            continue;
                                        }

                                        //Data for a table
                                        if (entry.getPublishedDate() != null) {
                                            Date docDate = entry.getPublishedDate();
                                            Date curent_date = new Date();
                                            int date_diff = Common.compareDatesOnly(curent_date, docDate);

                                            // вставка в архив всех новостей
                                            try {
                                                if (!SQLite.isTitleInArchiveExists(entry.getTitle() + entry.getPublishedDate())) {
                                                    SQLite.insertAllTitles(entry.getTitle(), entry.getPublishedDate().toString());
                                                }
                                            } catch (Exception s) {
                                                System.out.println(s.getMessage());
                                            }

                                            if (Gui.todayOrNotChbx.getState() && (date_diff != 0)) {
                                                newsCount++;
                                                Gui.labelSum.setText(String.valueOf(newsCount));
                                                dataForEmail.add(newsCount + ") " + title + "\n" + link + "\n" + newsDescribe + "\n" +
                                                        smi_source + " - " + dateToEmail);

                                                Object[] row = new Object[]{
                                                        newsCount,
                                                        smi_source,
                                                        title,
                                                        //newsDescribe,
                                                        pubDate,
                                                        link
                                                };
                                                Gui.model.addRow(row);

                                                //SQLite
                                                String[] subStr = entry.getTitle().split(" ");
                                                for (String s : subStr) {
                                                    if (s.length() > 3) {
                                                        assert st != null;
                                                        st.setString(1, Common.delNoLetter(s).toLowerCase());
                                                        st.executeUpdate();
                                                    }
                                                }
                                                SQLite.insertTitleIn256(Common.sha256(entry.getTitle() + entry.getPublishedDate()));

                                            } else if (!Gui.todayOrNotChbx.getState()) {
                                                newsCount++;
                                                Gui.labelSum.setText(String.valueOf(newsCount));
                                                dataForEmail.add(newsCount + ") " + title + "\n" + link + "\n" + newsDescribe + "\n" +
                                                        smi_source + " - " + dateToEmail);

                                                Object[] row = new Object[]{
                                                        newsCount,
                                                        smi_source,
                                                        title,
                                                        //newsDescribe,
                                                        pubDate,
                                                        link
                                                };
                                                Gui.model.addRow(row);

                                                // SQLite
                                                String[] subStr = entry.getTitle().split(" ");
                                                for (String s : subStr) {
                                                    if (s.length() > 3) {
                                                        assert st != null;
                                                        st.setString(1, Common.delNoLetter(s).toLowerCase());
                                                        st.executeUpdate();
                                                    }
                                                }
                                                SQLite.insertTitleIn256(Common.sha256(entry.getTitle() + entry.getPublishedDate()));
                                            }
                                        }
                                    }
                                } else if (pSearchType.equals("words")) {
                                    for (String it : Common.getKeywordsFromFile()) {
                                        if (entry.getTitle().toLowerCase().contains(it.toLowerCase()) && entry.getTitle().length() > 15) {

                                            // отсеиваем новости которые были обнаружены ранее
                                            if (SQLite.isTitleExists(Common.sha256(entry.getTitle() + entry.getPublishedDate()))) {
                                                continue;
                                            }

                                            //Data for a table
                                            if (entry.getPublishedDate() != null) {
                                                Date docDate = entry.getPublishedDate();
                                                Date curent_date = new Date();
                                                int date_diff = Common.compareDatesOnly(curent_date, docDate);

                                                if (Gui.todayOrNotChbx.getState() && (date_diff != 0)) {
                                                    newsCount++;
                                                    Gui.labelSum.setText(String.valueOf(newsCount));
                                                    dataForEmail.add(newsCount + ") " + title + "\n" + link + "\n" + newsDescribe + "\n" +
                                                            smi_source + " - " + dateToEmail);

                                                    Object[] row = new Object[]{
                                                            newsCount,
                                                            smi_source,
                                                            title,
                                                            //newsDescribe,
                                                            pubDate,
                                                            link
                                                    };
                                                    Gui.model.addRow(row);

                                                    //SQLite
                                                    String[] subStr = entry.getTitle().split(" ");
                                                    for (String s : subStr) {
                                                        if (s.length() > 3) {
                                                            assert st != null;
                                                            st.setString(1, Common.delNoLetter(s).toLowerCase());
                                                            st.executeUpdate();
                                                        }
                                                    }
                                                    SQLite.insertTitleIn256(Common.sha256(entry.getTitle() + entry.getPublishedDate()));
                                                } else if (!Gui.todayOrNotChbx.getState()) {
                                                    newsCount++;
                                                    Gui.labelSum.setText(String.valueOf(newsCount));
                                                    dataForEmail.add(newsCount + ") " + title + "\n" + link + "\n" + newsDescribe + "\n" +
                                                            smi_source + " - " + dateToEmail);

                                                    Object[] row = new Object[]{
                                                            newsCount,
                                                            smi_source,
                                                            title,
                                                            pubDate,
                                                            link
                                                    };
                                                    Gui.model.addRow(row);

                                                    //SQLite
                                                    String[] subStr = entry.getTitle().split(" ");
                                                    for (String s : subStr) {
                                                        if (s.length() > 3) {
                                                            assert st != null;
                                                            st.setString(1, Common.delNoLetter(s).toLowerCase());
                                                            st.executeUpdate();
                                                        }
                                                    }
                                                    SQLite.insertTitleIn256(Common.sha256(entry.getTitle() + entry.getPublishedDate()));
                                                }
                                            }
                                        }
                                    }
                                }
                                if (isStop.get()) return;
                            }
                            if (!Gui.isOnlyLastNews) SQLite.deleteFrom256();
                        } catch (Exception no_rss) {
                            Gui.labelInfo.setText("RssList: " + (char) 34 + Common.smi_link.get(Common.smi_number) + (char) 34 + " is not available");
                        }
                    } catch (Exception e) {
                        Common.console("status: to many news.. please restart the application!");
                        isStop.set(true);
                    }
                }
                //Время поиска
                Gui.timeEnd = System.currentTimeMillis();
                searchTime = (Gui.timeEnd - Gui.timeStart) / 1000;
                DecimalFormat f = new DecimalFormat("##.00");
                Common.console("status: search completed in " + f.format(searchTime) + " s.");
                isSearchNow.set(false);
                Gui.wasClickInTableForAnalysis.set(false);

                isSearchFinished.set(true);
                Gui.progressBar.setValue(100);
                Gui.table.setAutoCreateRowSorter(true);
                Gui.table_for_analysis.setAutoCreateRowSorter(true);
                Gui.search_animation.setText("total news: ");

                if (pSearchType.equals("word")) {
                    Gui.searchBtnTop.setVisible(true);
                    Gui.stopBtnTop.setVisible(false);
                } else if (pSearchType.equals("words")) {
                    Gui.searchBtnBottom.setVisible(true);
                    Gui.stopBtnBottom.setVisible(false);
                }

                // коммитим транзакцию
                String q_commit = "COMMIT";
                Statement st_commit = SQLite.connection.createStatement();
                st_commit.executeUpdate(q_commit);

                // удаляем все пустые строки
                String q_del = "delete from news_dual where title = ''";
                Statement st_del = SQLite.connection.createStatement();
                st_del.executeUpdate(q_del);

                // Заполняем таблицу анализа
                if (!Gui.wasClickInTableForAnalysis.get()) SQLite.selectSqlite();

                //auto send after search
                if (Gui.autoSendMessage.getState() && (Gui.model.getRowCount() > 0)) {
                    Gui.sendEmailBtn.doClick();
                }

                if (pSearchType.equals("word")) Common.console("info: number of news items in the archive = " + SQLite.archiveNewsCount());
                Main.LOGGER.log(Level.INFO, "search finished");
            } catch (Exception e) {
                try {
                String q_begin = "ROLLBACK";
                Statement st_begin = SQLite.connection.createStatement();
                st_begin.executeUpdate(q_begin);
                } catch (SQLException sql) {
                    sql.printStackTrace();
                }
                e.printStackTrace();
                isStop.set(true);
            }
        }
    }
}
