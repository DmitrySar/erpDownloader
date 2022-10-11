package com.example.erpdownloader;

import org.bson.Document;
import org.json.JSONObject;
import org.json.XML;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class Downloader {

    @Autowired
    private QueueSender sender;

    public void download() {

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
        //sortedLinks.values().forEach(System.out::println);
        //TODO Пробежаться по всем
        for (String link : links) {
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
            String url = zipFilesNames.get(zipFilesNames.size() - 1);
            //Получить xml
            String xml = createXml(restTemplate, url);
            //порезать на отдельные проверки и отправить в rabbit
            int length = xml.length();
            while (xml.contains("</INSPECTION>")) {
                int size = xml.indexOf("</INSPECTION>");
                String inspection = xml.substring(xml.indexOf("<INSPECTION "), size) + "</INSPECTION>";
                //отправить в rabbit
                int index = xml.indexOf("\" ERPID=\"");
                System.out.println(xml.substring(index, index + 22) + " осталось символов " + ((double)xml.length() / length) * 100 + "%");
                if (sender.send(inspection) > 2) try {
                    System.out.println("pause");
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                xml = xml.substring(size + 1);
            }
        }
    }

    private static void printToFile(String xml, String path) {
        try (PrintWriter printWriter = new PrintWriter(path)) {
            printWriter.print(xml);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private static String createXml(RestTemplate restTemplate, String url) {
        String xml;
        System.out.print("Downloading " + url);
        byte[] bytes = restTemplate.getForObject(url, byte[].class);
        System.out.println("[X]");
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(bytes));
             ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            System.out.println("unpacking");
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                int c;
                while ((c = zipInputStream.read()) != -1) {
                    byteArrayOutputStream.write(c);
                }
            }
            return new String(byteArrayOutputStream.toByteArray());
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
