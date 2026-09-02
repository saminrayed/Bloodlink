package com.bloodlink.util;

import javafx.concurrent.Task;

import java.util.function.Consumer;

/**
 * Runs blocking work (database calls) off the JavaFX Application Thread and
 * delivers the result back onto it. Used by every dashboard's auto-refresh
 * and any other UI-triggered database read, so periodic polling and
 * search-as-you-type never stall the UI.
 * <p>
 * This replaces the previous pattern of calling DAOs directly inside a
 * {@code Timeline}'s tick handler or a text-field listener, which ran the
 * query synchronously on the FX thread every time.
 */
public final class BackgroundTasks {
    private BackgroundTasks() { }

    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    /**
     * Runs {@code work} on a daemon background thread. On success,
     * {@code onSuccess} runs on the JavaFX Application Thread with the
     * result. On failure, {@code onFailure} runs on the JavaFX Application
     * Thread with the exception -- callers must not leave failures
     * unhandled just because they moved to a background thread.
     */
    public static <T> void run(ThrowingSupplier<T> work, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.get();
            }
        };
        task.setOnSucceeded(event -> onSuccess.accept(task.getValue()));
        task.setOnFailed(event -> onFailure.accept(task.getException()));
        Thread thread = new Thread(task, "bloodlink-background");
        thread.setDaemon(true);
        thread.start();
    }
}
