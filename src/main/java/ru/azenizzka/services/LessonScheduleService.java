package ru.azenizzka.services;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.azenizzka.utils.Day;
import ru.azenizzka.utils.MessagesConfig;

@Service
@Slf4j
public class LessonScheduleService {

  @Value("${schedule.url:https://ntmm.ru/5_screen.files/sheet001.htm}")
  private String scheduleUrl;

  private static final SSLSocketFactory SSL_SOCKET_FACTORY = createInsecureSslSocketFactory();
  private static final Pattern GROUP_PATTERN = Pattern.compile("(?<!\\d)\\d{2,3}(?!\\d)");

  // Отформатированный текст расписания: [Группа -> [День -> Текст]]
  private final Map<String, Map<Day, String>> scheduleCache = new ConcurrentHashMap<>();

  // Сырой список пар для звонков: [Группа -> [День -> Список пар [номер, предмет, каб]]]
  private final Map<String, Map<Day, List<List<String>>>> rawLessonsCache = new ConcurrentHashMap<>();

  @PostConstruct
  public void init() {
    updateCache();
  }

  @Scheduled(fixedRate = 10 * 60 * 1000)
  public void scheduledCacheUpdate() {
    updateCache();
  }

  public synchronized void updateCache() {
    log.info("Запуск обновления кэша расписания...");
    try {
      Document doc = Jsoup.connect(scheduleUrl)
          .sslSocketFactory(SSL_SOCKET_FACTORY)
          .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
          .timeout(15_000)
          .get();

      Element table = doc.selectFirst("table");
      if (table == null) {
        log.warn("Таблица расписания не найдена по адресу {}", scheduleUrl);
        return;
      }

      String[][] grid = buildMatrix(table);
      parseMatrix(grid);

      log.info("Кэш успешно обновлен. Загружено групп: {}", scheduleCache.size());
    } catch (Exception e) {
      log.error("Ошибка при обновлении кэша расписания: {}", e.getMessage());
    }
  }

  public boolean isGroupExists(int group) {
    return isGroupExists(String.valueOf(group));
  }

  public boolean isGroupExists(String group) {
    if (group == null || group.isBlank()) return false;
    return scheduleCache.containsKey(group.trim()) || rawLessonsCache.containsKey(group.trim());
  }

  public List<List<String>> getLessons(int groupNum, Day day) {
    return getLessons(String.valueOf(groupNum), day);
  }

  public List<List<String>> getLessons(String groupNum, Day day) {
    if (groupNum == null || day == null) return Collections.emptyList();
    Map<Day, List<List<String>>> days = rawLessonsCache.get(groupNum.trim());
    if (days == null) return Collections.emptyList();
    return days.getOrDefault(day, Collections.emptyList());
  }

  public String getSchedule(int groupNum, Day day) {
    return getSchedule(String.valueOf(groupNum), day);
  }

  public String getSchedule(String groupNum, Day day) {
    if (groupNum == null || groupNum.isBlank()) {
      return MessagesConfig.GROUP_NOT_DEFINED_EXCEPTION;
    }

    Map<Day, String> groupDays = scheduleCache.get(groupNum.trim());
    if (groupDays == null) {
      return MessagesConfig.GROUP_NOT_FOUND_EXCEPTION;
    }

    return groupDays.getOrDefault(day, MessagesConfig.NO_LESSONS_MESSAGE);
  }

  private String[][] buildMatrix(Element table) {
    Elements trs = table.select("tr");
    int rowCount = trs.size();
    int colCount = 100;

    String[][] grid = new String[rowCount][colCount];

    for (int r = 0; r < rowCount; r++) {
      Element tr = trs.get(r);
      Elements cells = tr.children();
      int c = 0;

      for (Element cell : cells) {
        if (!cell.tagName().equalsIgnoreCase("td") && !cell.tagName().equalsIgnoreCase("th")) {
          continue;
        }

        while (c < colCount && grid[r][c] != null) {
          c++;
        }

        if (c >= colCount) break;

        int rowspan = cell.hasAttr("rowspan") ? Integer.parseInt(cell.attr("rowspan")) : 1;
        int colspan = cell.hasAttr("colspan") ? Integer.parseInt(cell.attr("colspan")) : 1;
        String text = cell.text().replace("\u00A0", " ").trim();

        for (int dr = 0; dr < rowspan && (r + dr) < rowCount; dr++) {
          for (int dc = 0; dc < colspan && (c + dc) < colCount; dc++) {
            grid[r + dr][c + dc] = text;
          }
        }
        c += colspan;
      }
    }
    return grid;
  }

  private void parseMatrix(String[][] grid) {
    int groupRowIndex = -1;
    for (int r = 0; r < Math.min(10, grid.length); r++) {
      int groupMatches = 0;
      for (int c = 0; c < grid[r].length; c++) {
        if (grid[r][c] != null && GROUP_PATTERN.matcher(grid[r][c]).find()) {
          groupMatches++;
        }
      }
      if (groupMatches >= 3) {
        groupRowIndex = r;
        break;
      }
    }

    if (groupRowIndex == -1) {
      log.warn("Строка с номерами групп не найдена");
      return;
    }

    Map<Integer, String> colToGroup = new HashMap<>();
    for (int c = 0; c < grid[groupRowIndex].length; c++) {
      String cell = grid[groupRowIndex][c];
      if (cell != null && !cell.isBlank()) {
        Matcher m = GROUP_PATTERN.matcher(cell);
        if (m.find()) {
          String groupNum = m.group();
          if (!colToGroup.containsValue(groupNum)) {
            colToGroup.put(c, groupNum);
          }
        }
      }
    }

    Map<String, Map<Day, String>> newScheduleCache = new HashMap<>();
    Map<String, Map<Day, List<List<String>>>> newRawCache = new HashMap<>();

    Day currentDay = null;

    for (int r = groupRowIndex + 1; r < grid.length; r++) {
      for (int c = 0; c < 5; c++) {
        String val = grid[r][c];
        if (val != null) {
          Day detected = parseDay(val);
          if (detected != null) {
            currentDay = detected;
            break;
          }
        }
      }

      if (currentDay == null) continue;

      for (Map.Entry<Integer, String> entry : colToGroup.entrySet()) {
        int gCol = entry.getKey();
        String groupNum = entry.getValue();

        String pairNum = (gCol < grid[r].length && grid[r][gCol] != null) ? grid[r][gCol] : "";
        String discipline = (gCol + 1 < grid[r].length && grid[r][gCol + 1] != null) ? grid[r][gCol + 1] : "";
        String room = (gCol + 2 < grid[r].length && grid[r][gCol + 2] != null) ? grid[r][gCol + 2] : "";

        if (!discipline.isBlank() && !discipline.equalsIgnoreCase("н/п") && !discipline.equalsIgnoreCase("Дисциплина")) {
          newScheduleCache.computeIfAbsent(groupNum, k -> new HashMap<>());
          Map<Day, String> dayMap = newScheduleCache.get(groupNum);

          String currentSchedule = dayMap.getOrDefault(currentDay, "");
          String lessonLine = String.format("%s. %s %s\n",
              pairNum.isBlank() ? "•" : pairNum,
              discipline,
              room.isBlank() ? "" : "(" + room + ")");
          dayMap.put(currentDay, currentSchedule + lessonLine);

          newRawCache.computeIfAbsent(groupNum, k -> new HashMap<>());
          Map<Day, List<List<String>>> rawDayMap = newRawCache.get(groupNum);
          rawDayMap.computeIfAbsent(currentDay, k -> new ArrayList<>());
          rawDayMap.get(currentDay).add(List.of(pairNum, discipline, room));
        }
      }
    }

    if (!newScheduleCache.isEmpty()) {
      scheduleCache.clear();
      scheduleCache.putAll(newScheduleCache);

      rawLessonsCache.clear();
      rawLessonsCache.putAll(newRawCache);
    }
  }

  private Day parseDay(String text) {
    String t = text.trim().toUpperCase();
    if (t.contains("ПОНЕДЕЛЬНИК") || t.equals("ПН")) return Day.MONDAY;
    if (t.contains("ВТОРНИК") || t.equals("ВТ")) return Day.TUESDAY;
    if (t.contains("СРЕДА") || t.equals("СР")) return Day.WEDNESDAY;
    if (t.contains("ЧЕТВЕРГ") || t.equals("ЧТ")) return Day.THURSDAY;
    if (t.contains("ПЯТНИЦА") || t.equals("ПТ")) return Day.FRIDAY;
    if (t.contains("СУББОТА") || t.equals("СБ")) return Day.SATURDAY;
    return null;
  }

  private static SSLSocketFactory createInsecureSslSocketFactory() {
    TrustManager[] trustAllCerts = new TrustManager[] {
      new X509TrustManager() {
        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        public void checkClientTrusted(X509Certificate[] certs, String authType) {}
        public void checkServerTrusted(X509Certificate[] certs, String authType) {}
      }
    };

    try {
      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(null, trustAllCerts, new SecureRandom());
      return sslContext.getSocketFactory();
    } catch (Exception e) {
      throw new IllegalStateException("Ошибка инициализации SSL", e);
    }
  }
}
