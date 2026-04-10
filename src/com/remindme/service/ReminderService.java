package com.remindme.service;

import com.remindme.model.Task;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 在 JavaFX 线程上读取 ObservableList、弹窗；墙钟等待使用 ScheduledExecutorService（比 PauseTransition 更可靠）。
 */
public final class ReminderService {

    private final ObservableList<Task> tasks;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "remindme-reminder-scheduler");
        t.setDaemon(true);
        return t;
    });

    private final Map<String, LocalDateTime> remindedForInstant = new ConcurrentHashMap<>();
    // 记录应用启动时间，用来区分“启动前已过期任务”和“运行中到点任务”
    private final LocalDateTime appStartedAt = LocalDateTime.now();

    private volatile ScheduledFuture<?> pendingFuture;
    private volatile boolean started;
    private volatile boolean stopped;
    private ListChangeListener<Task> listListener;
    private Window ownerWindow;

    public ReminderService(ObservableList<Task> tasks) {
        this.tasks = tasks;
    }

    public void setOwnerWindow(Window owner) {
        this.ownerWindow = owner;
    }

    public void start() {
        if (started) {
            return;
        }
        started = true;
        listListener = c -> reschedule();
        tasks.addListener(listListener);
        // 首次排程应由 attachReminderOwner / UI 就绪后调用 reschedule()，避免与 cancel 竞态
    }

    public void reschedule() {
        if (stopped) {
            return;
        }
        if (Platform.isFxApplicationThread()) {
            doScheduleNextOnFx();
        } else {
            Platform.runLater(this::doScheduleNextOnFx);
        }
    }

    public void shutdown() {
        if (stopped) {
            return;
        }
        stopped = true;
        Runnable cleanup = () -> {
            if (listListener != null) {
                tasks.removeListener(listListener);
                listListener = null;
            }
            cancelPendingSchedule();
            scheduler.shutdown();
        };
        if (Platform.isFxApplicationThread()) {
            cleanup.run();
        } else {
            Platform.runLater(cleanup);
        }
    }

    private void cancelPendingSchedule() {
        ScheduledFuture<?> f = pendingFuture;
        pendingFuture = null;
        if (f != null) {
            f.cancel(false);
        }
    }

    /** 仅在 JavaFX 应用线程调用：快照任务列表并安排下一次触发 */
    private void doScheduleNextOnFx() {
        if (stopped) {
            return;
        }
        pruneRemindedMap();
        cancelPendingSchedule();

        List<Task> snapshot = new ArrayList<>(tasks);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextFire = computeEarliestFire(snapshot, now);
        if (nextFire == null) {
            return;
        }

        long delayMs = ChronoUnit.MILLIS.between(now, nextFire);
        if (delayMs < 0) {
            delayMs = 0;
        }
        if (delayMs > TimeUnit.DAYS.toMillis(3650)) {
            delayMs = TimeUnit.DAYS.toMillis(3650);
        }

        try {
            pendingFuture = scheduler.schedule(
                    () -> Platform.runLater(this::fireDueRemindersOnFx),
                    delayMs,
                    TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException ignored) {
            // 应用正在关闭，调度器已 shutdown
        }
    }

    /** 仅在 JavaFX 应用线程调用 */
    private void fireDueRemindersOnFx() {
        if (stopped) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<Task> snapshot = new ArrayList<>(tasks);
        List<Task> batch = new ArrayList<>();
        for (Task t : snapshot) {
            LocalDateTime at = reminderInstant(t);
            if (shouldSkipReminder(t, at)) {
                continue;
            }
            if (alreadyRemindedFor(t.getId(), at)) {
                continue;
            }

            // 启动前就已逾期的任务交给启动弹窗，不在运行期重复补弹
            if (!at.isAfter(appStartedAt) && !at.isAfter(now)) {
                continue;
            }

            if (!at.isAfter(now)) {
                batch.add(t);
            }
        }

        for (Task t : batch) {
            showReminder(t);
            LocalDateTime at = reminderInstant(t);
            if (at != null && t.getId() != null) {
                remindedForInstant.put(t.getId(), at);
            }
        }

        doScheduleNextOnFx();
    }

    private LocalDateTime computeEarliestFire(List<Task> snapshot, LocalDateTime now) {
        LocalDateTime nextFire = null;
        for (Task t : snapshot) {
            LocalDateTime at = reminderInstant(t);
            if (shouldSkipReminder(t, at)) {
                continue;
            }
            if (alreadyRemindedFor(t.getId(), at)) {
                continue;
            }

            // 启动前就已经过期的任务，不在这里单个弹窗处理
            if (!at.isAfter(now)) {
                continue;
            }

            if (nextFire == null || at.isBefore(nextFire)) {
                nextFire = at;
            }
        }
        return nextFire;
    }

    private void showReminder(Task task) {
        String title = task.getTitle() == null ? "" : task.getTitle().trim();
        String msg = title.isEmpty() ? "任务截止啦" : "「" + title + "」截止啦";

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initModality(Modality.APPLICATION_MODAL);
        if (ownerWindow != null) {
            alert.initOwner(ownerWindow);
        }
        alert.setTitle("提醒");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        ButtonType ok = new ButtonType("知道啦", ButtonBar.ButtonData.OK_DONE);
        alert.getButtonTypes().setAll(ok);
        alert.setOnShown(e -> {
            Window w = alert.getDialogPane().getScene() != null
                    ? alert.getDialogPane().getScene().getWindow()
                    : null;
            if (w instanceof Stage stage) {
                stage.setAlwaysOnTop(true);
            }
        });
        alert.showAndWait();
    }

    private void pruneRemindedMap() {
        Iterator<Map.Entry<String, LocalDateTime>> it = remindedForInstant.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, LocalDateTime> e = it.next();
            Task task = findById(e.getKey());
            if (task == null || task.isCompleted()) {
                it.remove();
                continue;
            }
            LocalDateTime current = reminderInstant(task);
            if (current == null || !current.equals(e.getValue())) {
                it.remove();
            }
        }
    }

    private Task findById(String id) {
        if (id == null) {
            return null;
        }
        for (Task t : tasks) {
            if (id.equals(t.getId())) {
                return t;
            }
        }
        return null;
    }

    private static LocalDateTime reminderInstant(Task task) {
        if (task.getDueTime() == null) {
            return null;
        }
        LocalTime rt = task.getRemindTime() != null ? task.getRemindTime() : LocalTime.of(9, 0);
        return LocalDateTime.of(task.getDueTime().toLocalDate(), rt);
    }

    // 过滤不应弹出的提醒
    private boolean shouldSkipReminder(Task task, LocalDateTime at) {
        if (task == null || at == null || task.isCompleted()) {
            return true;
        }

        // 新建任务时，提醒时间本来就早于创建时间：这种不弹
        LocalDateTime createdAt = task.getCreatedAt();
        if (createdAt != null && createdAt.isAfter(at)) {
            return true;
        }

        return false;
    }

    private boolean alreadyRemindedFor(String taskId, LocalDateTime instant) {
        if (taskId == null || instant == null) {
            return false;
        }
        return instant.equals(remindedForInstant.get(taskId));
    }
}