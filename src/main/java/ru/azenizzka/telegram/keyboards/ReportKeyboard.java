package ru.azenizzka.telegram.keyboards;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;

public class ReportKeyboard {

    public static final String REPORT_INIT_PREFIX = "REPORT_INIT:";
    public static final String REPORT_CONFIRM_PREFIX = "REPORT_CONFIRM:";
    public static final String REPORT_CANCEL = "REPORT_CANCEL";

    // Исходная кнопка под сообщением расписания
    public static InlineKeyboardMarkup getKeyboard(String dayIdentifier) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText("⚠️ Расписание неверное/кривое!");
        button.setCallbackData(REPORT_INIT_PREFIX + dayIdentifier);

        return new InlineKeyboardMarkup(List.of(List.of(button)));
    }

    // Клавиатура подтверждения
    public static InlineKeyboardMarkup getConfirmationKeyboard(String dayIdentifier) {
        InlineKeyboardButton confirmBtn = new InlineKeyboardButton();
        confirmBtn.setText("🔴 Да, я уверен, отправить");
        confirmBtn.setCallbackData(REPORT_CONFIRM_PREFIX + dayIdentifier);

        InlineKeyboardButton cancelBtn = new InlineKeyboardButton();
        cancelBtn.setText("Отмена");
        cancelBtn.setCallbackData(REPORT_CANCEL + ":" + dayIdentifier);

        return new InlineKeyboardMarkup(List.of(
                List.of(confirmBtn),
                List.of(cancelBtn)
        ));
    }
}
