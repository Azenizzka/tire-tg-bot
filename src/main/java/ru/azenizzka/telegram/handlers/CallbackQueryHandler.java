package ru.azenizzka.telegram.handlers;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.azenizzka.configuration.TelegramBotConfiguration;
import ru.azenizzka.entities.Person;
import ru.azenizzka.services.PersonService;
import ru.azenizzka.telegram.TelegramBot;
import ru.azenizzka.telegram.keyboards.ReportKeyboard;

@Component
@Slf4j
public class CallbackQueryHandler {

  private final PersonService personService;
  private final TelegramBotConfiguration configuration;
  private final TelegramBot telegramBot;

  private final Map<Long, Long> cooldownMap = new ConcurrentHashMap<>();
  private static final long COOLDOWN_MS = 5 * 60 * 1000; // 5 минут

  private static final String WARNING_TEXT =
      "Вы точно уверены?\n\n"
          + "Если вы нажмете на подтверждение, а расписание верное, "
          + "то вы будете временно заблокированы для репортов. "
          + "За повторные нарушения возможна вечная блокировка в боте.";

  public CallbackQueryHandler(
      PersonService personService,
      TelegramBotConfiguration configuration,
      @Lazy TelegramBot telegramBot) {
    this.personService = personService;
    this.configuration = configuration;
    this.telegramBot = telegramBot;
  }

  public void handle(CallbackQuery callbackQuery) {
    String data = callbackQuery.getData();
    if (data == null) return;

    if (data.startsWith(ReportKeyboard.REPORT_INIT_PREFIX)) {
      handleInitialClick(callbackQuery);
    } else if (data.startsWith(ReportKeyboard.REPORT_CONFIRM_PREFIX)) {
      handleConfirmation(callbackQuery);
    } else if (data.startsWith(ReportKeyboard.REPORT_CANCEL)) {
      handleCancel(callbackQuery);
    }
  }

  private void handleInitialClick(CallbackQuery callbackQuery) {
    long userId = callbackQuery.getFrom().getId();
    long now = System.currentTimeMillis();

    if (cooldownMap.containsKey(userId) && (now - cooldownMap.get(userId) < COOLDOWN_MS)) {
      long waitSec = (COOLDOWN_MS - (now - cooldownMap.get(userId))) / 1000;
      answer(callbackQuery.getId(), "⚠️ Вы уже отправляли жалобу. Подождите " + waitSec + " сек.", true);
      return;
    }

    String day = callbackQuery.getData().substring(ReportKeyboard.REPORT_INIT_PREFIX.length());

    answer(callbackQuery.getId(), WARNING_TEXT, true);
    editMarkup(callbackQuery, ReportKeyboard.getConfirmationKeyboard(day));
  }

  private void handleConfirmation(CallbackQuery callbackQuery) {
    long userId = callbackQuery.getFrom().getId();
    long now = System.currentTimeMillis();

    if (cooldownMap.containsKey(userId) && (now - cooldownMap.get(userId) < COOLDOWN_MS)) {
      long waitSec = (COOLDOWN_MS - (now - cooldownMap.get(userId))) / 1000;
      answer(callbackQuery.getId(), "⚠️ Вы уже отправляли жалобу. Подождите " + waitSec + " сек.", true);
      return;
    }

    cooldownMap.put(userId, now);

    String day = callbackQuery.getData().substring(ReportKeyboard.REPORT_CONFIRM_PREFIX.length());
    Person person = personService.findByChatId(String.valueOf(userId));
    
    String group = (person != null && person.getGroupNum() != 0) ? String.valueOf(person.getGroupNum()) : "Не указана";
    String username = callbackQuery.getFrom().getUserName() != null ? "@" + callbackQuery.getFrom().getUserName() : "нет username";

    String alertMessage = String.format(
        "🚨 *Подтвержденная жалоба на расписание!*\n"
            + "👤 Пользователь: %s (`%d`)\n"
            + "👥 Группа: `%s`\n"
            + "📅 День: `%s`",
        username, userId, group, day);

    try {
      SendMessage sendAlert = new SendMessage(configuration.getAuditLogChatId(), alertMessage);
      sendAlert.enableMarkdown(true);
      telegramBot.execute(sendAlert);
    } catch (TelegramApiException e) {
      log.error("Ошибка при отправке алерта в аудит-чат: ", e);
    }

    answer(callbackQuery.getId(), "Жалоба отправлена разработчику!", false);
    editMarkup(callbackQuery, null);
  }

  private void handleCancel(CallbackQuery callbackQuery) {
    String[] parts = callbackQuery.getData().split(":", 2);
    String day = parts.length > 1 ? parts[1] : "0";

    answer(callbackQuery.getId(), "Отправка отменена", false);
    editMarkup(callbackQuery, ReportKeyboard.getKeyboard(day));
  }

  private void editMarkup(CallbackQuery query, org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup markup) {
    try {
      EditMessageReplyMarkup edit = new EditMessageReplyMarkup();
      edit.setChatId(query.getMessage().getChatId().toString());
      edit.setMessageId(query.getMessage().getMessageId());
      edit.setReplyMarkup(markup);
      telegramBot.execute(edit);
    } catch (TelegramApiException ignored) {
    }
  }

  private void answer(String queryId, String text, boolean alert) {
    try {
      AnswerCallbackQuery answer = new AnswerCallbackQuery();
      answer.setCallbackQueryId(queryId);
      answer.setText(text);
      answer.setShowAlert(alert);
      telegramBot.execute(answer);
    } catch (TelegramApiException e) {
      log.error("Ошибка AnswerCallbackQuery: ", e);
    }
  }
}
