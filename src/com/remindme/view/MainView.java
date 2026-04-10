package com.remindme.view;

import com.remindme.model.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import com.remindme.util.AppPaths;
import java.nio.file.Files;

public class MainView extends BorderPane {
    private final ListView<Task> taskListView = new ListView<>();

    private final Button filterButton = new Button("筛选");
    private final Button addButton = new Button("+");
    private final Button deleteButton = new Button();

    // 右上角视图切换按钮：列表 / 日历
    private final ToggleButton viewSwitchButton = new ToggleButton("日历视图");

    // 中央区域改为堆叠容器，里面放列表页和日历页
    private final StackPane contentStack = new StackPane();

    private final VBox listPage = new VBox();
    private final BorderPane calendarPage = new BorderPane();

    // 日历页控件
    private final GridPane calendarGrid = new GridPane();
    private final Label monthLabel = new Label();

    // 月份切换
    private final Button prevMonthButton = new Button("<");
    private final Button nextMonthButton = new Button(">");

    // 年份切换
    private final Button prevYearButton = new Button("<<");
    private final Button nextYearButton = new Button(">>");

    public MainView() {
        createLayout();
    }

    private void createLayout() {
        taskListView.getStyleClass().add("task-list-view");
        var taskListCss = getClass().getResource("task-list.css");
        if (taskListCss != null) {
            taskListView.getStylesheets().add(taskListCss.toExternalForm());
        } else {
            System.err.println("task-list.css not found on classpath (expected next to MainView.class).");
        }
        Label emptyHint = new Label("什么也没有哦~");
        emptyHint.setStyle("-fx-text-fill: #9aa0a6; -fx-font-size: 14px;");
        VBox emptyBox = new VBox(emptyHint);
        emptyBox.setAlignment(Pos.TOP_CENTER);
        emptyBox.setPadding(new Insets(20, 0, 0, 0));
        taskListView.setPlaceholder(emptyBox);

        Label titleLabel = new Label("Remindme！Be On Time Everyday！ :-)");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        filterButton.setMinSize(72, 34);
        filterButton.setPrefSize(72, 34);
        filterButton.setStyle(
                "-fx-background-color: #e0e0e0; -fx-background-radius: 4; "
                        + "-fx-border-color: #bdbdbd; -fx-border-radius: 4; -fx-cursor: hand;"
        );

        // 视图切换按钮，风格尽量接近拨片开关
        viewSwitchButton.setMinSize(92, 34);
        viewSwitchButton.setPrefSize(92, 34);
        viewSwitchButton.setStyle(
                "-fx-background-color: #f3e8ff; "
                        + "-fx-background-radius: 18; "
                        + "-fx-border-color: #c084fc; "
                        + "-fx-border-radius: 18; "
                        + "-fx-cursor: hand; "
                        + "-fx-font-weight: bold;"
        );

        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);

        HBox topBar = new HBox(10, titleLabel, topSpacer, filterButton, viewSwitchButton);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(15));

        double size = 45;

        // add button
        addButton.setPrefSize(size, size);
        addButton.setMinSize(size, size);
        addButton.setMaxSize(size, size);
        addButton.setText("");

        var addPath = AppPaths.getIconFile("add.png");
        if (Files.exists(addPath)) {
            Image addImage = new Image(addPath.toUri().toString());
            ImageView addIcon = new ImageView(addImage);
            addIcon.setFitWidth(20);
            addIcon.setFitHeight(20);
            addIcon.setPreserveRatio(true);
            addButton.setGraphic(addIcon);
        } else {
            addButton.setText("+");
        }
        addButton.setStyle(
                "-fx-background-color: #99CCFF; " +
                        "-fx-background-radius: " + (size / 2) + "; " +
                        "-fx-cursor: hand; " +
                        "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 5, 0, 0, 2);"
        );

        // delete button
        deleteButton.setPrefSize(size, size);
        deleteButton.setMinSize(size, size);
        deleteButton.setMaxSize(size, size);
        deleteButton.setText("");

        var deletePath = AppPaths.getIconFile("delete.png");
        if (Files.exists(deletePath)) {
            Image deleteImage = new Image(deletePath.toUri().toString());
            ImageView deleteIcon = new ImageView(deleteImage);
            deleteIcon.setFitWidth(20);
            deleteIcon.setFitHeight(20);
            deleteIcon.setPreserveRatio(true);
            deleteButton.setGraphic(deleteIcon);
        } else {
            deleteButton.setText("删");
        }

        deleteButton.setStyle(
                "-fx-background-color: #FF9999; " +
                        "-fx-background-radius: " + (size / 2) + "; " +
                        "-fx-cursor: hand; " +
                        "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 5, 0, 0, 2);"
        );

        // spacer: push buttons to the right
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox buttonBar = new HBox(15, spacer, addButton, deleteButton);
        buttonBar.setPadding(new Insets(10));
        buttonBar.setAlignment(Pos.CENTER_RIGHT);

        // 列表页
        VBox listContent = new VBox(10, taskListView, buttonBar);
        listContent.setPadding(new Insets(15));
        VBox.setVgrow(taskListView, Priority.ALWAYS);
        listPage.getChildren().setAll(listContent);

        // 日历页
        buildCalendarPage();

        // 默认显示列表页，隐藏日历页
        contentStack.getChildren().addAll(listPage, calendarPage);
        calendarPage.setVisible(false);
        calendarPage.setManaged(false);

        setTop(topBar);
        setCenter(contentStack);
    }

    // 构建日历页基础框架，具体日期由 controller 动态渲染
    private void buildCalendarPage() {
        monthLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        prevYearButton.setStyle(
                "-fx-background-color: #ede9fe; -fx-background-radius: 16; -fx-cursor: hand;"
        );
        prevMonthButton.setStyle(
                "-fx-background-color: #ede9fe; -fx-background-radius: 16; -fx-cursor: hand;"
        );
        nextMonthButton.setStyle(
                "-fx-background-color: #ede9fe; -fx-background-radius: 16; -fx-cursor: hand;"
        );
        nextYearButton.setStyle(
                "-fx-background-color: #ede9fe; -fx-background-radius: 16; -fx-cursor: hand;"
        );

        // 年份与月份都可以切换
        HBox monthBar = new HBox(12, prevYearButton, prevMonthButton, monthLabel, nextMonthButton, nextYearButton);
        monthBar.setAlignment(Pos.CENTER);
        monthBar.setPadding(new Insets(15, 15, 10, 15));

        calendarGrid.setHgap(10);
        calendarGrid.setVgap(10);
        calendarGrid.setPadding(new Insets(10, 15, 15, 15));
        calendarGrid.setAlignment(Pos.TOP_CENTER);

        calendarPage.setTop(monthBar);
        calendarPage.setCenter(calendarGrid);
    }

    public ListView<Task> getTaskListView() {
        return taskListView;
    }

    public Button getFilterButton() {
        return filterButton;
    }

    public Button getAddButton() {
        return addButton;
    }

    public Button getDeleteButton() {
        return deleteButton;
    }

    public ToggleButton getViewSwitchButton() {
        return viewSwitchButton;
    }

    public VBox getListPage() {
        return listPage;
    }

    public BorderPane getCalendarPage() {
        return calendarPage;
    }

    public GridPane getCalendarGrid() {
        return calendarGrid;
    }

    public Label getMonthLabel() {
        return monthLabel;
    }

    public Button getPrevMonthButton() {
        return prevMonthButton;
    }

    public Button getNextMonthButton() {
        return nextMonthButton;
    }

    public Button getPrevYearButton() {
        return prevYearButton;
    }

    public Button getNextYearButton() {
        return nextYearButton;
    }
}
