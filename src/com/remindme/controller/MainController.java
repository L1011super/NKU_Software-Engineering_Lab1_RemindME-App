package com.remindme.controller;

import com.remindme.model.Priority;
import com.remindme.model.Task;
import com.remindme.service.ReminderService;
import com.remindme.service.TaskService;
import com.remindme.util.TaskDateFormats;
import com.remindme.view.MainView;
import com.remindme.MainApp;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import com.remindme.util.AppPaths;
import java.nio.file.Files;

public class MainController {
    private final Image icon = loadAppIcon();
    private static final int MAX_SUB_TASKS = 10;
    private static final int MAX_SUB_TASK_LENGTH = 100;

    private final MainView view;
    private final TaskService taskService;
    private final ReminderService reminderService;
    private FilteredList<Task> filteredTasks;
    private TaskFilter activeFilter = TaskFilter.ALL;
    private ListDisplayMode listDisplayMode = ListDisplayMode.REMINDER_ONLY;

    // 日历页状态
    private boolean calendarMode = false;
    private YearMonth currentYearMonth = YearMonth.now();
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy年M月");

    // 启动时逾期任务弹窗只显示一次
    private boolean startupOverdueDialogShown = false;

    public MainController() {
        this.view = new MainView();
        this.taskService = new TaskService();

        bindData();
        this.reminderService = new ReminderService(taskService.getTaskList());
        this.reminderService.start();
        registerEvents();
        initCellFactory();
        refreshCalendar();
    }

    private Image loadAppIcon() {
        var iconPath = AppPaths.getIconFile("AppIcon.png");
        if (Files.exists(iconPath)) {
            return new Image(iconPath.toUri().toString());
        }
        return null;
    }
    private void applyAlertIcon(Alert alert) {
        alert.setOnShown(e -> {
            if (icon == null) {
                return;
            }
            Window window = alert.getDialogPane().getScene() != null
                    ? alert.getDialogPane().getScene().getWindow()
                    : null;
            if (window instanceof Stage stage) {
                stage.getIcons().add(icon);
            }
        });
    }
    private void applyDialogIcon(Dialog<?> dialog) {
        dialog.setOnShown(e -> {
            if (icon == null) {
                return;
            }
            Window window = dialog.getDialogPane().getScene() != null
                    ? dialog.getDialogPane().getScene().getWindow()
                    : null;
            if (window instanceof Stage stage) {
                stage.getIcons().add(icon);
            }
        });
    }

    public Parent getView() {
        return view;
    }

    /** 关闭主窗口前调用，停止后台提醒调度线程。 */
    public void stopReminders() {
        reminderService.shutdown();
    }

    /** 绑定主窗口，提醒对话框以之为 owner，确保能置顶显示；并在下一帧重新排程（避免启动瞬间同步弹窗触发 FX 状态异常）。 */
    public void attachReminderOwner(Window owner) {
        reminderService.setOwnerWindow(owner);
        Platform.runLater(() -> {
            showStartupOverdueTasksDialog(); // 启动后统一展示逾期未完成任务和今日任务
            reminderService.reschedule();
        });
    }

    private void bindData() {
        filteredTasks = new FilteredList<>(taskService.getTaskList(), t -> true);
        view.getTaskListView().setItems(filteredTasks);

        // 任务列表变化时同步刷新日历页
        taskService.getTaskList().addListener((ListChangeListener<Task>) change -> refreshCalendar());
    }

    private void refreshReminders() {
        reminderService.reschedule();
    }

    private void registerEvents() {
        view.getFilterButton().setOnAction(e -> showFilterDialog());
        view.getAddButton().setOnAction(e -> showAddDialog());
        view.getTaskListView().addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (isClickOnEmptyArea(event)) {
                view.getTaskListView().getSelectionModel().clearSelection();
            }
        });
        view.getDeleteButton().setOnAction(e -> deleteSelectedTask());

        // 列表 / 日历切换
        view.getViewSwitchButton().setOnAction(e -> {
            calendarMode = view.getViewSwitchButton().isSelected();
            switchDisplayMode(calendarMode);
        });

        // 年份切换
        view.getPrevYearButton().setOnAction(e -> {
            currentYearMonth = currentYearMonth.minusYears(1);
            refreshCalendar();
        });

        view.getNextYearButton().setOnAction(e -> {
            currentYearMonth = currentYearMonth.plusYears(1);
            refreshCalendar();
        });

        // 月份切换
        view.getPrevMonthButton().setOnAction(e -> {
            currentYearMonth = currentYearMonth.minusMonths(1);
            refreshCalendar();
        });

        view.getNextMonthButton().setOnAction(e -> {
            currentYearMonth = currentYearMonth.plusMonths(1);
            refreshCalendar();
        });
    }

    private void initCellFactory() {
        view.getTaskListView().setCellFactory(param -> createTaskListCell(null, task -> {
            showEditDialog(task);
        }));
    }

    // 复用任务单元格样式：主列表和日历弹窗列表都走这一套
    private ListCell<Task> createTaskListCell(ListView<Task> hostListView, java.util.function.Consumer<Task> onDoubleClickTask) {
        return new ListCell<>() {
            {
                setOnMouseClicked(event -> {
                    if (event.getButton() == MouseButton.PRIMARY
                            && event.getClickCount() == 2
                            && !isEmpty()
                            && getItem() != null) {
                        if (onDoubleClickTask != null) {
                            onDoubleClickTask.accept(getItem());
                        }
                        event.consume(); // 防止事件继续冒泡到 ListView
                    }
                });
            }

            @Override
            protected void updateItem(Task task, boolean empty) {
                super.updateItem(task, empty);

                if (empty || task == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label title = new Label(task.getTitle());
                    title.getStyleClass().add("task-title-label");
                    title.setStyle(task.isCompleted() ? "-fx-strikethrough: true;" : "");
                    title.setWrapText(true);
                    title.setMaxWidth(Double.MAX_VALUE);

                    VBox infoCol = new VBox(4);
                    if (listDisplayMode == ListDisplayMode.REMINDER_ONLY) {
                        Label remindLine = new Label("提醒：" + TaskDateFormats.formatReminder(task));
                        remindLine.getStyleClass().add("task-meta-label");
                        remindLine.setWrapText(true);
                        remindLine.setMaxWidth(Double.MAX_VALUE);
                        infoCol.getChildren().add(remindLine);
                    } else {
                        Label dueLine = new Label("截止日期：" + TaskDateFormats.formatDateTime(task.getDueTime()));
                        Label createdLine = new Label("创建日期：" + TaskDateFormats.formatDateTime(task.getCreatedAt()));
                        Label doneLine = new Label("完成日期：" + TaskDateFormats.formatDateTime(task.getCompletedAt()));
                        dueLine.getStyleClass().add("task-meta-label");
                        createdLine.getStyleClass().add("task-meta-label");
                        doneLine.getStyleClass().add("task-times-label");
                        for (Label l : List.of(dueLine, createdLine, doneLine)) {
                            l.setWrapText(true);
                            l.setMaxWidth(Double.MAX_VALUE);
                        }
                        infoCol.getChildren().addAll(dueLine, createdLine, doneLine);
                    }

                    VBox left = new VBox(6, title, infoCol);
                    if (task.hasSubTasks()) {
                        left.getChildren().add(createSubTaskBox(task));
                    }
                    left.setAlignment(Pos.CENTER_LEFT);
                    left.setMinWidth(0);
                    left.setMaxWidth(Double.MAX_VALUE);
                    HBox.setHgrow(left, javafx.scene.layout.Priority.ALWAYS);

                    Circle priorityDot = new Circle(6);
                    priorityDot.setFill(getPriorityColor(task.getPriority()));

                    CheckBox completedBox = new CheckBox();
                    completedBox.setSelected(task.isCompleted());
                    completedBox.setOnAction(e -> {
                        handleParentCompletedToggle(task, completedBox.isSelected());
                        view.getTaskListView().refresh();
                        if (hostListView != null) {
                            hostListView.refresh();
                        }
                        refreshCalendar();
                    });

                    HBox row = new HBox(10, left, completedBox, priorityDot);
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.setPadding(new Insets(6, 10, 6, 10));

                    setText(null);
                    setGraphic(row);
                }
            }
        };
    }

    // 切换显示模式：列表页 / 日历页
    private void switchDisplayMode(boolean showCalendar) {
        view.getCalendarPage().setVisible(showCalendar);
        view.getCalendarPage().setManaged(showCalendar);

        view.getListPage().setVisible(!showCalendar);
        view.getListPage().setManaged(!showCalendar);

        view.getViewSwitchButton().setText(showCalendar ? "列表视图" : "日历视图");

        // 日历模式下不需要筛选和删除按钮
        view.getFilterButton().setDisable(showCalendar);
        view.getDeleteButton().setDisable(showCalendar);

        if (showCalendar) {
            refreshCalendar();
        }
    }

    // 刷新整个日历面板
    private void refreshCalendar() {
        view.getMonthLabel().setText(currentYearMonth.format(MONTH_FORMATTER));

        var grid = view.getCalendarGrid();
        grid.getChildren().clear();

        String[] weekDays = {"一", "二", "三", "四", "五", "六", "日"};
        for (int i = 0; i < weekDays.length; i++) {
            Label weekLabel = new Label(weekDays[i]);
            weekLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #6b7280;");
            weekLabel.setMinWidth(70);
            weekLabel.setPrefWidth(70);
            weekLabel.setAlignment(Pos.CENTER);
            grid.add(weekLabel, i, 0);
        }

        LocalDate firstDay = currentYearMonth.atDay(1);
        int lengthOfMonth = currentYearMonth.lengthOfMonth();
        int firstColumn = firstDay.getDayOfWeek().getValue() - 1;

        int row = 1;
        int col = firstColumn;

        for (int day = 1; day <= lengthOfMonth; day++) {
            LocalDate date = currentYearMonth.atDay(day);
            StackPane cell = createCalendarCell(date);
            grid.add(cell, col, row);

            col++;
            if (col > 6) {
                col = 0;
                row++;
            }
        }
    }

    // 创建单个日期格子
    private StackPane createCalendarCell(LocalDate date) {
        List<Task> tasksOfDay = getTasksByDate(date);

        boolean hasTask = !tasksOfDay.isEmpty();
        boolean allCompleted = hasTask && tasksOfDay.stream().allMatch(Task::isCompleted);

        Label dayLabel = new Label(String.valueOf(date.getDayOfMonth()));
        dayLabel.setFont(Font.font(16));
        dayLabel.setMinHeight(20);
        dayLabel.setPrefHeight(20);
        dayLabel.setStyle("-fx-text-fill: #111827;");

        // 小圆点：紫色=有未完成，灰色=全部完成，不显示=无任务
        Circle dot = new Circle(4);
        if (!hasTask) {
            dot.setOpacity(0);
        } else if (allCompleted) {
            dot.setFill(Color.web("#dfdfdf"));
        } else {
            dot.setFill(Color.web("#a855f7"));
        }

        // 这个占位很重要：保证所有日期数字高度一致、水平线对齐
        Region dotSpacer = new Region();
        dotSpacer.setMinHeight(8);
        dotSpacer.setPrefHeight(8);
        dotSpacer.setMaxHeight(8);

        VBox box = new VBox(6, dayLabel, dot, dotSpacer);
        box.setAlignment(Pos.TOP_CENTER);
        box.setPadding(new Insets(10, 6, 6, 6));
        box.setMinSize(76, 70);
        box.setPrefSize(76, 70);
        box.setMaxSize(76, 70);

        String borderColor = "#e5e7eb";
        String bgColor = date.equals(LocalDate.now()) ? "#f5f3ff" : "white";

        box.setStyle(
                "-fx-background-color: " + bgColor + ";"
                        + "-fx-background-radius: 12;"
                        + "-fx-border-color: " + borderColor + ";"
                        + "-fx-border-radius: 12;"
                        + "-fx-cursor: hand;"
        );

        box.setOnMouseClicked(e -> showTasksOfDate(date));

        return new StackPane(box);
    }

    // 点击某一天，弹出当天任务；风格复用主列表，双击可编辑
    private void showTasksOfDate(LocalDate date) {
        List<Task> tasks = getTasksByDate(date);

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("当天任务");
        applyDialogIcon(dialog);
        dialog.setHeaderText(date.toString());
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        if (tasks.isEmpty()) {
            Label emptyLabel = new Label("这天没有安排哦~");
            emptyLabel.setStyle("-fx-text-fill: gray; -fx-font-size: 14px;");

            StackPane emptyPane = new StackPane(emptyLabel);
            emptyPane.setPadding(new Insets(20));
            emptyPane.setPrefSize(420, 320);

            dialog.getDialogPane().setContent(emptyPane);
            dialog.showAndWait();
            return;
        }

        // 用 ListView 复用主列表风格
        ListView<Task> dayTaskListView = new ListView<>();
        dayTaskListView.getItems().setAll(tasks);

        // 这里不要再给 ListView 本身额外绑第二套双击事件
        // 只保留 cell 内部的双击处理，并且关闭旧弹窗后延后一帧再打开编辑框，避免空边框/重复弹窗
        dayTaskListView.setCellFactory(param -> createTaskListCell(dayTaskListView, task -> {
            dialog.close();
            Platform.runLater(() -> showEditDialog(task));
        }));

        dayTaskListView.getStyleClass().add("task-list-view");
        var taskListCss = getClass().getResource("/com/remindme/view/task-list.css");
        if (taskListCss != null) {
            dayTaskListView.getStylesheets().add(taskListCss.toExternalForm());
        }

        dayTaskListView.setPrefWidth(520);
        dayTaskListView.setPrefHeight(360);

        dialog.getDialogPane().setContent(dayTaskListView);
        dialog.getDialogPane().setPrefSize(560, 430);

        dialog.showAndWait();
    }

    private List<Task> getTasksByDate(LocalDate date) {
        if (date == null) {
            return List.of();
        }
        return taskService.getTaskList().stream()
                .filter(task -> task.getDueTime() != null)
                .filter(task -> task.getDueTime().toLocalDate().equals(date))
                .toList();
    }

    // 启动时弹出逾期未完成任务列表和今日任务列表，样式和主界面一致
    private void showStartupOverdueTasksDialog() {
        if (startupOverdueDialogShown) {
            return;
        }
        startupOverdueDialogShown = true;

        LocalDate today = LocalDate.now();

        List<Task> startupTasks = taskService.getTaskList().stream()
                .filter(task -> !task.isCompleted())
                .filter(task -> task.getDueTime() != null)
                .filter(task -> {
                    LocalDate dueDate = task.getDueTime().toLocalDate();
                    return dueDate.isBefore(today) || dueDate.equals(today);
                })
                .sorted(Comparator
                        .comparing((Task t) -> t.getDueTime() == null ? LocalDateTime.MAX : t.getDueTime())
                        .thenComparing(t -> t.getCreatedAt() == null ? LocalDateTime.MAX : t.getCreatedAt()))
                .toList();

        if (startupTasks.isEmpty()) {
            return;
        }

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("任务提醒");
        applyDialogIcon(dialog);
        dialog.setHeaderText("以下为逾期未完成和今日未完成任务");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        ListView<Task> startupTaskListView = new ListView<>();
        startupTaskListView.getItems().setAll(startupTasks);

        startupTaskListView.setCellFactory(param -> createTaskListCell(startupTaskListView, task -> {
            dialog.close();
            Platform.runLater(() -> showEditDialog(task));
        }));

        startupTaskListView.getStyleClass().add("task-list-view");
        var taskListCss = getClass().getResource("/com/remindme/view/task-list.css");
        if (taskListCss != null) {
            startupTaskListView.getStylesheets().add(taskListCss.toExternalForm());
        }

        startupTaskListView.setPrefWidth(520);
        startupTaskListView.setPrefHeight(360);

        dialog.getDialogPane().setContent(startupTaskListView);
        dialog.getDialogPane().setPrefSize(560, 430);

        dialog.showAndWait();
    }

    // 统一计算任务提醒时间点
    private LocalDateTime getReminderDateTime(Task task) {
        if (task == null || task.getDueTime() == null) {
            return null;
        }
        LocalTime remindTime = task.getRemindTime() != null ? task.getRemindTime() : LocalTime.of(9, 0);
        return LocalDateTime.of(task.getDueTime().toLocalDate(), remindTime);
    }

    private void showFilterDialog() {
        Dialog<TaskFilter> dialog = new Dialog<>();
        dialog.setTitle("筛选");
        applyDialogIcon(dialog);
        dialog.setHeaderText(null);

        ToggleGroup group = new ToggleGroup();
        VBox leftColumn = new VBox(12);
        leftColumn.getChildren().add(radioRow(group, TaskFilter.ALL, "默认", activeFilter));
        leftColumn.getChildren().add(sectionTitle("截止日期"));
        leftColumn.getChildren().add(radioRow(group, TaskFilter.DUE_PAST_INCOMPLETE, "已过期", activeFilter));
        leftColumn.getChildren().add(radioRow(group, TaskFilter.DUE_TODAY, "今天", activeFilter));
        leftColumn.getChildren().add(radioRow(group, TaskFilter.DUE_NEXT_WEEK, "未来一周", activeFilter));
        leftColumn.getChildren().add(radioRow(group, TaskFilter.DUE_NEXT_MONTH, "未来一个月", activeFilter));
        leftColumn.getChildren().add(radioRow(group, TaskFilter.DUE_LATER, "大于一个月", activeFilter));

        VBox rightColumn = new VBox(12);
        rightColumn.getChildren().add(sectionTitle("优先级"));
        rightColumn.getChildren().add(radioRow(group, TaskFilter.PRIORITY_HIGH, "高", activeFilter));
        rightColumn.getChildren().add(radioRow(group, TaskFilter.PRIORITY_MEDIUM, "中", activeFilter));
        rightColumn.getChildren().add(radioRow(group, TaskFilter.PRIORITY_LOW, "低", activeFilter));
        rightColumn.getChildren().add(sectionTitle("完成状况"));
        rightColumn.getChildren().add(radioRow(group, TaskFilter.STATUS_COMPLETED, "已完成", activeFilter));
        rightColumn.getChildren().add(radioRow(group, TaskFilter.STATUS_INCOMPLETE, "未完成", activeFilter));

        leftColumn.setFillWidth(true);
        rightColumn.setFillWidth(true);
        HBox.setHgrow(leftColumn, javafx.scene.layout.Priority.ALWAYS);
        HBox.setHgrow(rightColumn, javafx.scene.layout.Priority.ALWAYS);

        HBox sections = new HBox(36, leftColumn, rightColumn);
        sections.setPadding(new Insets(10, 20, 10, 20));
        sections.setAlignment(Pos.TOP_LEFT);

        dialog.getDialogPane().setContent(sections);
        dialog.getDialogPane().setPrefWidth(300);

        ButtonType applyType = new ButtonType("应用", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(applyType, ButtonType.CANCEL);

        dialog.setResultConverter(bt -> {
            if (bt != applyType) {
                return null;
            }
            Toggle t = group.getSelectedToggle();
            if (t == null || !(t instanceof RadioButton rb)) {
                return TaskFilter.ALL;
            }
            Object u = rb.getUserData();
            return u instanceof TaskFilter tf ? tf : TaskFilter.ALL;
        });

        dialog.showAndWait().ifPresent(this::applyTaskFilter);
    }

    private static Label sectionTitle(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-weight: bold;");
        return l;
    }

    private static RadioButton radioRow(ToggleGroup group, TaskFilter value, String text, TaskFilter current) {
        RadioButton rb = new RadioButton(text);
        rb.setToggleGroup(group);
        rb.setUserData(value);
        if (value == current) {
            rb.setSelected(true);
        }
        return rb;
    }

    private void applyTaskFilter(TaskFilter filter) {
        activeFilter = filter;
        listDisplayMode = (filter == TaskFilter.STATUS_COMPLETED || filter == TaskFilter.STATUS_INCOMPLETE)
                ? ListDisplayMode.COMPLETION_DETAIL
                : ListDisplayMode.REMINDER_ONLY;
        filteredTasks.setPredicate(buildPredicate(filter));
        view.getTaskListView().refresh();
    }

    private static Predicate<Task> buildPredicate(TaskFilter f) {
        LocalDate today = LocalDate.now();
        return switch (f) {
            case ALL -> t -> true;
            case PRIORITY_HIGH -> t -> t.getPriority() == Priority.HIGH;
            case PRIORITY_MEDIUM -> t -> t.getPriority() == Priority.MEDIUM;
            case PRIORITY_LOW -> t -> t.getPriority() == Priority.LOW;
            case DUE_PAST_INCOMPLETE -> t -> {
                LocalDate d = dueDateOf(t);
                return d != null && d.isBefore(today) && !t.isCompleted();
            };
            case DUE_TODAY -> t -> {
                LocalDate d = dueDateOf(t);
                return d != null && d.equals(today);
            };
            case DUE_NEXT_WEEK -> t -> {
                LocalDate d = dueDateOf(t);
                return d != null && d.isAfter(today) && !d.isAfter(today.plusDays(7));
            };
            case DUE_NEXT_MONTH -> t -> {
                LocalDate d = dueDateOf(t);
                return d != null && d.isAfter(today.plusDays(7)) && !d.isAfter(today.plusDays(30));
            };
            case DUE_LATER -> t -> {
                LocalDate d = dueDateOf(t);
                return d != null && d.isAfter(today.plusDays(30));
            };
            case STATUS_COMPLETED -> Task::isCompleted;
            case STATUS_INCOMPLETE -> t -> !t.isCompleted();
        };
    }

    private static LocalDate dueDateOf(Task task) {
        return task.getDueTime() == null ? null : task.getDueTime().toLocalDate();
    }

    private enum TaskFilter {
        ALL,
        PRIORITY_HIGH,
        PRIORITY_MEDIUM,
        PRIORITY_LOW,
        DUE_PAST_INCOMPLETE,
        DUE_TODAY,
        DUE_NEXT_WEEK,
        DUE_NEXT_MONTH,
        DUE_LATER,
        STATUS_COMPLETED,
        STATUS_INCOMPLETE
    }

    private enum ListDisplayMode {
        REMINDER_ONLY,
        COMPLETION_DETAIL
    }

    private void showAddDialog() {
        Dialog<Task> dialog = new Dialog<>();
        dialog.setTitle("新增任务");
        dialog.setHeaderText("请输入任务信息");
        applyDialogIcon(dialog);
        ButtonType confirmButtonType = new ButtonType("确认", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(confirmButtonType, ButtonType.CANCEL);

        TextField titleField = new TextField();
        titleField.setPromptText("标题");

        TextArea descriptionArea = new TextArea();
        descriptionArea.setPromptText("描述");
        descriptionArea.setWrapText(true);
        descriptionArea.setPrefRowCount(4);

        DatePicker datePicker = new DatePicker(LocalDate.now());

        Spinner<Integer> hourSpinner = new Spinner<>(0, 23, 9);
        hourSpinner.setEditable(true);
        Spinner<Integer> minuteSpinner = new Spinner<>(0, 59, 0);
        minuteSpinner.setEditable(true);
        HBox remindTimeBar = new HBox(8, new Label("提醒时间"), hourSpinner, new Label(":"), minuteSpinner);
        remindTimeBar.setAlignment(Pos.CENTER_LEFT);

        ComboBox<Priority> priorityBox = new ComboBox<>();
        priorityBox.getItems().addAll(Priority.values());
        priorityBox.setValue(Priority.MEDIUM);

        RadioButton addSubTaskToggle = new RadioButton("添加子任务");
        VBox subTaskFieldsBox = new VBox(10);
        List<SubTaskFieldRow> subTaskRows = new ArrayList<>();
        Button addSubTaskButton = new Button("+");
        styleRoundSymbolButton(addSubTaskButton, "+", 32);

        addSubTaskToggle.selectedProperty().addListener((obs, oldVal, newVal) -> {
            subTaskFieldsBox.setManaged(newVal);
            subTaskFieldsBox.setVisible(newVal);
            addSubTaskButton.setManaged(newVal);
            addSubTaskButton.setVisible(newVal);

            if (newVal && subTaskRows.isEmpty()) {
                addSubTaskFieldRow(subTaskFieldsBox, subTaskRows, addSubTaskButton, "", false);
            } else if (!newVal) {
                subTaskFieldsBox.getChildren().clear();
                subTaskRows.clear();
                addSubTaskButton.setDisable(false);
            }
        });

        addSubTaskButton.setOnAction(e -> {
            if (subTaskRows.size() >= MAX_SUB_TASKS) {
                showWarning("子任务最多添加10个");
                addSubTaskButton.setDisable(true);
                return;
            }
            addSubTaskFieldRow(subTaskFieldsBox, subTaskRows, addSubTaskButton, "", false);
        });

        subTaskFieldsBox.setManaged(false);
        subTaskFieldsBox.setVisible(false);
        addSubTaskButton.setManaged(false);
        addSubTaskButton.setVisible(false);

        VBox content = new VBox(10,
                new Label("标题"), titleField,
                new Label("描述"), descriptionArea,
                new Label("截止日期"), datePicker,
                remindTimeBar,
                new Label("优先级"), priorityBox,
                addSubTaskToggle,
                subTaskFieldsBox,
                addSubTaskButton
        );
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setPrefViewportWidth(420);
        scrollPane.setPrefViewportHeight(420);
        dialog.getDialogPane().setPrefSize(480, 520);
        dialog.getDialogPane().setContent(scrollPane);

        Button okButton = (Button) dialog.getDialogPane().lookupButton(confirmButtonType);
        okButton.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            String title = titleField.getText() == null ? "" : titleField.getText().trim();
            String desc = descriptionArea.getText() == null ? "" : descriptionArea.getText();

            if (title.isEmpty()) {
                showWarning("标题不能为空");
                e.consume();
                return;
            }
            if (title.length() > 50) {
                showWarning("字数超限：标题最多50字");
                e.consume();
                return;
            }
            if (desc.length() > 300) {
                showWarning("字数超限：正文最多300字");
                e.consume();
                return;
            }
            if (!validateSubTaskRows(subTaskRows)) {
                e.consume();
                return;
            }
        });

        dialog.setResultConverter(buttonType -> {
            if (buttonType == confirmButtonType) {
                String title = titleField.getText().trim();
                LocalDateTime dueTime = LocalDateTime.of(datePicker.getValue(), LocalTime.of(23, 59));
                LocalTime remindTime = LocalTime.of(hourSpinner.getValue(), minuteSpinner.getValue());
                Task task = new Task(title, descriptionArea.getText(), dueTime, remindTime, priorityBox.getValue());
                task.setSubTasks(collectSubTasksFromRows(subTaskRows));
                return task;
            }
            return null;
        });

        Optional<Task> result = dialog.showAndWait();
        result.ifPresent(task -> {
            taskService.addTask(task);
            refreshReminders();
            refreshCalendar();
        });
    }

    private void showEditDialog() {
        Task selected = view.getTaskListView().getSelectionModel().getSelectedItem();
        showEditDialog(selected);
    }

    private void showEditDialog(Task selected) {
        if (selected == null) {
            showWarning("请先选择一个任务进行编辑");
            return;
        }

        Dialog<Task> dialog = new Dialog<>();
        dialog.setTitle("编辑任务");

        dialog.setHeaderText("修改任务信息");
        applyDialogIcon(dialog);
        ButtonType confirmButtonType = new ButtonType("保存", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(confirmButtonType, ButtonType.CANCEL);

        TextField titleField = new TextField(selected.getTitle());
        TextArea descriptionArea = new TextArea(selected.getDescription());
        descriptionArea.setWrapText(true);
        descriptionArea.setPrefRowCount(4);

        DatePicker datePicker = new DatePicker(
                selected.getDueTime() != null ? selected.getDueTime().toLocalDate() : LocalDate.now()
        );

        LocalTime existingRemind = selected.getRemindTime() != null ? selected.getRemindTime() : LocalTime.of(9, 0);
        Spinner<Integer> hourSpinner = new Spinner<>(0, 23, existingRemind.getHour());
        hourSpinner.setEditable(true);
        Spinner<Integer> minuteSpinner = new Spinner<>(0, 59, existingRemind.getMinute());
        minuteSpinner.setEditable(true);
        HBox remindTimeBar = new HBox(8, new Label("提醒时间"), hourSpinner, new Label(":"), minuteSpinner);
        remindTimeBar.setAlignment(Pos.CENTER_LEFT);

        ComboBox<Priority> priorityBox = new ComboBox<>();
        priorityBox.getItems().addAll(Priority.values());
        priorityBox.setValue(selected.getPriority());

        VBox subTaskFieldsBox = new VBox(10);
        List<SubTaskFieldRow> subTaskRows = new ArrayList<>();
        Button addSubTaskButton = new Button("+");
        styleRoundSymbolButton(addSubTaskButton, "+", 32);
        HBox subTaskToolbar = new HBox(10, new Label("子任务（可选，最多10条）"), addSubTaskButton);
        subTaskToolbar.setAlignment(Pos.CENTER_LEFT);

        if (selected.hasSubTasks()) {
            for (Task.SubTask st : selected.getSubTasks()) {
                String txt = st.getTitle() == null ? "" : st.getTitle();
                addSubTaskFieldRow(subTaskFieldsBox, subTaskRows, addSubTaskButton, txt, st.isCompleted());
            }
        }

        addSubTaskButton.setOnAction(e -> {
            if (subTaskRows.size() >= MAX_SUB_TASKS) {
                showWarning("子任务最多添加10个");
                addSubTaskButton.setDisable(true);
                return;
            }
            addSubTaskFieldRow(subTaskFieldsBox, subTaskRows, addSubTaskButton, "", false);
        });

        VBox content = new VBox(10,
                new Label("标题"), titleField,
                new Label("描述"), descriptionArea,
                new Label("截止日期"), datePicker,
                remindTimeBar,
                new Label("优先级"), priorityBox,
                subTaskToolbar,
                subTaskFieldsBox
        );
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setPrefViewportWidth(420);
        scrollPane.setPrefViewportHeight(420);
        dialog.getDialogPane().setPrefSize(480, 520);
        dialog.getDialogPane().setContent(scrollPane);

        Button okButton = (Button) dialog.getDialogPane().lookupButton(confirmButtonType);
        okButton.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            String title = titleField.getText() == null ? "" : titleField.getText().trim();
            String desc = descriptionArea.getText() == null ? "" : descriptionArea.getText();

            if (title.isEmpty()) {
                showWarning("标题不能为空");
                e.consume();
                return;
            }
            if (title.length() > 50) {
                showWarning("字数超限：标题最多50字");
                e.consume();
                return;
            }
            if (desc.length() > 300) {
                showWarning("字数超限：正文最多300字");
                e.consume();
                return;
            }
            if (!validateSubTaskRows(subTaskRows)) {
                e.consume();
                return;
            }
        });

        dialog.setResultConverter(buttonType -> {
            if (buttonType == confirmButtonType) {
                String title = titleField.getText().trim();
                LocalDateTime dueTime = LocalDateTime.of(datePicker.getValue(), LocalTime.of(23, 59));
                LocalTime remindTime = LocalTime.of(hourSpinner.getValue(), minuteSpinner.getValue());
                Task newTask = new Task(title, descriptionArea.getText(), dueTime, remindTime, priorityBox.getValue());
                newTask.setSubTasks(collectSubTasksFromRows(subTaskRows));
                if (!newTask.hasSubTasks()) {
                    newTask.setCompleted(selected.isCompleted());
                }
                return newTask;
            }
            return null;
        });

        Optional<Task> result = dialog.showAndWait();
        result.ifPresent(task -> {
            taskService.updateTask(selected, task);
            refreshReminders();
            view.getTaskListView().refresh();
            refreshCalendar();
        });
    }

    private void deleteSelectedTask() {
        Task selected = view.getTaskListView().getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("请先选择一个任务进行删除");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("删除确认");
        confirm.setHeaderText(null);
        confirm.setContentText("确定要删除任务「" + selected.getTitle() + "」吗？此操作不可撤销。");
        applyAlertIcon(confirm);
        Optional<ButtonType> answer = confirm.showAndWait();
        if (answer.isEmpty() || answer.get() != ButtonType.OK) {
            return;
        }
        taskService.removeTask(selected);
        refreshReminders();
        refreshCalendar();
    }

    private Color getPriorityColor(Priority priority) {
        if (priority == null) {
            return Color.GRAY;
        }
        return switch (priority) {
            case HIGH -> Color.RED;
            case MEDIUM -> Color.GOLD;
            case LOW -> Color.DODGERBLUE;
        };
    }

    private VBox createSubTaskBox(Task task) {
        VBox subTaskBox = new VBox(6);
        subTaskBox.setPadding(new Insets(6, 0, 0, 50));
        subTaskBox.setMaxWidth(Double.MAX_VALUE);

        int index = 1;
        for (Task.SubTask subTask : task.getSubTasks()) {
            Label subTaskLabel = new Label("子任务" + index + "： " + subTask.getTitle());
            subTaskLabel.getStyleClass().add("task-subtask-label");
            subTaskLabel.setWrapText(true);
            subTaskLabel.setMinWidth(0);
            subTaskLabel.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(subTaskLabel, javafx.scene.layout.Priority.ALWAYS);
            if (subTask.isCompleted()) {
                subTaskLabel.getStyleClass().add("subtask-strikethrough");
            }

            RadioButton subTaskDoneButton = new RadioButton();
            subTaskDoneButton.setMnemonicParsing(false);
            subTaskDoneButton.setSelected(subTask.isCompleted());
            subTaskDoneButton.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            subTaskDoneButton.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            subTaskDoneButton.setOnAction(e -> {
                taskService.setSubTaskCompleted(task, subTask, subTaskDoneButton.isSelected());
                refreshReminders();
                view.getTaskListView().refresh();
                refreshCalendar();
            });

            HBox row = new HBox(10, subTaskLabel, subTaskDoneButton);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setMaxWidth(Double.MAX_VALUE);
            subTaskBox.getChildren().add(row);
            index++;
        }
        return subTaskBox;
    }

    private void handleParentCompletedToggle(Task task, boolean targetCompleted) {
        if (!task.hasSubTasks()) {
            taskService.setCompleted(task, targetCompleted);
            refreshReminders();
            refreshCalendar();
            return;
        }

        if (targetCompleted && !task.areAllSubTasksCompleted()) {
            ButtonType yesButton = new ButtonType("是", ButtonBar.ButtonData.YES);
            ButtonType noButton = new ButtonType("否", ButtonBar.ButtonData.NO);
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("确认完成");
            alert.setHeaderText(null);
            alert.setContentText("是否已经完成所有子任务？");
            alert.getButtonTypes().setAll(yesButton, noButton);
            applyAlertIcon(alert);
            Optional<ButtonType> result = alert.showAndWait();
            if (result.isEmpty() || result.get() != yesButton) {
                return;
            }
        }

        taskService.setCompleted(task, targetCompleted);
        refreshReminders();
        refreshCalendar();
    }

    private void styleRoundSymbolButton(Button b, String symbol, int size) {
        b.setText(symbol);
        b.setMnemonicParsing(false);
        double s = size;
        b.setMinSize(s, s);
        b.setMaxSize(s, s);
        boolean isPlus = "+".equals(symbol);
        String bg = isPlus ? "#e3f2fd" : "#eeeeee";
        b.setStyle(String.format(
                "-fx-background-color: %s; -fx-background-radius: %.0f; "
                        + "-fx-min-width: %.0fpx; -fx-min-height: %.0fpx; "
                        + "-fx-max-width: %.0fpx; -fx-max-height: %.0fpx; -fx-padding: 0; "
                        + "-fx-font-weight: bold; -fx-cursor: hand; -fx-text-fill: black;",
                bg, s / 2, s, s, s, s));
    }

    private void addSubTaskFieldRow(
            VBox subTaskFieldsBox,
            List<SubTaskFieldRow> rows,
            Button addSubTaskButton,
            String initialText,
            boolean initialCompleted) {
        if (rows.size() >= MAX_SUB_TASKS) {
            addSubTaskButton.setDisable(true);
            return;
        }
        SubTaskFieldRow row = new SubTaskFieldRow();
        row.completed = initialCompleted;
        if (initialText != null && !initialText.isEmpty()) {
            row.textArea.setText(initialText);
        }
        row.textArea.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.length() > MAX_SUB_TASK_LENGTH) {
                row.textArea.setText(oldVal);
            }
        });
        styleRoundSymbolButton(row.removeButton, "\u2212", 28);
        row.removeButton.setOnAction(e -> {
            subTaskFieldsBox.getChildren().remove(row.container);
            rows.remove(row);
            renumberSubTaskFieldRows(rows);
            addSubTaskButton.setDisable(rows.size() >= MAX_SUB_TASKS);
        });

        HBox line = new HBox(8, row.textArea, row.removeButton);
        line.setAlignment(Pos.CENTER_LEFT);
        line.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(row.textArea, javafx.scene.layout.Priority.ALWAYS);
        row.container.getChildren().addAll(row.indexLabel, line);
        subTaskFieldsBox.getChildren().add(row.container);
        rows.add(row);
        renumberSubTaskFieldRows(rows);
        addSubTaskButton.setDisable(rows.size() >= MAX_SUB_TASKS);
    }

    private void renumberSubTaskFieldRows(List<SubTaskFieldRow> rows) {
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).indexLabel.setText("子任务" + (i + 1));
        }
    }

    private boolean validateSubTaskRows(List<SubTaskFieldRow> rows) {
        for (SubTaskFieldRow r : rows) {
            String text = r.textArea.getText() == null ? "" : r.textArea.getText().trim();
            if (text.length() > MAX_SUB_TASK_LENGTH) {
                showWarning("子任务字数超限：每项最多100字");
                return false;
            }
        }
        return true;
    }

    private List<Task.SubTask> collectSubTasksFromRows(List<SubTaskFieldRow> rows) {
        List<Task.SubTask> subTasks = new ArrayList<>();
        for (SubTaskFieldRow r : rows) {
            String text = r.textArea.getText() == null ? "" : r.textArea.getText().trim();
            if (!text.isEmpty()) {
                Task.SubTask s = new Task.SubTask(text);
                s.setCompleted(r.completed);
                subTasks.add(s);
            }
        }
        return subTasks;
    }

    private static final class SubTaskFieldRow {
        private final VBox container = new VBox(4);
        private final Label indexLabel = new Label();
        private final TextArea textArea = new TextArea();
        private final Button removeButton = new Button();
        private boolean completed;

        SubTaskFieldRow() {
            textArea.setPromptText("请输入子任务内容");
            textArea.setWrapText(true);
            textArea.setPrefRowCount(2);
            textArea.setMinWidth(80);
            textArea.setMaxWidth(Double.MAX_VALUE);
        }
    }

    private boolean isClickOnEmptyArea(MouseEvent event) {
        Object target = event.getTarget();
        if (!(target instanceof Node node)) {
            return false;
        }

        while (node != null) {
            if (node instanceof ListCell<?> cell) {
                return cell.isEmpty();
            }
            node = node.getParent();
        }
        return true;
    }

    private void showWarning(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        applyAlertIcon(alert);

        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
