package com.remindme.util;

import com.remindme.model.Task;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class TaskDateFormats {
    /** 界面展示：xxxx年xx月xx日 xx：xx */
    public static final DateTimeFormatter UI_DATETIME =
            DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH：mm", Locale.CHINA);

    private TaskDateFormats() {
    }

    public static String formatDateTime(LocalDateTime dt) {
        if (dt == null) {
            return "无";
        }
        return dt.format(UI_DATETIME);
    }

    /**
     * 提醒时刻：取截止日期中的日期 + 提醒时分；无截止日期则用创建日期的日期部分。
     */
    public static LocalDateTime reminderDateTime(Task task) {
        if (task == null) {
            return null;
        }
        LocalTime rt = task.getRemindTime() != null ? task.getRemindTime() : LocalTime.of(9, 0);
        if (task.getDueTime() != null) {
            return LocalDateTime.of(task.getDueTime().toLocalDate(), rt);
        }
        if (task.getCreatedAt() != null) {
            return LocalDateTime.of(task.getCreatedAt().toLocalDate(), rt);
        }
        return LocalDateTime.of(LocalDate.now(), rt);
    }

    public static String formatReminder(Task task) {
        LocalDateTime r = reminderDateTime(task);
        return r == null ? "无" : r.format(UI_DATETIME);
    }
}
