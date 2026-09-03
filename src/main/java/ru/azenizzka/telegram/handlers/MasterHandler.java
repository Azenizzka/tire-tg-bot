package ru.azenizzka.telegram.handlers;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.azenizzka.configuration.TelegramBotConfiguration;
import ru.azenizzka.entities.Person;
import ru.azenizzka.telegram.messages.ErrorMessage;
import ru.azenizzka.utils.MessagesConfig;

@Component
@Slf4j
public class MasterHandler implements Handler {

  private final CommandsHandler commandsHandler;
  private final BellTypeHandler bellTypeHandler;
  private final ChangeGroupHandler changeGroupHandler;
  private final SettingHandler settingHandler;
  private final RecessHandler recessHandler;
  private final AuditLogHandler auditLogHandler;

  private final TelegramBotConfiguration configuration;

  public MasterHandler(
      TelegramBotConfiguration configuration,
      CommandsHandler commandsHandler,
      BellTypeHandler bellTypeHandler,
      ChangeGroupHandler changeGroupHandler,
      SettingHandler settingHandler,
      RecessHandler recessHandler,
      AuditLogHandler auditLogHandler) {
    this.configuration = configuration;

    this.commandsHandler = commandsHandler;
    this.bellTypeHandler = bellTypeHandler;
    this.changeGroupHandler = changeGroupHandler;
    this.settingHandler = settingHandler;
    this.recessHandler = recessHandler;
    this.auditLogHandler = auditLogHandler;
  }

  @Override
  public List<SendMessage> handle(Update update, Person person) {
    // 1. Если пишут админы из аудит-чата (баны, ответы пользователям) - сразу отдаем управление логеру
    if (person.getChatId().equals(configuration.getAuditLogChatId())) {
      return auditLogHandler.handle(update, person);
    }

    List<SendMessage> messages = new ArrayList<>();

    if (update.getMessage().getText() != null && update.getMessage().getText().equals(MessagesConfig.RETURN_COMMAND)) {
      person.setInputType(InputType.COMMAND);
    }

    if (person.isBanned()) {
      messages.add(new ErrorMessage(person.getChatId(), "Ошибка доступа к боту.."));
      return messages;
    }

    // 2. Обрабатываем запрос обычного юзера
    try {
      switch (person.getInputType()) {
        case COMMAND -> messages.addAll(commandsHandler.handle(update, person));
        case BELL_TYPE -> messages.addAll(bellTypeHandler.handle(update, person));
        case GROUP -> messages.addAll(changeGroupHandler.handle(update, person));
        case SETTINGS_MAIN -> messages.addAll(settingHandler.handle(update, person));
        case DAY -> messages.addAll(recessHandler.handle(update, person));
      }

      // 3. Проверяем, есть ли среди ответов бота ошибка (сообщение типа ErrorMessage)
      boolean hasError = messages.stream().anyMatch(m -> m instanceof ErrorMessage);

      // Логируем в аудит-чат ТОЛЬКО если бот ответил юзеру ошибкой
      if (hasError) {
        messages.addAll(auditLogHandler.handle(update, person));
      }

    } catch (Exception e) {
      log.error("Unhandled exception for user {}: ", person.getChatId(), e);
      messages.add(new ErrorMessage(person.getChatId(), "Произошла системная ошибка."));
      
      // В случае краша (например, NullPointerException) тоже кидаем исходный запрос в аудит, чтобы ты видел, на чем бот упал
      messages.addAll(auditLogHandler.handle(update, person));
    }

    return messages;
  }
}
