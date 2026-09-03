package ru.azenizzka.telegram;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.azenizzka.configuration.TelegramBotConfiguration;
import ru.azenizzka.entities.Person;
import ru.azenizzka.repositories.PersonRepository;
import ru.azenizzka.telegram.handlers.InputType;
import ru.azenizzka.telegram.handlers.MasterHandler;

@Component
@Slf4j
public class TelegramBot extends TelegramLongPollingBot {

  private final PersonRepository personRepository;
  private final TelegramBotConfiguration configuration;
  private final MasterHandler masterHandler;
  private static TelegramBot instance;

  public TelegramBot(
      PersonRepository personRepository,
      TelegramBotConfiguration configuration,
      MasterHandler masterHandler) {
    super(configuration.getToken());
    this.personRepository = personRepository;
    this.configuration = configuration;
    this.masterHandler = masterHandler;
    instance = this;
  }

  public static TelegramBot getInstance() {
    return instance;
  }

  @Override
  public String getBotUsername() {
    return configuration.getName();
  }

  @Override
  public void onUpdateReceived(Update update) {
    if (!update.hasMessage() || !update.getMessage().hasText()) {
      return;
    }

    String chatId = update.getMessage().getChatId().toString();
    String username = update.getMessage().getChat().getUserName();

    try {
      // 1. Ищем пользователя или создаем нового
      Person person = personRepository.findByChatId(chatId);
      if (person == null) {
        person = new Person();
        person.setChatId(chatId);
        person.setUsername(username);
        person.setInputType(InputType.COMMAND);
      } else {
        person.setUsername(username);
      }

      // 2. Проверяем админа из .env вместо хардкода
      if (configuration.getAdminChatId() != null
          && !configuration.getAdminChatId().isBlank()
          && configuration.getAdminChatId().equals(chatId)) {
        person.setAdmin(true);
      }

      // 3. Передаем апдейт в FSM роутер
      List<SendMessage> responses = masterHandler.handle(update, person);

      // 4. Одиночное сохранение состояния в БД
      personRepository.save(person);

      // 5. Отправка сообщений
      sendMessage(responses);
    } catch (Exception e) {
      log.error("Ошибка при обработке сообщения от chatId {}: {}", chatId, e.getMessage(), e);
    }
  }

  public void sendMessage(List<SendMessage> messages) {
    if (messages == null || messages.isEmpty()) {
      return;
    }

    for (SendMessage message : messages) {
      try {
        execute(message);
      } catch (TelegramApiException e) {
        log.error("Не удалось отправить сообщение в chatId {}: {}", message.getChatId(), e.getMessage());
      }
    }
  }
}
