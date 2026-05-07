package com.motus.motusbot.service;

import com.motus.motusbot.config.BotConfig;
import com.motus.motusbot.model.Car;
import com.motus.motusbot.model.Client;
import com.motus.motusbot.model.OrderStatus;
import com.motus.motusbot.model.Service;
import com.motus.motusbot.model.ServiceOrder;
import com.motus.motusbot.model.Station;
import com.motus.motusbot.repository.CarRepository;
import com.motus.motusbot.repository.ClientRepository;
import com.motus.motusbot.repository.ServiceOrderRepository;
import com.motus.motusbot.repository.ServiceRepository;
import com.motus.motusbot.repository.StationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static com.motus.motusbot.model.ButtonCallBack.*;

@Component
public class MotusBot extends TelegramLongPollingBot {

    private static final Logger log = LoggerFactory.getLogger(MotusBot.class);

    private final BotConfig botConfig;
    private final ClientRepository clientRepository;
    private final CarRepository carRepository;
    private final ServiceRepository serviceRepository;
    private final ServiceOrderRepository serviceOrderRepository;
    private final StationRepository stationRepository;
    private final MotusStationBot motusStationBot;

    private static final String CALLBACK_PROFILE_PHONE = "prof_phone";
    private static final String CALLBACK_PROFILE_EDIT_CAR = "prof_edit_car";
    private static final String CALLBACK_PROFILE_ADD_CAR = "prof_add_car";

    private enum RegistrationState {
        NONE,
        WAITING_FOR_NAME,
        WAITING_FOR_PHONE,
        WAITING_FOR_CAR_MAKE,
        WAITING_FOR_CAR_MODEL,
        WAITING_FOR_CAR_YEAR,
        EDIT_PHONE,
        WAITING_FOR_WORK_DESCRIPTION,
        WAITING_FOR_VIN
    }

    private static final String SERVICE_PARTS_SELECTION = "Подбор запчастей";

    private final Map<Long, RegistrationState> userStates = new HashMap<>();
    private final Map<Long, Client> pendingClients = new HashMap<>();
    private final Map<Long, Car> pendingCars = new HashMap<>();
    private final Map<Long, UUID> pendingRepairServiceId = new HashMap<>();
    private final Map<Long, UUID> pendingRepairCarId = new HashMap<>();
    private final Set<Long> partsFlowTelegramIds = new HashSet<>();
    private final Map<Long, UUID> pendingServiceOrderId = new HashMap<>();
    private final Map<Long, UUID> pendingOrderIdForStationSelection = new HashMap<>();

    private static final String CALLBACK_REPAIR_CAR_PREFIX = "rep_car_";
    private static final String CALLBACK_REPAIR_BACK = "rep_back";

    private static final String HELLO = "Здравствуйте я Ваш помощник (MotusBot) \n" +
            "\n" +
            "Помогу подобрать автозапчасти и организовать  ремонт Вашего автомобиля\n" +
            "-Грамотный подбор запчастей с бесплатной доставкой \n" +
            " -Ремонт и техническое обслуживание автомобиля любой сложности \n" +
            "-Автоателье, тюнинг, автозвук, автомойки и другие услуги";

    public static final String START = "/start";
    public static final String EDIT_PROFILE = "/editprofile";
    public static final String MY_PROFILE = "/myprofile";

    private static final String CALLBACK_SERVICE_PREFIX = "svc_";
    private static final String CALLBACK_AVAIL_STATION_PREFIX = "avail_st_";

    public MotusBot(BotConfig botConfig, ClientRepository clientRepository, CarRepository carRepository,
                    ServiceRepository serviceRepository, ServiceOrderRepository serviceOrderRepository,
                    StationRepository stationRepository, @Lazy MotusStationBot motusStationBot) {
        this.botConfig = botConfig;
        this.clientRepository = clientRepository;
        this.carRepository = carRepository;
        this.serviceRepository = serviceRepository;
        this.serviceOrderRepository = serviceOrderRepository;
        this.stationRepository = stationRepository;
        this.motusStationBot = motusStationBot;
        List<BotCommand> listofCommands = new ArrayList<>();
        listofCommands.add(new BotCommand("/start", "Начать работу"));
        listofCommands.add(new BotCommand("/myprofile", "Мой профиль"));
        listofCommands.add(new BotCommand("/editprofile", "Редактировать профиль"));
        try {
            this.execute(new SetMyCommands(listofCommands, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            log.error(e.getMessage());
        }
    }

    @Override
    public String getBotUsername() {
        return botConfig.getMotusBotName();
    }

    @Override
    public String getBotToken() {
        return botConfig.getMotusBotToken() ;
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
                sendProfileViewMessage(chatId, telegramId);
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

            if (CALLBACK_PROFILE_PHONE.equals(callbackData)) {
                handleProfileEditPhone(chatId, telegramId);
            } else if (CALLBACK_PROFILE_EDIT_CAR.equals(callbackData)) {
                handleProfileEditCar(chatId, telegramId);
            } else if (CALLBACK_PROFILE_ADD_CAR.equals(callbackData)) {
                handleProfileAddCar(chatId, telegramId);
            } else if (callbackData.equals(PROFILE_VIEW_BUTTON.getId())) {
                handleViewProfile(chatId, messageId, telegramId);
            } else if (callbackData.equals(REPAIR_BUTTON.getId())) {
                sendServiceList(chatId, messageId);
            } else if (callbackData.startsWith(CALLBACK_SERVICE_PREFIX)) {
                handleServiceSelected(chatId, messageId, telegramId, callbackData);
            } else if (callbackData.startsWith(CALLBACK_REPAIR_CAR_PREFIX)) {
                handleRepairCarSelected(chatId, messageId, telegramId, callbackData);
            } else if (callbackData.equals(CALLBACK_REPAIR_BACK)) {
                pendingRepairServiceId.remove(telegramId);
                if (partsFlowTelegramIds.remove(telegramId)) {
                    menuMessage(chatId);
                } else {
                    sendServiceList(chatId, messageId);
                }
            } else if (callbackData.equals(PARTS_BUTTON.getId())) {
                handlePartsButton(chatId, messageId, telegramId);
            } else if (callbackData.equals(OPERATOR_BUTTON.getId())) {
                String text = "OPERATOR CALL INFORMATION";
                executeEditMessageText(text, chatId, messageId);
            } else if (callbackData.equals(BACK_BUTTON.getId())) {
                menuMessage(chatId);
            } else if (callbackData != null && callbackData.startsWith(CALLBACK_AVAIL_STATION_PREFIX)) {
                handleAvailableStationSelected(chatId, telegramId, callbackData);
            }
        }
    }

    private void sendServiceList(long chatId, long messageId) {
        List<Service> services = serviceRepository.findAllByOrderByNameAsc();
        String text;
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        if (services.isEmpty()) {
            text = "Запись на ремонт.\n\nСписок услуг пока пуст.";
        } else {
            String serviceTitle = "Запись на ремонт. Выберите вид услуги:";
            for (int i = 0; i < services.size(); i++) {
                Service s = services.get(i);
                List<InlineKeyboardButton> row = new ArrayList<>();
                row.add(createButton(CALLBACK_SERVICE_PREFIX + s.getId().toString(), s.getName()));
                rows.add(row);
            }
            text = serviceTitle;
        }

        List<InlineKeyboardButton> rowBack = new ArrayList<>();
        rowBack.add(createButton(BACK_BUTTON.getId(), "⬅️ Назад"));
        rows.add(rowBack);
        markup.setKeyboard(rows);

        EditMessageText editMessage = new EditMessageText();
        editMessage.setChatId(String.valueOf(chatId));
        editMessage.setMessageId((int) messageId);
        editMessage.setText(text);
        editMessage.setReplyMarkup(markup);
        try {
            execute(editMessage);
        } catch (TelegramApiException e) {
            log.error("Ошибка при отправке списка услуг: {}", e.getMessage());
        }
    }

    private void handleServiceSelected(long chatId, long messageId, Long telegramId, String callbackData) {
        String serviceIdStr = callbackData.substring(CALLBACK_SERVICE_PREFIX.length());
        UUID serviceId;
        try {
            serviceId = UUID.fromString(serviceIdStr);
        } catch (IllegalArgumentException e) {
            executeEditMessageText("Ошибка выбора услуги.", chatId, messageId);
            return;
        }
        Optional<Client> clientOpt = clientRepository.findByTelegramId(telegramId);
        if (clientOpt.isEmpty()) {
            executeEditMessageText("Сначала зарегистрируйтесь с помощью команды /start", chatId, messageId);
            return;
        }
        Client client = clientOpt.get();
        List<Car> cars = carRepository.findByClient(client);
        if (cars == null || cars.isEmpty()) {
            executeEditMessageText("Добавьте автомобиль в профиле (команда «Редактировать профиль» в меню).", chatId, messageId);
            return;
        }
        pendingRepairServiceId.put(telegramId, serviceId);
        sendCarListForRepair(chatId, messageId, cars);
    }

    private void sendCarListForRepair(long chatId, long messageId, List<Car> cars) {
        sendCarListForRepair(chatId, messageId, cars, "Выберите автомобиль для обслуживания:");
    }

    private void sendCarListForRepair(long chatId, long messageId, List<Car> cars, String titleText) {
        String text = titleText;
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (Car car : cars) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            String label = String.format("%s %s, %d г.", car.getMake(), car.getModel(), car.getYear());
            row.add(createButton(CALLBACK_REPAIR_CAR_PREFIX + car.getId().toString(), label));
            rows.add(row);
        }
        List<InlineKeyboardButton> rowBack = new ArrayList<>();
        rowBack.add(createButton(CALLBACK_REPAIR_BACK, "⬅️ Назад"));
        rows.add(rowBack);
        markup.setKeyboard(rows);
        EditMessageText edit = new EditMessageText();
        edit.setChatId(String.valueOf(chatId));
        edit.setMessageId((int) messageId);
        edit.setText(text);
        edit.setReplyMarkup(markup);
        try {
            execute(edit);
        } catch (TelegramApiException e) {
            log.error("Ошибка при отправке списка автомобилей: {}", e.getMessage());
        }
    }

    private void handlePartsButton(long chatId, long messageId, Long telegramId) {
        Optional<Service> partsService = serviceRepository.findFirstByName(SERVICE_PARTS_SELECTION);
        if (partsService.isEmpty()) {
            executeEditMessageText("Услуга «Подбор запчастей» недоступна.", chatId, messageId);
            return;
        }
        Optional<Client> clientOpt = clientRepository.findByTelegramId(telegramId);
        if (clientOpt.isEmpty()) {
            executeEditMessageText("Сначала зарегистрируйтесь с помощью команды /start", chatId, messageId);
            return;
        }
        List<Car> cars = carRepository.findByClient(clientOpt.get());
        if (cars == null || cars.isEmpty()) {
            executeEditMessageText("Добавьте автомобиль в профиле (команда «Редактировать профиль» в меню).", chatId, messageId);
            return;
        }
        partsFlowTelegramIds.add(telegramId);
        pendingRepairServiceId.put(telegramId, partsService.get().getId());
        sendCarListForRepair(chatId, messageId, cars, "Подбор запчастей. Выберите автомобиль:");
    }

    private void handleRepairCarSelected(long chatId, long messageId, Long telegramId, String callbackData) {
        partsFlowTelegramIds.remove(telegramId);
        UUID serviceId = pendingRepairServiceId.remove(telegramId);
        String carIdStr = callbackData.substring(CALLBACK_REPAIR_CAR_PREFIX.length());
        UUID carId;
        try {
            carId = UUID.fromString(carIdStr);
        } catch (IllegalArgumentException e) {
            executeEditMessageText("Ошибка выбора автомобиля.", chatId, messageId);
            return;
        }
        Optional<Client> clientOpt = clientRepository.findByTelegramId(telegramId);
        Optional<Service> serviceOpt = serviceId != null ? serviceRepository.findById(serviceId) : Optional.empty();
        Optional<Car> carOpt = carRepository.findById(carId);
        if (clientOpt.isEmpty() || serviceOpt.isEmpty() || carOpt.isEmpty()) {
            executeEditMessageText("Данные не найдены. Попробуйте снова.", chatId, messageId);
            return;
        }
        Client client = clientOpt.get();
        Car car = carOpt.get();
        Service service = serviceOpt.get();
        boolean needsVin = SERVICE_PARTS_SELECTION.equals(service.getName())
                && (car.getVin() == null || car.getVin().trim().isEmpty());
        if (needsVin) {
            pendingRepairServiceId.put(telegramId, serviceId);
            pendingRepairCarId.put(telegramId, carId);
            userStates.put(telegramId, RegistrationState.WAITING_FOR_VIN);
            executeEditMessageText("Вы выбрали:\n🔧 Услуга: " + service.getName() + "\n🚗 Автомобиль: " + String.format("%s %s, %d г.", car.getMake(), car.getModel(), car.getYear()) + "\n\nДля подбора запчастей укажите VIN-номер автомобиля:", chatId, messageId);
            return;
        }
        ServiceOrder order = new ServiceOrder();
        order.setClient(client);
        order.setCar(car);
        order.setService(service);
        order.setStation(null);
        order.setServiceTime(LocalDateTime.now());
        try {
            order = serviceOrderRepository.save(order);
            pendingServiceOrderId.put(telegramId, order.getId());
            userStates.put(telegramId, RegistrationState.WAITING_FOR_WORK_DESCRIPTION);
            executeEditMessageText("Вы выбрали:\n🔧 Услуга: " + service.getName() + "\n🚗 Автомобиль: " + String.format("%s %s, %d г.", car.getMake(), car.getModel(), car.getYear()) + "\n\nОпишите, что требуется сделать (описание работ):", chatId, messageId);
        } catch (Exception e) {
            log.error("Ошибка при создании заказа: {}", e.getMessage(), e);
            executeEditMessageText("Ошибка при создании заявки. Попробуйте позже.", chatId, messageId);
        }
    }

    private void handleViewProfile(long chatId, long messageId, Long telegramId) {
        String profileText = buildProfileText(telegramId);
        if (profileText == null) {
            executeEditMessageText("Сначала зарегистрируйтесь с помощью команды /start", chatId, messageId);
            return;
        }
        executeEditMessageText(profileText, chatId, messageId);
    }

    private void sendProfileViewMessage(long chatId, Long telegramId) {
        String profileText = buildProfileText(telegramId);
        if (profileText == null) {
            sendMessage(chatId, "Сначала зарегистрируйтесь с помощью команды /start");
            return;
        }
        sendMessage(chatId, profileText);
    }

    private String buildProfileText(Long telegramId) {
        Optional<Client> clientOpt = clientRepository.findByTelegramId(telegramId);
        if (clientOpt.isEmpty()) {
            return null;
        }
        Client client = clientOpt.get();
        List<Car> cars = carRepository.findByClient(client);
        StringBuilder sb = new StringBuilder("📋 Мой профиль\n\n");
        sb.append("👤 Имя: ").append(client.getName() != null ? client.getName() : "—").append("\n");
        sb.append("📞 Телефон: ").append(client.getPhoneNumber() != null ? client.getPhoneNumber() : "—").append("\n\n");
        if (cars == null || cars.isEmpty()) {
            sb.append("🚗 Автомобили: не добавлены");
        } else {
            sb.append("🚗 Автомобили:\n");
            for (int i = 0; i < cars.size(); i++) {
                Car c = cars.get(i);
                sb.append(i + 1).append(". ").append(c.getMake() != null ? c.getMake() : "—")
                        .append(" ").append(c.getModel() != null ? c.getModel() : "—")
                        .append(", ").append(c.getYear() > 0 ? c.getYear() : "—").append(" г.\n");
            }
        }
        return sb.toString();
    }

    private void handleStartCommand(long chatId, Long telegramId) {
        Optional<Client> existingClient = clientRepository.findByTelegramId(telegramId);
        
        if (existingClient.isPresent()) {
            menuMessage(chatId);
        } else {
            // Начинаем процесс регистрации
            Client newClient = new Client();
            newClient.setTelegramId(telegramId);
            pendingClients.put(telegramId, newClient);
            
            Car newCar = new Car();
            pendingCars.put(telegramId, newCar);
            
            userStates.put(telegramId, RegistrationState.WAITING_FOR_NAME);
            sendMessage(chatId, "Добро пожаловать! Для начала работы нам нужна информация о вас.\n\nПожалуйста, введите ваше имя:");
        }
    }

    private void handleProfileCommand(long chatId, Long telegramId) {
        Optional<Client> existingClient = clientRepository.findByTelegramId(telegramId);
        
        if (existingClient.isEmpty()) {
            sendMessage(chatId, "Сначала зарегистрируйтесь с помощью команды /start");
            return;
        }
        
        sendProfileMenu(chatId);
    }

    private void sendProfileMenu(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("Редактирование профиля. Выберите действие:");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(createButton(CALLBACK_PROFILE_PHONE, "Изменить номер телефона"));
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(createButton(CALLBACK_PROFILE_EDIT_CAR, "Изменить автомобиль"));
        List<InlineKeyboardButton> row3 = new ArrayList<>();
        row3.add(createButton(CALLBACK_PROFILE_ADD_CAR, "Добавить автомобиль"));
        rows.add(row1);
        rows.add(row2);
        rows.add(row3);
        markup.setKeyboard(rows);
        message.setReplyMarkup(markup);
        executeMessage(message);
    }

    private void handleProfileEditPhone(long chatId, Long telegramId) {
        Optional<Client> clientOpt = clientRepository.findByTelegramId(telegramId);
        if (clientOpt.isEmpty()) {
            sendMessage(chatId, "Клиент не найден. Используйте /start");
            return;
        }
        Client client = clientOpt.get();
        pendingClients.put(telegramId, client);
        userStates.put(telegramId, RegistrationState.EDIT_PHONE);
        String current = client.getPhoneNumber() != null ? client.getPhoneNumber() : "не указан";
        sendMessage(chatId, "Введите новый номер телефона (текущий: " + current + "):");
    }

    private void handleProfileEditCar(long chatId, Long telegramId) {
        Optional<Client> clientOpt = clientRepository.findByTelegramId(telegramId);
        if (clientOpt.isEmpty()) {
            sendMessage(chatId, "Клиент не найден. Используйте /start");
            return;
        }
        Client client = clientOpt.get();
        List<Car> cars = carRepository.findByClient(client);
        if (cars.isEmpty()) {
            sendMessage(chatId, "У вас пока нет автомобилей. Выберите «Добавить автомобиль».");
            return;
        }
        Car car = cars.get(0);
        pendingClients.put(telegramId, client);
        pendingCars.put(telegramId, car);
        userStates.put(telegramId, RegistrationState.WAITING_FOR_CAR_MAKE);
        String currentMake = car.getMake() != null ? car.getMake() : "не указана";
        sendMessage(chatId, "Введите марку автомобиля (текущая: " + currentMake + "):");
    }

    private void handleProfileAddCar(long chatId, Long telegramId) {
        Optional<Client> clientOpt = clientRepository.findByTelegramId(telegramId);
        if (clientOpt.isEmpty()) {
            sendMessage(chatId, "Клиент не найден. Используйте /start");
            return;
        }
        Client client = clientOpt.get();
        Car newCar = new Car();
        newCar.setClient(client);
        pendingClients.put(telegramId, client);
        pendingCars.put(telegramId, newCar);
        userStates.put(telegramId, RegistrationState.WAITING_FOR_CAR_MAKE);
        sendMessage(chatId, "Добавление автомобиля.\n\nВведите марку (например: Toyota, BMW):");
    }

    private void handleUserInput(long chatId, Long telegramId, String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        
        RegistrationState state = userStates.getOrDefault(telegramId, RegistrationState.NONE);

        switch (state) {
            case EDIT_PHONE:
                Client clientEdit = pendingClients.get(telegramId);
                if (clientEdit != null) {
                    clientEdit.setPhoneNumber(text.trim());
                    try {
                        clientRepository.save(clientEdit);
                        sendMessage(chatId, "Номер телефона обновлён.");
                        userStates.remove(telegramId);
                        pendingClients.remove(telegramId);
                    } catch (Exception e) {
                        log.error("Ошибка при сохранении номера: {}", e.getMessage(), e);
                        sendMessage(chatId, "Ошибка при сохранении. Попробуйте позже.");
                    }
                }
                break;

            case WAITING_FOR_NAME:
                Client client = pendingClients.get(telegramId);
                if (client != null) {
                    client.setName(text.trim());
                    userStates.put(telegramId, RegistrationState.WAITING_FOR_PHONE);
                    String currentPhone = client.getPhoneNumber() != null ? client.getPhoneNumber() : "не указан";
                    sendMessage(chatId, "Введите ваш номер телефона (текущий: " + currentPhone + "):");
                }
                break;

            case WAITING_FOR_PHONE:
                client = pendingClients.get(telegramId);
                if (client != null) {
                    client.setPhoneNumber(text.trim());
                    userStates.put(telegramId, RegistrationState.WAITING_FOR_CAR_MAKE);
                    Car pendingCar = pendingCars.get(telegramId);
                    String currentMake = pendingCar != null && pendingCar.getMake() != null ? pendingCar.getMake() : "не указана";
                    sendMessage(chatId, "Введите марку автомобиля (текущая: " + currentMake + "):");
                }
                break;

            case WAITING_FOR_CAR_MAKE:
                Car car = pendingCars.get(telegramId);
                if (car != null) {
                    car.setMake(text.trim());
                    userStates.put(telegramId, RegistrationState.WAITING_FOR_CAR_MODEL);
                    String currentModel = car.getModel() != null ? car.getModel() : "не указана";
                    sendMessage(chatId, "Введите модель автомобиля (текущая: " + currentModel + "):");
                }
                break;

            case WAITING_FOR_CAR_MODEL:
                car = pendingCars.get(telegramId);
                if (car != null) {
                    car.setModel(text.trim());
                    userStates.put(telegramId, RegistrationState.WAITING_FOR_CAR_YEAR);
                    String currentYear = car.getYear() > 0 ? String.valueOf(car.getYear()) : "не указан";
                    sendMessage(chatId, "Введите год выпуска автомобиля (текущий: " + currentYear + "):");
                }
                break;

            case WAITING_FOR_CAR_YEAR:
                car = pendingCars.get(telegramId);
                client = pendingClients.get(telegramId);
                if (car != null && client != null) {
                    try {
                        int year = Integer.parseInt(text.trim());
                        if (year < 1900 || year > 2100) {
                            sendMessage(chatId, "Пожалуйста, введите корректный год выпуска (от 1900 до 2100):");
                            return;
                        }
                        car.setYear(year);
                        car.setClient(client);
                        
                        // Сохраняем клиента и автомобиль
                        boolean isProfileEdit = client.getId() != null;
                        Client savedClient = clientRepository.save(client);
                        car.setClient(savedClient);
                        carRepository.save(car);
                        
                        sendMessage(chatId, isProfileEdit ? "Профиль успешно обновлён!" : "Спасибо! Регистрация завершена успешно!");
                        userStates.remove(telegramId);
                        pendingClients.remove(telegramId);
                        pendingCars.remove(telegramId);
                        
                        // Показываем меню
                        menuMessage(chatId);
                    } catch (NumberFormatException e) {
                        sendMessage(chatId, "Пожалуйста, введите год выпуска числом (например: 2020):");
                    } catch (Exception e) {
                        log.error("Ошибка при сохранении клиента и автомобиля: {}", e.getMessage(), e);
                        sendMessage(chatId, "Произошла ошибка при регистрации. Пожалуйста, попробуйте позже.");
                    }
                }
                break;

            case WAITING_FOR_VIN: {
                UUID serviceId = pendingRepairServiceId.remove(telegramId);
                UUID carId = pendingRepairCarId.remove(telegramId);
                userStates.remove(telegramId);
                if (serviceId == null || carId == null) {
                    sendMessage(chatId, "Сессия истекла. Начните запись заново.");
                    return;
                }
                Optional<Client> clientOpt = clientRepository.findByTelegramId(telegramId);
                Optional<Service> serviceOpt = serviceRepository.findById(serviceId);
                Optional<Car> carOpt = carRepository.findById(carId);
                if (clientOpt.isEmpty() || serviceOpt.isEmpty() || carOpt.isEmpty()) {
                    sendMessage(chatId, "Данные не найдены. Попробуйте снова.");
                    return;
                }
                Client clientVin = clientOpt.get();
                Car carVin = carOpt.get();
                Service serviceVin = serviceOpt.get();
                carVin.setVin(text.trim());
                try {
                    carRepository.save(carVin);
                    ServiceOrder order = new ServiceOrder();
                    order.setClient(clientVin);
                    order.setCar(carVin);
                    order.setService(serviceVin);
                    order.setStation(null);
                    order.setServiceTime(LocalDateTime.now());
                    order = serviceOrderRepository.save(order);
                    pendingServiceOrderId.put(telegramId, order.getId());
                    userStates.put(telegramId, RegistrationState.WAITING_FOR_WORK_DESCRIPTION);
                    sendMessage(chatId, "VIN-номер сохранён.\n\nОпишите, что требуется сделать (описание работ):");
                } catch (Exception e) {
                    log.error("Ошибка при сохранении VIN или создании заявки: {}", e.getMessage(), e);
                    sendMessage(chatId, "Ошибка при сохранении. Попробуйте позже.");
                }
                break;
            }

            case WAITING_FOR_WORK_DESCRIPTION: {
                UUID orderId = pendingServiceOrderId.remove(telegramId);
                userStates.remove(telegramId);
                if (orderId == null) {
                    sendMessage(chatId, "Сессия заявки истекла. Начните запись на ремонт заново.");
                    return;
                }
                Optional<ServiceOrder> orderOpt = serviceOrderRepository.findByIdWithServiceAndClientAndCar(orderId);
                if (orderOpt.isEmpty()) {
                    sendMessage(chatId, "Заявка не найдена. Попробуйте снова.");
                    return;
                }
                ServiceOrder order = orderOpt.get();
                order.setWorkDescription(text.trim());
                order.setStatus(OrderStatus.AVAILABLE);
                try {
                    UUID serviceId = order.getService() != null ? order.getService().getId() : null;
                    serviceOrderRepository.save(order);
                    sendMessage(chatId, "Заявка создана. Статус: Доступен.");
                    if (serviceId != null) {
                        sendAvailableStationsMessage(chatId, serviceId, order.getId(), telegramId);
                    }
                } catch (Exception e) {
                    log.error("Ошибка при сохранении заявки: {}", e.getMessage(), e);
                    sendMessage(chatId, "Ошибка при сохранении заявки. Попробуйте позже.");
                }
                break;
            }

            default:
                sendMessage(chatId, "Используйте команду /start для начала работы.");
                break;
        }
    }

    private void sendAvailableStationsMessage(long chatId, UUID serviceId, UUID orderId, Long telegramId) {
        pendingOrderIdForStationSelection.put(telegramId, orderId);
        List<Station> stations = stationRepository.findByServiceId(serviceId);
        String text = "Список доступных исполнителей";
        if (stations.isEmpty()) {
            sendMessage(chatId, text + "\n\nНет доступных исполнителей по данной услуге.");
            return;
        }
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (Station station : stations) {
            String name = station.getName() != null ? station.getName() : "—";
            String address = station.getAddress() != null ? station.getAddress() : "—";
            String buttonText = name + ", " + address;
            if (buttonText.length() > 64) {
                buttonText = buttonText.substring(0, 61) + "...";
            }
            InlineKeyboardButton btn = createButton(CALLBACK_AVAIL_STATION_PREFIX + station.getId(), buttonText);
            rows.add(List.of(btn));
        }
        markup.setKeyboard(rows);
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        message.setReplyMarkup(markup);
        executeMessage(message);
    }

    private String buildOrderDetailsText(ServiceOrder order) {
        if (order == null) return "";
        String serviceName = order.getService() != null ? order.getService().getName() : "—";
        String clientName = order.getClient() != null ? order.getClient().getName() : "—";
        String clientPhone = order.getClient() != null ? order.getClient().getPhoneNumber() : "—";
        String carInfo = "—";
        if (order.getCar() != null) {
            Car c = order.getCar();
            carInfo = String.format("%s %s, %d г.", c.getMake(), c.getModel(), c.getYear());
        }
        String workDesc = order.getWorkDescription() != null ? order.getWorkDescription() : "—";
        return "🔧 Услуга: " + serviceName + "\n" +
                "👤 Клиент: " + clientName + "\n" +
                "📞 Телефон: " + clientPhone + "\n" +
                "🚗 Автомобиль: " + carInfo + "\n" +
                "📝 Описание работ: " + workDesc;
    }

    private void handleAvailableStationSelected(long chatId, Long telegramId, String callbackData) {
        String idStr = callbackData.substring(CALLBACK_AVAIL_STATION_PREFIX.length());
        UUID stationId;
        try {
            stationId = UUID.fromString(idStr);
        } catch (IllegalArgumentException e) {
            return;
        }
        UUID orderId = pendingOrderIdForStationSelection.get(telegramId);
        Optional<Station> stationOpt = stationRepository.findById(stationId);
        if (stationOpt.isEmpty()) {
            sendMessage(chatId, "Исполнитель не найден.");
            return;
        }
        Station station = stationOpt.get();
        String name = station.getName() != null ? station.getName() : "—";
        String address = station.getAddress() != null ? station.getAddress() : "—";
        if (orderId == null) {
            sendMessage(chatId, "Вы выбрали: " + name + ", " + address);
            return;
        }
        Optional<ServiceOrder> orderOpt = serviceOrderRepository.findByIdWithServiceAndClientAndCar(orderId);
        if (orderOpt.isEmpty()) {
            sendMessage(chatId, "Заявка не найдена.");
            return;
        }
        ServiceOrder order = orderOpt.get();
        String orderDetailsText = buildOrderDetailsText(order);
        motusStationBot.sendOrderToStation(station.getTelegramId(), orderId, orderDetailsText);
        sendMessage(chatId, "Вы выбрали: " + name + ", " + address + ". Заявка отправлена исполнителю.");
    }

    /**
     * Отправляет сообщение клиенту (вызывается из MotusStationBot при подтверждении записи).
     */
    public void sendMessageToClient(long clientTelegramId, String text) {
        sendMessage(clientTelegramId, text);
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

        List<InlineKeyboardButton> rowProfile = new ArrayList<>();
        rowRepair.add(repairButton);
        rowParts.add(partsButton);
        rowOperator.add(operatorButton);
        rowsInLine.add(rowRepair);
        rowsInLine.add(rowParts);
        rowsInLine.add(rowOperator);
        rowsInLine.add(rowProfile);

        markupInLine.setKeyboard(rowsInLine);
        message.setReplyMarkup(markupInLine);

        executeMessage(message);
    }
}
