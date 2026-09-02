package com.bloodlink.util;

import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.util.Callback;

import java.util.Locale;

public final class ChipTableCells {
    private ChipTableCells() {
    }

    public static <S, T> Callback<TableColumn<S, T>, TableCell<S, T>> forValues() {
        return column -> new TableCell<>() {
            private final Label chip = new Label();

            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                String text = item.toString();
                chip.setText(text);
                chip.getStyleClass().setAll("chip", "chip-" + normalize(text));
                setText(null);
                setGraphic(chip);
            }
        };
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replace('+', 'p')
                .replace('-', 'n')
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }
}
