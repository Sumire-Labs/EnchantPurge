package com.example.disenchanter.logging;

import com.example.disenchanter.DisenchanterPlugin;
import org.bukkit.Bukkit;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Handles operation logging to console and file.
 * <p>
 * Queue-based logging with asynchronous flush to daily log files.
 * Uses config's {@code logging.format} string with placeholder replacement:
 * <ul>
 *   <li>{@code {time}} — timestamp</li>
 *   <li>{@code {player}} — player name</li>
 *   <li>{@code {uuid}} — player UUID</li>
 *   <li>{@code {item}} — item type name</li>
 *   <li>{@code {enchant}} — enchantment key</li>
 *   <li>{@code {level}} — enchantment level</li>
 *   <li>{@code {mode}} — removal mode (individual/bulk)</li>
 *   <li>{@code {cost}} — cost description</li>
 *   <li>{@code {result}} — result status (success/skip/deny)</li>
 * </ul>
 */
public class DisenchantLogger {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DisenchanterPlugin plugin;
    private final ConcurrentLinkedQueue<String> logQueue = new ConcurrentLinkedQueue<>();
    private final ExecutorService fileExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Disenchanter-LogWriter");
        t.setDaemon(true);
        return t;
    });

    private File logDir;
    private volatile boolean shutdown = false;

    public DisenchantLogger(DisenchanterPlugin plugin) {
        this.plugin = plugin;
    }

    /** Initialize logger — create log directory if file logging is enabled. */
    public void init() {
        boolean fileLogging = plugin.getConfigManager().isFileLogging();
        if (fileLogging) {
            logDir = new File(plugin.getDataFolder(), "logs");
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
        }
    }

    /**
     * Log a disenchant operation.
     *
     * @param playerName player name
     * @param playerUuid player UUID
     * @param enchant    enchantment display key (e.g. "minecraft:sharpness")
     * @param level      enchantment level
     * @param itemName   item type name
     * @param cost       cost description
     * @param mode       removal mode label ("individual" or "bulk")
     * @param result     result label ("success", "skip", "deny")
     */
    public void log(String playerName, String playerUuid, String enchant, int level,
                    String itemName, String cost, String mode, String result) {
        if (shutdown) return;

        String time = LocalDateTime.now().format(TIME_FORMATTER);
        String format = plugin.getConfigManager().getLogFormat();

        // Apply placeholder replacement
        String message = format
                .replace("{time}", time)
                .replace("{player}", playerName)
                .replace("{uuid}", playerUuid)
                .replace("{enchant}", enchant)
                .replace("{level}", String.valueOf(level))
                .replace("{item}", itemName)
                .replace("{cost}", cost)
                .replace("{mode}", mode)
                .replace("{result}", result);

        // Console logging — on main thread is fine
        if (plugin.getConfigManager().isConsoleLogging()) {
            plugin.getLogger().info("[Disenchant] " + message);
        }

        // File logging — queue and flush asynchronously
        if (plugin.getConfigManager().isFileLogging() && logDir != null) {
            logQueue.add(message);
            scheduleFlush();
        }
    }

    /** Schedule an async flush of queued log entries using a safe Java executor. */
    private void scheduleFlush() {
        if (shutdown) return;
        fileExecutor.submit(this::flushQueue);
    }

    /** Flush queued log entries to disk on the executor thread. */
    private void flushQueue() {
        if (logQueue.isEmpty()) return;

        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        File logFile = new File(logDir, "disenchant-" + today + ".log");

        try (BufferedWriter writer = new BufferedWriter(
                new FileWriter(logFile, StandardCharsets.UTF_8, true))) {
            String entry;
            while ((entry = logQueue.poll()) != null) {
                writer.write(entry);
                writer.newLine();
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to write log: " + e.getMessage());
        }
    }

    /**
     * Shutdown the logger — flush remaining entries synchronously and
     * shut down the async executor. Blocks until all pending writes complete.
     */
    public void shutdown() {
        shutdown = true;
        // Flush remaining queue synchronously
        flushQueue();
        // Shut down the async executor
        fileExecutor.shutdown();
        try {
            if (!fileExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                fileExecutor.shutdownNow();
                plugin.getLogger().warning("Log file executor did not terminate in time.");
            }
        } catch (InterruptedException e) {
            fileExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}