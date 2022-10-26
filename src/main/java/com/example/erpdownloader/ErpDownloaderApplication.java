package com.example.erpdownloader;

import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import javax.annotation.PostConstruct;

@EnableRabbit
@SpringBootApplication
public class ErpDownloaderApplication {

    @Autowired
    private Downloader downloader;
    @Autowired
    private QueueSender sender;

    public static void main(String[] args) {
        SpringApplication.run(ErpDownloaderApplication.class, args);
    }

    @Bean
    public void startProcess() {
        System.out.println("\n==========================\nПоехали\n==========================\n");
        downloader.download(0, 1);
        //downloader.cutStringAndSendToRabbitmq(Downloader.TEMP_XML_FILE_NAME);
    }


}
