package com.motus.motusbot.service;

import com.motus.motusbot.config.BotConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;

public class MotusStationBot extends TelegramLongPollingBot {

    private static final Logger log = LoggerFactory.getLogger(MotusBot.class);

    final BotConfig botConfig;

    public static final String START = "/start";

    public static final String HELLO = "hello";

    public MotusStationBot(BotConfig botConfig) {
        this.botConfig = botConfig;
        List<BotCommand> listofCommands = new ArrayList<>();
        listofCommands.add(new BotCommand("/start", HELLO));
        try {
            this.execute(new SetMyCommands(listofCommands, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            log.error(e.getMessage());
        }
    }

    @Override
    public String getBotUsername() {
        return botConfig.getMotusStationBotName();
    }

    @Override
    public String getBotToken() {
        return botConfig.getMotusStationToken();
    }

    @Override
    public void onUpdateReceived(Update update) {
    }
}
