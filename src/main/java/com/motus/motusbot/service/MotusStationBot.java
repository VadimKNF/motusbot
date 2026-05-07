package com.motus.motusbot.service;

import com.motus.motusbot.config.BotConfig;
import com.motus.motusbot.model.Car;
import com.motus.motusbot.model.Client;
import com.motus.motusbot.model.OrderStatus;
import com.motus.motusbot.model.Service;
import com.motus.motusbot.model.ServiceOrder;
import com.motus.motusbot.model.Station;
import com.motus.motusbot.repository.ServiceOrderRepository;
import com.motus.motusbot.repository.ServiceRepository;
import com.motus.motusbot.repository.StationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
public class MotusStationBot extends TelegramLongPollingBot {

    private static final Logger log = LoggerFactory.getLogger(MotusStationBot.class);

    private final BotConfig botConfig;
    private final StationRepository stationRepository;
    private final ServiceRepository serviceRepository;
    private final ServiceOrderRepository serviceOrderRepository;
    private final MotusBot motusBot;

    public static final String START = "/start";
    public static final String EDIT_PROFILE = "/editprofile";
    public static final String MY_PROFILE = "/myprofile";

    private static final String CALLBACK_SERVICE_PREFIX = "st_svc_";
    private static final String CALLBACK_SERVICE_DONE = "st_svc_done";
    private static final String CALLBACK_PROFILE_VIEW = "st_prof_view";
    private static final String CALLBACK_MAIN_EDIT = "st_main_edit";
    private static final String CALLBACK_PROFILE_NAME = "st_prof_name";
    private static final String CALLBACK_PROFILE_ADDRESS = "st_prof_addr";
    private static final String CALLBACK_PROFILE_CONTACT = "st_prof_contact";
    private static final String CALLBACK_PROFILE_SERVICES = "st_prof_services";
    private static final String CALLBACK_AVAILABLE_ORDERS = "st_avail_orders";
    private static final String CALLBACK_ACCEPT_ORDER_PREFIX = "accept_order_";
    private static final String CALLBACK_SET_ORDER_TIME_PREFIX = "set_order_time_";
    private static final DateTimeFormatter ORDER_TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private enum RegistrationState {
        NONE,
        WAITING_FOR_NAME,
        WAITING_FOR_ADDRESS,
        WAITING_FOR_CONTACT,
        WAITING_FOR_SERVICES,
        EDIT_NAME,
        EDIT_ADDRESS,
        EDIT_CONTACT,
        WAITING_FOR_ORDER_TIME
    }

    private final Map<Long, RegistrationState> userStates = new HashMap<>();
    private final Map<Long, Station> pendingStations = new HashMap<>();
    private final Map<Long, Set<UUID>> pendingServiceIds = new HashMap<>();
    private final Map<Long, UUID> pendingOrderIdForTime = new HashMap<>();

    public MotusStationBot(BotConfig botConfig, StationRepository stationRepository,
                           ServiceRepository serviceRepository, ServiceOrderRepository serviceOrderRepository,
                           MotusBot motusBot) {
        this.botConfig = botConfig;
        this.stationRepository = stationRepository;
        this.serviceRepository = serviceRepository;
        this.serviceOrderRepository = serviceOrderRepository;
        this.motusBot = motusBot;
        List<BotCommand> listofCommands = new ArrayList<>();
        listofCommands.add(new BotCommand("/start", "Начать работу"));
        listofCommands.add(new BotCommand("/myprofile", "Просмотр профиля"));
        listofCommands.add(new BotCommand("/editprofile", "Редактировать профиль"));
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
        if (update.hasMessage()) {
            Message message = update.getMessage();
            if (message == null || message.getFrom() == null) {
                return;
            }
            
            Long chatIdLong = message.getChatId();
            if (chatIdLong == null) {
                return;
            }
            long chatId = chatIdLong;
            
            Long telegramId = message.getFrom().getId();
            String text = message.getText();

            if (text != null && START.equals(text)) {
                handleStartCommand(chatId, telegramId);
            } else if (text != null && MY_PROFILE.equals(text)) {
                handleProfileView(chatId, telegramId);
            } else if (text != null && EDIT_PROFILE.equals(text)) {
                handleProfileCommand(chatId, telegramId);
            } else if (text != null) {
                handleUserInput(chatId, telegramId, text);
            }
        }
        if (update.hasCallbackQuery()) {
            String callbackData = update.getCallbackQuery().getData();
            long messageId = update.getCallbackQuery().getMessage().getMessageId();
            long chatId = update.getCallbackQuery().getMessage().getChatId();
            Long telegramId = update.getCallbackQuery().getFrom().getId();
            if (callbackData != null && CALLBACK_PROFILE_VIEW.equals(callbackData)) {
                handleProfileView(chatId, telegramId);
            } else if (callbackData != null && CALLBACK_MAIN_EDIT.equals(callbackData)) {
                sendProfileMenu(chatId);
            } else if (callbackData != null && CALLBACK_PROFILE_NAME.equals(callbackData)) {
                handleProfileEditName(chatId, telegramId);
            } else if (callbackData != null && CALLBACK_PROFILE_ADDRESS.equals(callbackData)) {
                handleProfileEditAddress(chatId, telegramId);
            } else if (callbackData != null && CALLBACK_PROFILE_CONTACT.equals(callbackData)) {
                handleProfileEditContact(chatId, telegramId);
            } else if (callbackData != null && CALLBACK_PROFILE_SERVICES.equals(callbackData)) {
                handleProfileEditServices(chatId, telegramId);
            } else if (callbackData != null && CALLBACK_AVAILABLE_ORDERS.equals(callbackData)) {
                handleAvailableOrders(chatId, telegramId);
            } else if (callbackData != null && callbackData.startsWith(CALLBACK_SERVICE_PREFIX)) {
                handleServiceSelectionCallback(chatId, messageId, telegramId, callbackData);
            } else if (callbackData != null && callbackData.startsWith(CALLBACK_ACCEPT_ORDER_PREFIX)) {
                handleAcceptOrder(chatId, telegramId, callbackData);
            } else if (callbackData != null && callbackData.startsWith(CALLBACK_SET_ORDER_TIME_PREFIX)) {
                handleSetOrderTimeClick(chatId, telegramId, callbackData);
            }
        }
    }

    private void handleStartCommand(long chatId, Long telegramId) {
        Optional<Station> existingStation = stationRepository.findByTelegramId(telegramId);
        
        if (existingStation.isPresent()) {
            sendMainMenu(chatId);
        } else {
            // Начинаем процесс регистрации
            Station newStation = new Station();
            newStation.setTelegramId(telegramId);
            pendingStations.put(telegramId, newStation);
            userStates.put(telegramId, RegistrationState.WAITING_FOR_NAME);
            sendMessage(chatId, "Добро пожаловать! Для регистрации нам нужна информация о вашей станции.\n\nВведите название организации:");
        }
    }

    private void handleProfileCommand(long chatId, Long telegramId) {
        Optional<Station> existingStation = stationRepository.findByTelegramId(telegramId);
        if (existingStation.isEmpty()) {
            sendMessage(chatId, "Сначала зарегистрируйтесь с помощью команды /start");
            return;
        }
        sendProfileMenu(chatId);
    }

    private void sendMainMenu(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("Добро пожаловать! Выберите действие:");
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(createButton(CALLBACK_PROFILE_VIEW, "Просмотр профиля")));
        rows.add(List.of(createButton(CALLBACK_AVAILABLE_ORDERS, "Просмотр доступных заявок")));
        rows.add(List.of(createButton(CALLBACK_MAIN_EDIT, "Редактировать профиль")));
        markup.setKeyboard(rows);
        message.setReplyMarkup(markup);
        executeMessage(message);
    }

    private void sendProfileMenu(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("Редактирование профиля. Выберите, что изменить:");
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(createButton(CALLBACK_PROFILE_NAME, "Изменить название организации")));
        rows.add(List.of(createButton(CALLBACK_PROFILE_ADDRESS, "Изменить адрес")));
        rows.add(List.of(createButton(CALLBACK_PROFILE_CONTACT, "Изменить контактные данные")));
        rows.add(List.of(createButton(CALLBACK_PROFILE_SERVICES, "Изменить набор услуг")));
        markup.setKeyboard(rows);
        message.setReplyMarkup(markup);
        executeMessage(message);
    }

    private void handleProfileView(long chatId, Long telegramId) {
        Optional<Station> opt = stationRepository.findByTelegramIdWithServices(telegramId);
        if (opt.isEmpty()) {
            sendMessage(chatId, "Станция не найдена. Используйте /start для регистрации.");
            return;
        }
        Station station = opt.get();
        StringBuilder sb = new StringBuilder("📋 Профиль станции\n\n");
        sb.append("🏢 Название: ").append(station.getName() != null ? station.getName() : "—").append("\n");
        sb.append("📍 Адрес: ").append(station.getAddress() != null ? station.getAddress() : "—").append("\n");
        sb.append("📞 Контакты: ").append(station.getContact() != null ? station.getContact() : "—").append("\n\n");
        if (station.getServices() == null || station.getServices().isEmpty()) {
            sb.append("🔧 Услуги: не выбраны");
        } else {
            sb.append("🔧 Услуги:\n");
            int i = 1;
            for (Service s : station.getServices()) {
                sb.append(i++).append(". ").append(s.getName() != null ? s.getName() : "—").append("\n");
            }
        }
        sendMessage(chatId, sb.toString());
    }

    private void handleAvailableOrders(long chatId, Long telegramId) {
        Optional<Station> opt = stationRepository.findByTelegramIdWithServices(telegramId);
        if (opt.isEmpty()) {
            sendMessage(chatId, "Станция не найдена. Используйте /start для регистрации.");
            return;
        }
        Station station = opt.get();
        if (station.getServices() == null || station.getServices().isEmpty()) {
            sendMessage(chatId, "В профиле не выбраны услуги. Добавьте услуги в разделе «Редактировать профиль».");
            return;
        }
        List<UUID> serviceIds = station.getServices().stream()
                .map(Service::getId)
                .toList();
        List<ServiceOrder> orders = serviceOrderRepository.findByStatusAndServiceIdIn(OrderStatus.AVAILABLE, serviceIds);
        if (orders.isEmpty()) {
            sendMessage(chatId, "Нет доступных заявок по вашим услугам.");
            return;
        }
        final int maxMessageLength = 4000;
        StringBuilder block = new StringBuilder();
        block.append("📋 Доступные заявки (").append(orders.size()).append("):\n\n");
        for (int i = 0; i < orders.size(); i++) {
            ServiceOrder o = orders.get(i);
            String serviceName = o.getService() != null ? o.getService().getName() : "—";
            String clientName = o.getClient() != null ? o.getClient().getName() : "—";
            String clientPhone = o.getClient() != null ? o.getClient().getPhoneNumber() : "—";
            String carInfo = "—";
            if (o.getCar() != null) {
                Car c = o.getCar();
                carInfo = String.format("%s %s, %d г.", c.getMake(), c.getModel(), c.getYear());
            }
            String workDesc = o.getWorkDescription() != null ? o.getWorkDescription() : "—";
            String orderBlock = "——— Заявка " + (i + 1) + " ———\n" +
                    "🔧 Услуга: " + serviceName + "\n" +
                    "👤 Клиент: " + clientName + "\n" +
                    "📞 Телефон: " + clientPhone + "\n" +
                    "🚗 Автомобиль: " + carInfo + "\n" +
                    "📝 Описание: " + workDesc + "\n\n";
            if (block.length() + orderBlock.length() > maxMessageLength) {
                sendMessage(chatId, block.toString());
                block.setLength(0);
                block.append(orderBlock);
            } else {
                block.append(orderBlock);
            }
        }
        if (block.length() > 0) {
            sendMessage(chatId, block.toString());
        }
    }

    private InlineKeyboardButton createButton(String callbackData, String text) {
        InlineKeyboardButton btn = new InlineKeyboardButton();
        btn.setCallbackData(callbackData);
        btn.setText(text);
        return btn;
    }

    private void handleProfileEditName(long chatId, Long telegramId) {
        Optional<Station> opt = stationRepository.findByTelegramId(telegramId);
        if (opt.isEmpty()) {
            sendMessage(chatId, "Станция не найдена. Используйте /start");
            return;
        }
        Station station = opt.get();
        pendingStations.put(telegramId, station);
        userStates.put(telegramId, RegistrationState.EDIT_NAME);
        String current = station.getName() != null ? station.getName() : "не указано";
        sendMessage(chatId, "Введите новое название организации (текущее: " + current + "):");
    }

    private void handleProfileEditAddress(long chatId, Long telegramId) {
        Optional<Station> opt = stationRepository.findByTelegramId(telegramId);
        if (opt.isEmpty()) {
            sendMessage(chatId, "Станция не найдена. Используйте /start");
            return;
        }
        Station station = opt.get();
        pendingStations.put(telegramId, station);
        userStates.put(telegramId, RegistrationState.EDIT_ADDRESS);
        String current = station.getAddress() != null ? station.getAddress() : "не указан";
        sendMessage(chatId, "Введите новый адрес станции (текущий: " + current + "):");
    }

    private void handleProfileEditContact(long chatId, Long telegramId) {
        Optional<Station> opt = stationRepository.findByTelegramId(telegramId);
        if (opt.isEmpty()) {
            sendMessage(chatId, "Станция не найдена. Используйте /start");
            return;
        }
        Station station = opt.get();
        pendingStations.put(telegramId, station);
        userStates.put(telegramId, RegistrationState.EDIT_CONTACT);
        String current = station.getContact() != null ? station.getContact() : "не указаны";
        sendMessage(chatId, "Введите контактные данные (текущие: " + current + "):");
    }

    private void handleProfileEditServices(long chatId, Long telegramId) {
        Optional<Station> opt = stationRepository.findByTelegramIdWithServices(telegramId);
        if (opt.isEmpty()) {
            sendMessage(chatId, "Станция не найдена. Используйте /start");
            return;
        }
        Station station = opt.get();
        pendingStations.put(telegramId, station);
        userStates.put(telegramId, RegistrationState.WAITING_FOR_SERVICES);
        Set<UUID> selectedIds = new HashSet<>();
        if (station.getServices() != null) {
            station.getServices().forEach(s -> selectedIds.add(s.getId()));
        }
        pendingServiceIds.put(telegramId, selectedIds);
        sendServiceSelectionMessage(chatId, telegramId);
    }

    private void handleUserInput(long chatId, Long telegramId, String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        
        RegistrationState state = userStates.getOrDefault(telegramId, RegistrationState.NONE);

        switch (state) {
            case EDIT_ADDRESS:
                Station stationEdit = pendingStations.get(telegramId);
                if (stationEdit != null) {
                    stationEdit.setAddress(text.trim());
                    try {
                        stationRepository.save(stationEdit);
                        sendMessage(chatId, "Адрес обновлён.");
                        userStates.remove(telegramId);
                        pendingStations.remove(telegramId);
                    } catch (Exception e) {
                        log.error("Ошибка при сохранении адреса: {}", e.getMessage(), e);
                        sendMessage(chatId, "Ошибка при сохранении. Попробуйте позже.");
                    }
                }
                break;

            case EDIT_CONTACT:
                Station stationContact = pendingStations.get(telegramId);
                if (stationContact != null) {
                    stationContact.setContact(text.trim());
                    try {
                        stationRepository.save(stationContact);
                        sendMessage(chatId, "Контактные данные обновлены.");
                        userStates.remove(telegramId);
                        pendingStations.remove(telegramId);
                    } catch (Exception e) {
                        log.error("Ошибка при сохранении контактов: {}", e.getMessage(), e);
                        sendMessage(chatId, "Ошибка при сохранении. Попробуйте позже.");
                    }
                }
                break;

            case WAITING_FOR_NAME: {
                Station stationName = pendingStations.get(telegramId);
                if (stationName != null) {
                    stationName.setName(text.trim());
                    userStates.put(telegramId, RegistrationState.WAITING_FOR_ADDRESS);
                    sendMessage(chatId, "Введите адрес станции:");
                }
                break;
            }

            case WAITING_FOR_ADDRESS: {
                Station station = pendingStations.get(telegramId);
                if (station != null) {
                    station.setAddress(text.trim());
                    userStates.put(telegramId, RegistrationState.WAITING_FOR_CONTACT);
                    String currentContact = station.getContact() != null ? station.getContact() : "не указаны";
                    sendMessage(chatId, "Теперь введите контактные данные (текущие: " + currentContact + "):");
                }
                break;
            }

            case WAITING_FOR_CONTACT: {
                Station station = pendingStations.get(telegramId);
                if (station != null) {
                    station.setContact(text.trim());
                    userStates.put(telegramId, RegistrationState.WAITING_FOR_SERVICES);
                    Set<UUID> selectedIds = new HashSet<>();
                    if (station.getServices() != null) {
                        station.getServices().forEach(s -> selectedIds.add(s.getId()));
                    }
                    pendingServiceIds.put(telegramId, selectedIds);
                    sendServiceSelectionMessage(chatId, telegramId);
                }
                break;
            }

            case EDIT_NAME: {
                Station station = pendingStations.get(telegramId);
                if (station != null) {
                    station.setName(text.trim());
                    try {
                        stationRepository.save(station);
                        sendMessage(chatId, "Название организации обновлено.");
                        userStates.remove(telegramId);
                        pendingStations.remove(telegramId);
                    } catch (Exception e) {
                        log.error("Ошибка при сохранении названия: {}", e.getMessage(), e);
                        sendMessage(chatId, "Ошибка при сохранении. Попробуйте позже.");
                    }
                }
                break;
            }

            case WAITING_FOR_ORDER_TIME: {
                UUID orderId = pendingOrderIdForTime.remove(telegramId);
                userStates.remove(telegramId);
                if (orderId == null) {
                    sendMessage(chatId, "Сессия истекла.");
                    return;
                }
                try {
                    LocalDateTime dateTime = LocalDateTime.parse(text.trim(), ORDER_TIME_FORMAT);
                    Optional<ServiceOrder> orderOpt = serviceOrderRepository.findById(orderId);
                    if (orderOpt.isEmpty()) {
                        sendMessage(chatId, "Заявка не найдена.");
                        return;
                    }
                    ServiceOrder order = orderOpt.get();
                    order.setServiceTime(dateTime);
                    order.setStatus(OrderStatus.IN_PROGRESS);
                    serviceOrderRepository.save(order);
                    sendMessage(chatId, "Дата и время проведения работ сохранены.");
                    Optional<ServiceOrder> orderWithRefs = serviceOrderRepository.findByIdWithStationAndClient(orderId);
                    orderWithRefs.ifPresent(o -> notifyClientAboutAppointment(o, dateTime));
                } catch (DateTimeParseException e) {
                    pendingOrderIdForTime.put(telegramId, orderId);
                    userStates.put(telegramId, RegistrationState.WAITING_FOR_ORDER_TIME);
                    sendMessage(chatId, "Неверный формат. Введите дату и время в формате ДД.ММ.ГГГГ ЧЧ:ММ (например: 25.03.2025 14:30)");
                } catch (Exception e) {
                    log.error("Ошибка при сохранении даты заявки: {}", e.getMessage(), e);
                    sendMessage(chatId, "Ошибка при сохранении. Попробуйте позже.");
                }
                break;
            }

            default:
                sendMessage(chatId, "Используйте команду /start для начала работы.");
                break;
        }
    }

    private void sendMessage(long chatId, String textToSend) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(String.valueOf(chatId));
        sendMessage.setText(textToSend);
        executeMessage(sendMessage);
    }

    private void executeMessage(SendMessage message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Ошибка при отправке сообщения: {}", e.getMessage());
        }
    }

    /**
     * Отправляет исполнителю (станции) сообщение о заявке с кнопкой «Принять заявку».
     */
    public void sendOrderToStation(long stationTelegramId, UUID orderId, String orderDetailsText) {
        if (orderDetailsText == null) orderDetailsText = "";
        String text = "Новая заявка на ремонт\n\n" + orderDetailsText;
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        InlineKeyboardButton acceptBtn = new InlineKeyboardButton();
        acceptBtn.setText("Принять заявку");
        acceptBtn.setCallbackData(CALLBACK_ACCEPT_ORDER_PREFIX + orderId);
        markup.setKeyboard(List.of(List.of(acceptBtn)));
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(stationTelegramId));
        message.setText(text);
        message.setReplyMarkup(markup);
        executeMessage(message);
    }

    private void handleAcceptOrder(long chatId, Long telegramId, String callbackData) {
        String orderIdStr = callbackData.substring(CALLBACK_ACCEPT_ORDER_PREFIX.length());
        UUID orderId;
        try {
            orderId = UUID.fromString(orderIdStr);
        } catch (IllegalArgumentException e) {
            sendMessage(chatId, "Ошибка данных заявки.");
            return;
        }
        Optional<Station> stationOpt = stationRepository.findByTelegramId(telegramId);
        if (stationOpt.isEmpty()) {
            sendMessage(chatId, "Станция не найдена. Используйте /start.");
            return;
        }
        Optional<ServiceOrder> orderOpt = serviceOrderRepository.findByIdWithServiceAndClientAndCar(orderId);
        if (orderOpt.isEmpty()) {
            sendMessage(chatId, "Заявка не найдена.");
            return;
        }
        ServiceOrder order = orderOpt.get();
        Station station = stationOpt.get();
        order.setStation(station);
        order.setStatus(OrderStatus.PAUSED);
        try {
            serviceOrderRepository.save(order);
        } catch (Exception e) {
            log.error("Ошибка при принятии заявки: {}", e.getMessage(), e);
            sendMessage(chatId, "Ошибка при сохранении. Попробуйте позже.");
            return;
        }
        String clientPhone = "";
        Client client = order.getClient();
        if (client != null && client.getPhoneNumber() != null) {
            clientPhone = client.getPhoneNumber().trim();
        }
        String contactMessage = "Свяжитесь с клиентом и согласуйте дату и время проведения работ\n\n📞 Телефон клиента: " + (clientPhone.isEmpty() ? "—" : clientPhone);
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(contactMessage);
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        InlineKeyboardButton setTimeBtn = new InlineKeyboardButton();
        setTimeBtn.setText("Указать согласованную дату");
        setTimeBtn.setCallbackData(CALLBACK_SET_ORDER_TIME_PREFIX + orderId);
        markup.setKeyboard(List.of(List.of(setTimeBtn)));
        message.setReplyMarkup(markup);
        executeMessage(message);
    }

    private void notifyClientAboutAppointment(ServiceOrder order, LocalDateTime dateTime) {
        Client client = order.getClient();
        if (client == null || client.getTelegramId() == null) return;
        Station station = order.getStation();
        String stationName = station != null && station.getName() != null ? station.getName() : "—";
        String stationAddress = station != null && station.getAddress() != null ? station.getAddress() : "—";
        String dateTimeStr = dateTime.format(ORDER_TIME_FORMAT);
        String message = "Ваша запись подтверждена.\n\n" +
                "📅 Дата и время: " + dateTimeStr + "\n" +
                "🏢 Станция: " + stationName + "\n" +
                "📍 Адрес: " + stationAddress;
        motusBot.sendMessageToClient(client.getTelegramId(), message);
    }

    private void handleSetOrderTimeClick(long chatId, Long telegramId, String callbackData) {
        String orderIdStr = callbackData.substring(CALLBACK_SET_ORDER_TIME_PREFIX.length());
        UUID orderId;
        try {
            orderId = UUID.fromString(orderIdStr);
        } catch (IllegalArgumentException e) {
            sendMessage(chatId, "Ошибка данных заявки.");
            return;
        }
        Optional<ServiceOrder> orderOpt = serviceOrderRepository.findById(orderId);
        if (orderOpt.isEmpty()) {
            sendMessage(chatId, "Заявка не найдена.");
            return;
        }
        pendingOrderIdForTime.put(telegramId, orderId);
        userStates.put(telegramId, RegistrationState.WAITING_FOR_ORDER_TIME);
        sendMessage(chatId, "Введите согласованную дату и время в формате ДД.ММ.ГГГГ ЧЧ:ММ (например: 05.03.2026 14:30)");
    }

    private void sendServiceSelectionMessage(long chatId, Long telegramId) {
        List<Service> services = serviceRepository.findAllByOrderByNameAsc();
        String text = services.isEmpty()
                ? "Список услуг пока пуст. Нажмите «Готово» для завершения."
                : "Выберите услуги, которые предоставляет ваша станция (нажмите для выбора/снятия). Затем нажмите «Готово»:";
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        message.setReplyMarkup(buildServiceKeyboard(telegramId));
        executeMessage(message);
    }

    private void handleServiceSelectionCallback(long chatId, long messageId, Long telegramId, String callbackData) {
        if (CALLBACK_SERVICE_DONE.equals(callbackData)) {
            Station station = pendingStations.get(telegramId);
            Set<UUID> selectedIds = pendingServiceIds.get(telegramId);
            if (station == null) {
                sendMessage(chatId, "Сессия истекла. Начните с /start или /profile.");
                return;
            }
            try {
                if (selectedIds != null && !selectedIds.isEmpty()) {
                    List<Service> selectedServices = serviceRepository.findAllById(selectedIds);
                    station.getServices().clear();
                    station.getServices().addAll(selectedServices);
                } else {
                    station.getServices().clear();
                }
                stationRepository.save(station);
                String reply = station.getId() != null ? "Профиль успешно обновлён." : "Спасибо! Ваша станция успешно зарегистрирована.";
                editMessageText(chatId, messageId, reply);
                userStates.remove(telegramId);
                pendingStations.remove(telegramId);
                pendingServiceIds.remove(telegramId);
            } catch (Exception e) {
                log.error("Ошибка при сохранении станции: {}", e.getMessage(), e);
                sendMessage(chatId, "Произошла ошибка при сохранении. Пожалуйста, попробуйте позже.");
            }
            return;
        }
        String serviceIdStr = callbackData.substring(CALLBACK_SERVICE_PREFIX.length());
        try {
            UUID serviceId = UUID.fromString(serviceIdStr);
            Set<UUID> ids = pendingServiceIds.computeIfAbsent(telegramId, k -> new HashSet<>());
            if (ids.contains(serviceId)) {
                ids.remove(serviceId);
            } else {
                ids.add(serviceId);
            }
            updateServiceSelectionMessage(chatId, messageId, telegramId);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private InlineKeyboardMarkup buildServiceKeyboard(Long telegramId) {
        List<Service> services = serviceRepository.findAllByOrderByNameAsc();
        Set<UUID> selectedIds = pendingServiceIds.getOrDefault(telegramId, new HashSet<>());
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (Service s : services) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            InlineKeyboardButton btn = new InlineKeyboardButton();
            boolean selected = selectedIds.contains(s.getId());
            btn.setText((selected ? "✓ " : "") + s.getName());
            btn.setCallbackData(CALLBACK_SERVICE_PREFIX + s.getId().toString());
            row.add(btn);
            rows.add(row);
        }
        List<InlineKeyboardButton> rowDone = new ArrayList<>();
        InlineKeyboardButton doneBtn = new InlineKeyboardButton();
        doneBtn.setText("Готово");
        doneBtn.setCallbackData(CALLBACK_SERVICE_DONE);
        rowDone.add(doneBtn);
        rows.add(rowDone);
        markup.setKeyboard(rows);
        return markup;
    }

    private void updateServiceSelectionMessage(long chatId, long messageId, Long telegramId) {
        List<Service> services = serviceRepository.findAllByOrderByNameAsc();
        String text = services.isEmpty()
                ? "Список услуг пока пуст. Нажмите «Готово» для завершения."
                : "Выберите услуги, которые предоставляет ваша станция (нажмите для выбора/снятия). Затем нажмите «Готово»:";
        EditMessageText edit = new EditMessageText();
        edit.setChatId(String.valueOf(chatId));
        edit.setMessageId((int) messageId);
        edit.setText(text);
        edit.setReplyMarkup(buildServiceKeyboard(telegramId));
        try {
            execute(edit);
        } catch (TelegramApiException e) {
            log.error("Ошибка при обновлении сообщения: {}", e.getMessage());
        }
    }

    private void editMessageText(long chatId, long messageId, String text) {
        EditMessageText edit = new EditMessageText();
        edit.setChatId(String.valueOf(chatId));
        edit.setMessageId((int) messageId);
        edit.setText(text);
        try {
            execute(edit);
        } catch (TelegramApiException e) {
            log.error("Ошибка при редактировании сообщения: {}", e.getMessage());
        }
    }
}
