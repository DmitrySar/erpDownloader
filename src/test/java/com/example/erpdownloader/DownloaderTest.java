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
        downloader.cutStringAndSendToRabbitmq(Downloader.TEMP_XML_FILE_NAME);
    }
}