package com.motus.motusbot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource("application.properties")
public class BotConfig {

    @Value("${bot.name}")
    String motusBotName;

    @Value("${bot.token}")
    String motusBotToken;

    @Value("MotusStationBot")
    String motusStationBotName;

    @Value("8394930178:AAFiAS_CoXIsBH0WH3BPScOXfbalSJzsPCk")
    String motusStationToken;

    public String getMotusBotName() {
        return motusBotName;
    }

    public String getMotusBotToken() {
        return motusBotToken;
    }

    public String getMotusStationBotName() {
        return motusStationBotName;
    }

    public String getMotusStationToken() {
        return motusStationToken;
    }
}
