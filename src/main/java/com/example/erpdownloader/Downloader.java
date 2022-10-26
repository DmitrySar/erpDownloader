package com.example.erpdownloader;

import org.bson.Document;
import org.json.JSONObject;
import org.json.XML;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Service
public class Downloader {

    public final static String TEMP_XML_FILE_NAME = "c:\\xml\\tmp.xml";
    @Autowired
    private QueueSender sender;

    public void download(int skipMonthCount, int limitMonthCount, int skipCountInMonth, int limitCountInMonth) {

        RestTemplate restTemplate = new RestTemplate();
        //Реестр наборов данных
        String xmlContent = restTemplate.getForObject("https://proverki.gov.ru/blob/opendata/list.xml", String.class);
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

        //TODO Пробежаться по всем
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
            zipFilesNames.stream().skip(skipCountInMonth).limit(limitCountInMonth).forEach(url -> {
                //Получить xml
                createXml(restTemplate, url);
                //порезать на отдельные проверки и отправить в rabbit
                cutStringAndSendToRabbitmq(TEMP_XML_FILE_NAME);
            });
        }
    }

    public void cutStringAndSendToRabbitmq(String path) {
        StringBuilder res = new StringBuilder();
        AtomicLong count = new AtomicLong();
        try (Stream<String> stream = Files.lines(Paths.get(path))) {
            stream.forEach(d -> {
                if (d.contains("</INSPECTION>")) {
                    if (res.indexOf("<INSPECTION ") != -1) res.replace(0, res.lastIndexOf("<INSPECTION "), "");
                    res.append(d);
                    sender.send(res.toString());
                    System.out.println(count.incrementAndGet() + res.substring(0, 150));
                    res.setLength(0);
                } else {
                    res.append(d);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void printToFile(String xml, String path) {
        try (PrintWriter printWriter = new PrintWriter(path)) {
            printWriter.print(xml);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private static void createXml(RestTemplate restTemplate, String url) {
        System.out.print("downloading " + url);
        byte[] bytes = restTemplate.getForObject(url, byte[].class);
        System.out.println("[X]");
        System.out.print("save zip ");
        try (FileOutputStream zipOutputStream = new FileOutputStream(TEMP_XML_FILE_NAME + ".zip")) {
            zipOutputStream.write(bytes);
            System.out.println("[X]");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.print("unpacking zip ");

        try (FileSystem fileSystem = FileSystems.newFileSystem(Paths.get(TEMP_XML_FILE_NAME + ".zip"), null);
             FileOutputStream xmlOutputStream = new FileOutputStream(TEMP_XML_FILE_NAME);
             ZipFile zipFile = new ZipFile(TEMP_XML_FILE_NAME + ".zip")) {
            Path fileToExtract = fileSystem.getPath(zipFile.entries().nextElement().getName());
            Files.copy(fileToExtract, xmlOutputStream);
            System.out.println("[X]}");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static LocalDate getDateFromLink(String link) {
        link = link.substring(link.indexOf("inspection-20"), link.indexOf(".xml"));
        link = link.substring(link.indexOf("20"));
        String[] date = link.split("-");
        return LocalDate.of(Integer.parseInt(date[0]), Integer.parseInt(date[1]), 1);

    }
}
