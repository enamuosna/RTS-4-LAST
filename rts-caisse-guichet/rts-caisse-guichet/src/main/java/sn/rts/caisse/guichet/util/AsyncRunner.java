package sn.rts.caisse.guichet.util;

import javafx.application.Platform;
import javafx.concurrent.Task;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Wrapper simple pour exécuter un appel API sur un thread dédié et
 * recevoir le résultat (ou l'erreur) sur le thread JavaFX Application.
 * <p>
 * Cela évite de geler l'UI pendant les 50-500 ms d'un appel HTTP.
 */
public final class AsyncRunner {

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "rts-api-worker");
        t.setDaemon(true);
        return t;
    });

    private AsyncRunner() {}

    /**
     * Exécute {@code supplier} en tâche de fond, appelle {@code onSuccess}
     * sur le FX thread en cas de succès, {@code onError} en cas d'exception.
     */
    public static <T> void run(Supplier<T> supplier,
                               Consumer<T> onSuccess,
                               Consumer<Throwable> onError) {
        Task<T> task = new Task<>() {
            @Override protected T call() { return supplier.get(); }
        };
        task.setOnSucceeded(e -> Platform.runLater(() -> onSuccess.accept(task.getValue())));
        task.setOnFailed(e -> Platform.runLater(() -> onError.accept(task.getException())));
        EXECUTOR.submit(task);
    }

    public static void shutdown() {
        EXECUTOR.shutdownNow();
    }
}
