package ru.azenizzka.app.telegram.commands;

import java.util.List;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.azenizzka.app.entities.Person;

public interface Command {
  String getCommand();

  boolean isRequiredAdminRights();

  List<SendMessage> handle(Update update, Person person);
}