package com.auction.client.util;

import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.shape.SVGPath;

/** Wire một cặp PasswordField + TextField với ToggleButton hiển thị/ẩn mật khẩu. */
public final class PasswordToggle {

    private static final String EYE_OPEN =
            "M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5"
            + "C21.27 7.61 17 4.5 12 4.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5"
            + "-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z";

    private static final String EYE_OFF =
            "M12 7c2.76 0 5 2.24 5 5 0 .65-.13 1.26-.36 1.83l2.92 2.92c1.51-1.26 2.7-2.89"
            + " 3.43-4.75C21.27 7.61 17 4.5 12 4.5c-1.4 0-2.74.25-3.98.7l2.16 2.16C10.74 7.13"
            + " 11.35 7 12 7zM2 4.27l2.28 2.28.46.46C3.08 8.3 1.78 10.02 1 12c1.73 4.39 6 7.5"
            + " 11 7.5 1.55 0 3.03-.3 4.38-.84l.42.42L19.73 22 21 20.73 3.27 3 2 4.27zM7.53"
            + " 9.8l1.55 1.55c-.05.21-.08.43-.08.65 0 1.66 1.34 3 3 3 .22 0 .44-.03.65-.08"
            + "l1.55 1.55c-.67.33-1.41.53-2.2.53-2.76 0-5-2.24-5-5 0-.79.2-1.53.53-2.2zM11.84"
            + " 9.02l3.15 3.15.02-.16c0-1.66-1.34-3-3-3l-.17.01z";

    private PasswordToggle() {}

    public static void bind(PasswordField password, TextField visible,
                            ToggleButton toggle, SVGPath icon) {
        visible.textProperty().bindBidirectional(password.textProperty());
        visible.managedProperty().bind(toggle.selectedProperty());
        visible.visibleProperty().bind(toggle.selectedProperty());
        password.managedProperty().bind(toggle.selectedProperty().not());
        password.visibleProperty().bind(toggle.selectedProperty().not());
        toggle.selectedProperty().addListener((o, was, isNow) ->
                icon.setContent(isNow ? EYE_OFF : EYE_OPEN));
    }
}
