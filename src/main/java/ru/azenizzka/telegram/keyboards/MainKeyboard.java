package ru.azenizzka.telegram.keyboards;

import java.util.ArrayList;
import java.util.List;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import ru.azenizzka.utils.MessagesConfig;

public class MainKeyboard {
  public static void addKeyboard(SendMessage message) {
    ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
    List<KeyboardRow> keyboard = new ArrayList<>();
    
    KeyboardRow row = new KeyboardRow();

    // Если "Звонки" тебе тут больше не нужны, можешь удалить строку ниже:
    row.add(MessagesConfig.RECESS_SCHEDULE_COMMAND); 
    row.add(MessagesConfig.LESSON_SCHEDULE_COMMAND);

    keyboard.add(row);
    row = new KeyboardRow();

    row.add(MessagesConfig.SETTINGS_COMMAND);
    row.add("ℹ️ О боте"); // <-- Наша новая кнопка
    
    keyboard.add(row);

    keyboardMarkup.setResizeKeyboard(true);
    keyboardMarkup.setKeyboard(keyboard);
    message.setReplyMarkup(keyboardMarkup);
  }
}
