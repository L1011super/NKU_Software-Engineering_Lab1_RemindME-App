package com.remindme;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.scene.image.Image;
import com.remindme.controller.MainController;
import com.remindme.util.AppPaths;
import java.nio.file.Files;

public class MainApp extends Application {
    @Override
    public void start(Stage primaryStage) {
        MainController controller = new MainController();
        var appIconPath = AppPaths.getIconFile("AppIcon.png");
        if (Files.exists(appIconPath)) {
            Image appIcon = new Image(appIconPath.toUri().toString());
            primaryStage.getIcons().add(appIcon);
        }        Scene scene = new Scene(controller.getView(), 900, 660);
        primaryStage.setTitle("RemindMe");
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> controller.stopReminders());
        primaryStage.show();
        controller.attachReminderOwner(primaryStage);
    }

    public static void main(String[] args) {
        launch(args);
    }

}