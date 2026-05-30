package com.auction.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

import java.util.function.Consumer;

public class ClientApp extends Application {

    private static Stage primaryStage;

    @Override
    public void start(Stage stage) throws Exception {
        primaryStage = stage;

        Parent root = FXMLLoader.load(getClass().getResource("/fxml/login.fxml"));
        Scene scene = new Scene(root,920,600);

        var css = getClass().getResource("/css/style.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());

        stage.setTitle("Hệ thống Đấu giá Trực tuyến");
        stage.setScene(scene);
        stage.show();
    }

    public static void switchScene(String fxmlFile) {
        try {
            Parent root = FXMLLoader.load(ClientApp.class.getResource("/fxml/" + fxmlFile));
            primaryStage.getScene().setRoot(root);
        } catch (Exception e) {
            e.printStackTrace();
            showError("Không thể chuyển màn hình: " + e.getMessage());
        }
    }

    public static void switchSceneWithData(String fxmlFile, Consumer<Object> setupCtrl) {
        try {
            FXMLLoader loader = new FXMLLoader(ClientApp.class.getResource("/fxml/" + fxmlFile));
            Parent root = loader.load();
            Object ctrl = loader.getController();
            setupCtrl.accept(ctrl);
            primaryStage.getScene().setRoot(root);
        } catch (Exception e) {
            e.printStackTrace();
            showError("Không thể chuyển màn hình: " + e.getMessage());
        }
    }

    public static void showError(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle("Lỗi");
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    public static void showInfo(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Thông báo");
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
