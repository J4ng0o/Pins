package com.example.emojiprefix.listener;

import com.example.emojiprefix.EmojiPrefixPlugin;
import com.example.emojiprefix.util.EmojiManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerListener implements Listener {

    private final EmojiPrefixPlugin plugin;
    private final EmojiManager emojiManager;

    public PlayerListener(EmojiPrefixPlugin plugin, EmojiManager emojiManager) {
        this.plugin = plugin;
        this.emojiManager = emojiManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Применяем сохранённый смайлик при входе игрока
        String emoji = emojiManager.getPlayerEmoji(event.getPlayer().getUniqueId());
        if (emoji != null && !emoji.isEmpty()) {
            // Небольшая задержка, чтобы tablist успел инициализироваться
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (event.getPlayer().isOnline()) {
                    emojiManager.applyEmoji(event.getPlayer(), emoji);
                }
            }, 5L); // 5 тиков задержки
        }
    }
}
