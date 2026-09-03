package ru.azenizzka.telegram.handlers;

import java.util.List;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.azenizzka.entities.Person;
import ru.azenizzka.services.LessonScheduleService;
import ru.azenizzka.telegram.keyboards.KeyboardType;
import ru.azenizzka.telegram.messages.CustomMessage;
import ru.azenizzka.telegram.messages.ErrorMessage;
import ru.azenizzka.utils.MessagesConfig;

@Component
public class ChangeGroupHandler implements Handler {
  private final LessonScheduleService lessonScheduleService;

  public ChangeGroupHandler(LessonScheduleService lessonScheduleService) {
    this.lessonScheduleService = lessonScheduleService;
  }

  @Override
  public List<SendMessage> handle(Update update, Person person) {
    String textMessage = update.getMessage().getText().trim();

    try {
      // 1. Вычищаем любые буквы, пробелы, дефисы и спецсимволы
      String digitsOnly = textMessage.replaceAll("[^0-9]", "");

      if (digitsOnly.isBlank()) {
        throw new NumberFormatException();
      }

      int group = Integer.parseInt(digitsOnly);

      if (!lessonScheduleService.isGroupExists(group)) {
        throw new IllegalArgumentException();
      }

      // Переводим в COMMAND только при успешном выборе
      person.setInputType(InputType.COMMAND);
      person.setGroupNum(group);

      SendMessage message = new CustomMessage(person.getChatId(), KeyboardType.MAIN);
      message.setText(MessagesConfig.SUCCESS_CHANGE_GROUP_MESSAGE);
      return List.of(message);

    } catch (Exception e) {
      // Оставляем пользователя в режиме GROUP, чтобы он мог ввести номер повторно
      person.setInputType(InputType.GROUP);
      return List.of(new ErrorMessage(person.getChatId(), MessagesConfig.GROUP_NOT_FOUND_EXCEPTION));
    }
  }
}
