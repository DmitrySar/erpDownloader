package com.example.erpdownloader;

import org.bson.Document;
import org.json.JSONObject;
import org.json.XML;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

@Service
public class Downloader {
    private static final Logger LOG = LoggerFactory.getLogger(Downloader.class);

    public final static String TEMP_XML_FILE_NAME = "c:\\xml\\tmp.xml";
    public final static int QUEUE_MESSAGE_COUNT = 40_000;
    @Autowired
    private QueueSender sender;

    public void start(int skipMonthCount, int limitMonthCount) {
        String erpUrl = "https://proverki.gov.ru/blob/opendata/list.xml";
        System.out.println("\n==========================\nПоехали\n==========================\n");
        download(0, 1, erpUrl);
//        String erknmUrl = "https://proverki.gov.ru/blob/erknm-opendata/list.xml";
//        System.out.println("\n==========================\nПоехали\n==========================\n");
//        download(0, 1, erknmUrl);
    }

    public void download(int skipMonthCount, int limitMonthCount, String urlXmlData) {
        AtomicReference<AtomicLong> count = new AtomicReference<>(new AtomicLong());
        RestTemplate restTemplate = new RestTemplate();
        //Реестр наборов данных
        String xmlContent = restTemplate.getForObject(urlXmlData, String.class);
        JSONObject xmlJSONObj = XML.toJSONObject(xmlContent);
        //4
        String jsonPrettyPrintString = xmlJSONObj.toString();
//        System.out.println(jsonPrettyPrintString);
        Document list = Document.parse(jsonPrettyPrintString);
        //Собираем линки на проверки
        List<String> links = (List<String>) list.get("list", Document.class)
                .get("standardversion", Document.class)
                .get("item", List.class).stream()
                .map(d -> ((Document) d).getString("link"))
                .collect(Collectors.toList());
        //отсортировать по времени
        Map<LocalDate, String> sortedLinks = new TreeMap<>();
        links.stream().filter(l -> l.contains("inspection-20")).forEach(l -> sortedLinks.put(getDateFromLink(l), l));
        sortedLinks.values().forEach(System.out::println);

        for (String link : sortedLinks.values().stream().skip(skipMonthCount).limit(limitMonthCount).collect(Collectors.toList())) {
            System.out.print("\ndownloading " + link + " ");
            String xmlDoc = restTemplate.getForObject(link, String.class);
            System.out.println("[X]");
            xmlJSONObj = XML.toJSONObject(xmlDoc);
            //System.out.println(xmlJSONObj.toString(4));
            Document listZipFiles = Document.parse(xmlJSONObj.toString());
            List<String> zipFilesNames = (List<String>) listZipFiles.get("meta", Document.class).get("data", Document.class).get("dataversion", List.class).stream()
                    .map(d -> ((Document) d).getString("source"))
                    .collect(Collectors.toList());
            //Взять последнюю запись месяца
            //String url = zipFilesNames.get(zipFilesNames.size() - 1);
            //Пробегаемся по всем вложенным
            int fileCount = zipFilesNames.size();
            AtomicInteger currentCount = new AtomicInteger(0);
            zipFilesNames.stream().skip(0).forEach(url -> {
                System.out.println("Обрабатывается " + currentCount.incrementAndGet() + " из " + fileCount);
                //Получить xml
                while (createXml(restTemplate, url))
                    waitSeconds(60);
                //порезать на отдельные проверки и отправить в rabbit
                count.set(cutStringAndSendToRabbitmq(TEMP_XML_FILE_NAME, processSender));
                //count.set(cutStringAndSendToRabbitmq(TEMP_XML_FILE_NAME, text -> System.out.print(text.substring(text.indexOf("ERPID"), text.indexOf("ERPID") + 20))));
                System.out.println("\ncount: " + count.get());
            });
        }
    }

    public AtomicLong cutStringAndSendToRabbitmq(String path, Consumer<StringBuilder> outer) {
        StringBuilder res = new StringBuilder();
        AtomicLong count = new AtomicLong();
        try (Stream<String> stream = Files.lines(Paths.get(path))) {
            stream.forEach(d -> {
                if (d.contains("</INSPECTION>")) {
                    if (res.indexOf("<INSPECTION ") != -1) res.replace(0, res.lastIndexOf("<INSPECTION "), "");
                    res.append(d);
                    outer.accept(res);
                    System.out.print("\t" + count.incrementAndGet() + " \r");
                    res.setLength(0);
                } else {
                    res.append(d);
                }
            });
        } catch (IOException e) {
            LOG.error(e.getLocalizedMessage());
            throw new RuntimeException(e);
        }
        return count;
    }

    private Consumer<StringBuilder> processSender = res -> {
        int messageCount = sender.send(res.toString());
        System.out.print(res.substring(0, 150) + "\r");
        if (messageCount > QUEUE_MESSAGE_COUNT) {
            waitSeconds(10);
        }
    };

    private static void waitSeconds(int timeout) {
        try {
            TimeUnit.SECONDS.sleep(timeout);
        } catch (InterruptedException e) {
            LOG.error(e.getLocalizedMessage());
        }
    }

    private Set<String> erpids = new HashSet<>();
    private Consumer<StringBuilder> processStub = res -> {
        erpids.add(res.substring(res.indexOf("ERPID="), res.indexOf("ITYPE_NAME=")));
        System.out.print(erpids.size() + "\r");
    };

    private static void printToFile(String xml, String path) {
        try (PrintWriter printWriter = new PrintWriter(path)) {
            printWriter.print(xml);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean createXml(RestTemplate restTemplate, String url) {
        System.out.print("\ndownloading " + url);

        System.out.println("[X]");
        System.out.print("save zip ");
        try (FileOutputStream zipOutputStream = new FileOutputStream(TEMP_XML_FILE_NAME + ".zip")) {
            byte[] bytes = restTemplate.getForObject(url, byte[].class);
            zipOutputStream.write(bytes);
            System.out.println("[X]");
        } catch (IOException e) {
            LOG.error(e.getLocalizedMessage());
            return true;
        }
        System.out.print("\nunpacking zip ");

        try (FileSystem fileSystem = FileSystems.newFileSystem(Paths.get(TEMP_XML_FILE_NAME + ".zip"), null);
             FileOutputStream xmlOutputStream = new FileOutputStream(TEMP_XML_FILE_NAME);
             ZipFile zipFile = new ZipFile(TEMP_XML_FILE_NAME + ".zip")) {
            Path fileToExtract = fileSystem.getPath(zipFile.entries().nextElement().getName());
            Files.copy(fileToExtract, xmlOutputStream);
            System.out.println("[X]}");
        } catch (IOException e) {
            LOG.error(e.getLocalizedMessage());
        }
        return false;
    }

    private static LocalDate getDateFromLink(String link) {
        link = link.substring(link.indexOf("inspection-20"), link.indexOf(".xml"));
        link = link.substring(link.indexOf("20"));
        String[] date = link.split("-");
        return LocalDate.of(Integer.parseInt(date[0]), Integer.parseInt(date[1]), 1);

    }
}
