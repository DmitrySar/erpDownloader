package com.example.erpdownloader;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.util.Zip4jUtil;
import org.bson.Document;
import org.json.JSONObject;
import org.json.XML;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipInputStream;

@Service
public class Downloader {
    private final static String xmlContent = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
            "<list>\n" +
            "<standardversion>\n" +
            "<item identifier=\"1\" title=\"Проверки на Январь 2021 года\" link=\"https://proverki.gov.ru/blob/opendata/7710146102-inspection-2021-1.xml\" format=\"xml\"/>\n" +
            "<item identifier=\"2\" title=\"Проверки на Октябрь 2021 года\" link=\"https://proverki.gov.ru/blob/opendata/7710146102-inspection-2021-10.xml\" format=\"xml\"/>\n" +
            "<item identifier=\"3\" title=\"Проверки на Ноябрь 2021 года\" link=\"https://proverki.gov.ru/blob/opendata/7710146102-inspection-2021-11.xml\" format=\"xml\"/>\n" +
            "<item identifier=\"4\" title=\"Проверки на Декабрь 2021 года\" link=\"https://proverki.gov.ru/blob/opendata/7710146102-inspection-2021-12.xml\" format=\"xml\"/>\n" +
            "<item identifier=\"5\" title=\"Проверки на Февраль 2021 года\" link=\"https://proverki.gov.ru/blob/opendata/7710146102-inspection-2021-2.xml\" format=\"xml\"/>\n" +
            "<item identifier=\"6\" title=\"Проверки на Март 2021 года\" link=\"https://proverki.gov.ru/blob/opendata/7710146102-inspection-2021-3.xml\" format=\"xml\"/>\n" +
            "<item identifier=\"7\" title=\"Проверки на Апрель 2021 года\" link=\"https://proverki.gov.ru/blob/opendata/7710146102-inspection-2021-4.xml\" format=\"xml\"/>\n" +
            "<item identifier=\"8\" title=\"Проверки на Май 2021 года\" link=\"https://proverki.gov.ru/blob/opendata/7710146102-inspection-2021-5.xml\" format=\"xml\"/>\n" +
            "<item identifier=\"9\" title=\"Проверки на Июнь 2021 года\" link=\"https://proverki.gov.ru/blob/opendata/7710146102-inspection-2021-6.xml\" format=\"xml\"/>\n" +
            "<item identifier=\"10\" title=\"Проверки на Июль 2021 года\" link=\"https://proverki.gov.ru/blob/opendata/7710146102-inspection-2021-7.xml\" format=\"xml\"/>\n" +
            "<item identifier=\"11\" title=\"Проверки на Август 2021 года\" link=\"https://proverki.gov.ru/blob/opendata/7710146102-inspection-2021-8.xml\" format=\"xml\"/>\n" +
            "<item identifier=\"12\" title=\"Проверки на Сентябрь 2021 года\" link=\"https://proverki.gov.ru/blob/opendata/7710146102-inspection-2021-9.xml\" format=\"xml\"/>\n" +
            "<item identifier=\"13\" title=\"Проверки на Январь 2022 года\" link=\"https://proverki.gov.ru/blob/opendata/7710146102-inspection-2022-1.xml\" format=\"xml\"/>\n" +
            "<item identifier=\"14\" title=\"Проверки на Октябрь 2022 года\" link=\"https://proverki.gov.ru/blob/opendata/7710146102-inspection-2022-10.xml\" format=\"xml\"/>\n" +
            "<item identifier=\"15\" title=\"Проверки на Ноябрь 2022 года\" link=\"https://proverki.gov.ru/blob/opendata/7710146102-inspection-2022-11.xml\" format=\"xml\"/>\n" +
            "<item identifier=\"16\" title=\"Проверки на Февраль 2022 года\" link=\"https://proverki.gov.ru/blob/opendata/7710146102-inspection-2022-2.xml\" format=\"xml\"/>\n" +
            "<item identifier=\"17\" title=\"Проверки на Март 2022 года\" link=\"https://proverki.gov.ru/blob/opendata/7710146102-inspection-2022-3.xml\" format=\"xml\"/>\n" +
            "<item identifier=\"18\" title=\"Проверки на Апрель 2022 года\" link=\"https://proverki.gov.ru/blob/opendata/7710146102-inspection-2022-4.xml\" format=\"xml\"/>\n" +
            "<item identifier=\"19\" title=\"Проверки на Май 2022 года\" link=\"https://proverki.gov.ru/blob/opendata/7710146102-inspection-2022-5.xml\" format=\"xml\"/>\n" +
            "<item identifier=\"20\" title=\"Проверки на Июнь 2022 года\" link=\"https://proverki.gov.ru/blob/opendata/7710146102-inspection-2022-6.xml\" format=\"xml\"/>\n" +
            "<item identifier=\"21\" title=\"Проверки на Июль 2022 года\" link=\"https://proverki.gov.ru/blob/opendata/7710146102-inspection-2022-7.xml\" format=\"xml\"/>\n" +
            "<item identifier=\"22\" title=\"Проверки на Август 2022 года\" link=\"https://proverki.gov.ru/blob/opendata/7710146102-inspection-2022-8.xml\" format=\"xml\"/>\n" +
            "<item identifier=\"23\" title=\"Проверки на Сентябрь 2022 года\" link=\"https://proverki.gov.ru/blob/opendata/7710146102-inspection-2022-9.xml\" format=\"xml\"/>\n" +
            "</standardversion>\n" +
            "</list>";

    public static void main(String[] args) {
        JSONObject xmlJSONObj = XML.toJSONObject(xmlContent);
        //4
        String jsonPrettyPrintString = xmlJSONObj.toString();
//        System.out.println(jsonPrettyPrintString);
        Document list = Document.parse(jsonPrettyPrintString);
        List<String> links = (List<String>) list.get("list", Document.class)
                .get("standardversion", Document.class)
                .get("item", List.class).stream()
                .map(d -> ((Document) d).getString("link"))
                .collect(Collectors.toList());
        //links.forEach(System.out::println);
        RestTemplate restTemplate = new RestTemplate();
        //TODO пробежаться по всему списку
        String xmlDoc = restTemplate.getForObject(links.get(0), String.class);

        xmlJSONObj = XML.toJSONObject(xmlDoc);
        System.out.println(xmlJSONObj.toString(4));
        Document listZipFiles = Document.parse(xmlJSONObj.toString());
        List<String> zipFilesNames = (List<String>) listZipFiles.get("meta", Document.class).get("data", Document.class).get("dataversion", List.class).stream()
                .map(d -> ((Document) d).getString("source"))
                .collect(Collectors.toList());
        //TODO пробежаться по всем
        zipFilesNames.forEach(url -> {
            byte[] bytes = restTemplate.getForObject(url, byte[].class);
            ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(bytes));
            try {
                Files.write(Paths.get("c:\\xml\\" + zipInputStream.getNextEntry().getName() + ".zip"), bytes);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
