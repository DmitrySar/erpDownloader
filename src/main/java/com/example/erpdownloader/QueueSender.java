package com.example.erpdownloader;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Properties;


@Component
public class QueueSender {
    @Value("${q.erp.import.name}")
    private String queueName;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private AmqpAdmin admin;

    public int send(String message) {
        rabbitTemplate.convertAndSend(queueName, message);
        Properties properties = admin.getQueueProperties(queueName);
        return (int) properties.get("QUEUE_CONSUMER_COUNT");
    }

}
