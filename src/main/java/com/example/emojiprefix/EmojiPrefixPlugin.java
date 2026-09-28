package com.example.emojiprefix;

import com.example.emojiprefix.command.PinsCommand;
import com.example.emojiprefix.listener.PlayerListener;
import com.example.emojiprefix.util.EmojiManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class EmojiPrefixPlugin extends JavaPlugin {

    private EmojiManager emojiManager;

    @Override
    public void onEnable() {
        // Сохраняем конфиг по умолчанию
        saveDefaultConfig();

        // Инициализируем менеджер смайликов
        emojiManager = new EmojiManager(this);
        emojiManager.loadEmojis();

        // Регистрируем команды
        PinsCommand pinsCommand = new PinsCommand(this, emojiManager);
        getCommand("pins").setExecutor(pinsCommand);
        getCommand("pins").setTabCompleter(pinsCommand);

        // Регистрируем слушателя событий
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this, emojiManager), this);

        // Применяем префиксы ко всем игрокам при старте (если сервер перезапускается)
        Bukkit.getOnlinePlayers().forEach(player -> {
            String emoji = emojiManager.getPlayerEmoji(player.getUniqueId());
            if (emoji != null && !emoji.isEmpty()) {
                emojiManager.applyEmoji(player, emoji);
            }
        });

        getLogger().info("EmojiPrefix enabled! Loaded " + emojiManager.getEmojiCount() + " emojis.");
    }

    @Override
    public void onDisable() {
        // Убираем Scoreboard Team при выключении
        emojiManager.cleanup();
        getLogger().info("EmojiPrefix disabled.");
    }

    public EmojiManager getEmojiManager() {
        return emojiManager;
    }
}
