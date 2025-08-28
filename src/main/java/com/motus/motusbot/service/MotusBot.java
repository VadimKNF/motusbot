package com.motus.motusbot.service;

import com.motus.motusbot.config.BotConfig;
import com.motus.motusbot.model.ButtonCallBack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;

import static com.motus.motusbot.model.ButtonCallBack.*;

@Component
public class MotusBot extends TelegramLongPollingBot {

    private static final Logger log = LoggerFactory.getLogger(MotusBot.class);

    final BotConfig botConfig;

    private static final String HELLO = "Здравствуйте я Ваш помощник (MotusBot) \n" +
            "\n" +
            "Помогу подобрать автозапчасти и организовать  ремонт Вашего автомобиля\n" +
            "-Грамотный подбор запчастей с бесплатной доставкой \n" +
            " -Ремонт и техническое обслуживание автомобиля любой сложности \n" +
            "-Автоателье, тюнинг, автозвук, автомойки и другие услуги";

    public static final String START = "/start";

    public MotusBot(BotConfig botConfig) {
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
        return botConfig.getBotName();
    }

    @Override
    public String getBotToken() {
        return botConfig.getToken() ;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage()) {
            Message message = update.getMessage();
            if (START.equals(message.getText())) {
                menuMessage(message.getChatId());
            } else {
                sendMessage(message.getChatId(), "пошел нахуй, не пиши сюда, долбоеб");
            }
        }
        if (update.hasCallbackQuery()) {
            String callbackData = update.getCallbackQuery().getData();
            long messageId = update.getCallbackQuery().getMessage().getMessageId();
            long chatId = update.getCallbackQuery().getMessage().getChatId();

            if (callbackData.equals(REPAIR_BUTTON.getId())) {
                String text = "REPAIR INFORMATION";
                executeEditMessageText(text, chatId, messageId);
            } else if (callbackData.equals(PARTS_BUTTON.getId())) {
                String text = "PARTS ORDER INFORMATION";
                executeEditMessageText(text, chatId, messageId);
            } else if (callbackData.equals(OPERATOR_BUTTON.getId())) {
                String text = "OPERATOR CALL INFORMATION";
                executeEditMessageText(text, chatId, messageId);
            } else if (callbackData.equals(BACK_BUTTON.getId())) {
                menuMessage(chatId);
            }
        }
    }

    private void sendMessage(long chatId, String textToSend) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(String.valueOf(chatId));
        sendMessage.setText(textToSend);
        executeMessage(sendMessage);
    }

    private void executeMessage(SendMessage message){
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error(e.getMessage());
        }
    }

    private void executeEditMessageText(String text, long chatId, long messageId){
        EditMessageText message = new EditMessageText();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        message.setMessageId((int) messageId);
        InlineKeyboardMarkup markupInLine = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInLine = new ArrayList<>();
        List<InlineKeyboardButton> rowBack = new ArrayList<>();
        InlineKeyboardButton backButton = createButton(BACK_BUTTON.getId(), "⬅️Назад");
        rowBack.add(backButton);
        rowsInLine.add(rowBack);
        markupInLine.setKeyboard(rowsInLine);
        message.setReplyMarkup(markupInLine);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error(e.getMessage());
        }
    }

    private InlineKeyboardButton createButton(String callBackData, String text) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setCallbackData(callBackData);
        button.setText(text);
        return button;
    }

    private void menuMessage(long chatId) {

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(HELLO);

        InlineKeyboardMarkup markupInLine = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInLine = new ArrayList<>();
        List<InlineKeyboardButton> rowRepair = new ArrayList<>();
        List<InlineKeyboardButton> rowParts = new ArrayList<>();
        List<InlineKeyboardButton> rowOperator = new ArrayList<>();

        InlineKeyboardButton repairButton = createButton(REPAIR_BUTTON.getId(), REPAIR_BUTTON.getLabel());
        InlineKeyboardButton partsButton = createButton(PARTS_BUTTON.getId(), PARTS_BUTTON.getLabel());
        InlineKeyboardButton operatorButton = createButton(OPERATOR_BUTTON.getId(), OPERATOR_BUTTON.getLabel());

        rowRepair.add(repairButton);
        rowParts.add(partsButton);
        rowOperator.add(operatorButton);
        rowsInLine.add(rowRepair);
        rowsInLine.add(rowParts);
        rowsInLine.add(rowOperator);

        markupInLine.setKeyboard(rowsInLine);
        message.setReplyMarkup(markupInLine);

        executeMessage(message);
    }
}
