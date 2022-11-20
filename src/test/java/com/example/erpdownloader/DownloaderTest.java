package com.example.erpdownloader;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class DownloaderTest {

    @Test
    public void cutStringAndSendToRabbitmq() {
        Downloader downloader = new Downloader();
        downloader.cutStringAndSendToRabbitmq("/home/dmiyry/xml/tmp.xml", text -> System.out.print(text.substring(text.indexOf("ERPID"), text.indexOf("ERPID") + 20) + "\r"));
    }
}