package ru.azenizzka.app.utils;

import ru.azenizzka.app.exceptions.BellTypeConvertException;

public class BellUtil {
  public static BellType convertStrToBell(String str) throws BellTypeConvertException {
    switch (str) {
      case "основное" -> {
        return BellType.MAIN;
      }
      case "понедельник" -> {
        return BellType.MONDAY;
      }
      case "суббота" -> {
        return BellType.SATURDAY;
      }
    }

    throw new BellTypeConvertException(MessagesConfig.BELL_TYPE_CONVERT_EXCEPTION);
  }
}
