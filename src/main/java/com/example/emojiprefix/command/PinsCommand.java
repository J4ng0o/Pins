package com.example.emojiprefix.command;

import com.example.emojiprefix.EmojiPrefixPlugin;
import com.example.emojiprefix.util.EmojiManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class PinsCommand implements CommandExecutor, TabCompleter {

    private final EmojiPrefixPlugin plugin;
    private final EmojiManager emojiManager;

    public PinsCommand(EmojiPrefixPlugin plugin, EmojiManager emojiManager) {
        this.plugin = plugin;
        this.emojiManager = emojiManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        // /pins - показать доступные смайлики
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Эта команда только для игроков!", NamedTextColor.RED));
                return true;
            }
            showAvailableEmojis(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "set" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Эта команда только для игроков!", NamedTextColor.RED));
                    return true;
                }
                handleSet(player, args);
                return true;
            }
            case "give" -> {
                if (!sender.hasPermission("emojiprefix.admin")) {
                    sender.sendMessage(Component.text("У вас нет прав на эту команду!", NamedTextColor.RED));
                    return true;
                }
                handleGive(sender, args);
                return true;
            }
            default -> {
                sender.sendMessage(Component.text("Неизвестная подкоманда. Используйте: /pins, /pins set <emoji>, /pins give <player> <emoji>", NamedTextColor.RED));
                return true;
            }
        }
    }

    // ==================== /pins ====================

    private void showAvailableEmojis(Player player) {
        List<String> unlocked = emojiManager.getUnlockedEmojisForDisplay(player.getUniqueId());
        String currentEmojiId = getCurrentEmojiId(player.getUniqueId());

        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
        player.sendMessage(Component.text("Ваши доступные смайлики:", NamedTextColor.GOLD));
        player.sendMessage(Component.text(""));

        if (unlocked.isEmpty()) {
            player.sendMessage(Component.text("У вас пока нет доступных смайликов.", NamedTextColor.GRAY));
            player.sendMessage(Component.text("Попросите администратора выдать вам смайлик!", NamedTextColor.GRAY));
        } else {
            for (String emojiId : unlocked) {
                String symbol = emojiManager.getEmojiSymbol(emojiId);
                boolean isCurrent = emojiId.equals(currentEmojiId);

                Component line = Component.text(symbol + " ", NamedTextColor.WHITE)
                    .append(Component.text("[" + emojiId + "]", isCurrent ? NamedTextColor.GREEN : NamedTextColor.YELLOW));

                if (isCurrent) {
                    line = line.append(Component.text(" ✔ (установлен)", NamedTextColor.GREEN));
                }

                player.sendMessage(line);
            }

            player.sendMessage(Component.text(""));
            player.sendMessage(Component.text("Используйте: /pins set <id>", NamedTextColor.GRAY));
        }

        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
    }

    // ==================== /pins set ====================

    private void handleSet(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Использование: /pins set <emoji_id>", NamedTextColor.RED));
            player.sendMessage(Component.text("Просмотреть доступные: /pins", NamedTextColor.GRAY));
            return;
        }

        String emojiId = args[1].toLowerCase();

        if (!emojiManager.isEmojiAvailable(emojiId)) {
            player.sendMessage(Component.text("Смайлик '" + emojiId + "' не существует!", NamedTextColor.RED));
            return;
        }

        if (!emojiManager.getUnlockedEmojis(player.getUniqueId()).contains(emojiId)) {
            player.sendMessage(Component.text("Вы не разблокировали этот смайлик!", NamedTextColor.RED));
            player.sendMessage(Component.text("Попросите администратора выдать вам его: /pins give " + player.getName() + " " + emojiId, NamedTextColor.GRAY));
            return;
        }

        boolean success = emojiManager.setPlayerEmoji(player.getUniqueId(), emojiId);

        if (success) {
            String symbol = emojiManager.getEmojiSymbol(emojiId);
            player.sendMessage(Component.text("Смайлик " + symbol + " установлен как префикс!", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Не удалось установить смайлик!", NamedTextColor.RED));
        }
    }

    // ==================== /pins give ====================

    private void handleGive(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Использование: /pins give <player> <emoji_id>", NamedTextColor.RED));
            sender.sendMessage(Component.text("Доступные смайлики:", NamedTextColor.GRAY));

            // Показываем все смайлики
            StringBuilder sb = new StringBuilder();
            for (String id : emojiManager.getAllEmojiIds()) {
                sb.append(id).append(" ");
            }
            sender.sendMessage(Component.text(sb.toString().trim(), NamedTextColor.YELLOW));
            return;
        }

        String playerName = args[1];
        String emojiId = args[2].toLowerCase();

        Player target = Bukkit.getPlayerExact(playerName);

        if (target == null) {
            sender.sendMessage(Component.text("Игрок '" + playerName + "' не найден!", NamedTextColor.RED));
            return;
        }

        if (!emojiManager.isEmojiAvailable(emojiId)) {
            sender.sendMessage(Component.text("Смайлик '" + emojiId + "' не существует!", NamedTextColor.RED));
            return;
        }

        boolean success = emojiManager.unlockEmoji(target.getUniqueId(), emojiId);

        if (success) {
            String symbol = emojiManager.getEmojiSymbol(emojiId);
            sender.sendMessage(Component.text("Вы выдали смайлик " + symbol + " игроку " + target.getName() + "!", NamedTextColor.GREEN));
            target.sendMessage(Component.text("Вам выдан новый смайлик: " + symbol + " (" + emojiId + ")", NamedTextColor.GOLD));
            target.sendMessage(Component.text("Установите его командой: /pins set " + emojiId, NamedTextColor.GRAY));
        } else {
            sender.sendMessage(Component.text("Не удалось выдать смайлик!", NamedTextColor.RED));
        }
    }

    // ==================== Утилиты ====================

    private String getCurrentEmojiId(UUID uuid) {
        // Возвращает ID текущего смайлика игрока (для отображения в /pins)
        for (String id : emojiManager.getAllEmojiIds()) {
            String symbol = emojiManager.getEmojiSymbol(id);
            if (symbol.equals(emojiManager.getPlayerEmoji(uuid))) {
                return id;
            }
        }
        return null;
    }

    // ==================== Tab Completion ====================

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {

        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            subs.add("set");
            if (sender.hasPermission("emojiprefix.admin")) {
                subs.add("give");
            }
            return subs.stream()
                .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("set")) {
                if (sender instanceof Player player) {
                    // Показываем только разблокированные смайлики
                    return emojiManager.getUnlockedEmojisForDisplay(player.getUniqueId()).stream()
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
                }
            } else if (args[0].equalsIgnoreCase("give") && sender.hasPermission("emojiprefix.admin")) {
                // Показываем имена игроков
                return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("give") && sender.hasPermission("emojiprefix.admin")) {
            // Показываем все доступные смайлики
            return emojiManager.getAllEmojiIds().stream()
                .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
