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

        // /pins — показать доступные игроку смайлики
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Эта команда только для игроков!", NamedTextColor.RED));
                return true;
            }
            showAvailableEmojis(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "set" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Эта команда только для игроков!", NamedTextColor.RED));
                    return true;
                }
                handleSet(player, args);
            }
            case "clear", "reset" -> {
                // Игрок снимает свой префикс; админ может указать ник
                if (!(sender instanceof Player) && args.length < 2) {
                    sender.sendMessage(Component.text("Из консоли используйте: /pins clear <ник>", NamedTextColor.RED));
                    return true;
                }
                handleClear(sender, args);
            }
            case "give" -> {
                if (!sender.hasPermission("emojiprefix.admin")) {
                    sender.sendMessage(Component.text("У вас нет прав на эту команду!", NamedTextColor.RED));
                    return true;
                }
                handleGive(sender, args);
            }
            case "take" -> {
                if (!sender.hasPermission("emojiprefix.admin")) {
                    sender.sendMessage(Component.text("У вас нет прав на эту команду!", NamedTextColor.RED));
                    return true;
                }
                handleTake(sender, args);
            }
            case "takeall" -> {
                if (!sender.hasPermission("emojiprefix.admin")) {
                    sender.sendMessage(Component.text("У вас нет прав на эту команду!", NamedTextColor.RED));
                    return true;
                }
                handleTakeAll(sender, args);
            }
            default -> sender.sendMessage(Component.text(
                "Неизвестная подкоманда. Доступно: /pins, /pins set <emoji>, /pins clear [player], /pins give <player> <emoji>, /pins take <player> <emoji>, /pins takeall <player>",
                NamedTextColor.RED));
        }
        return true;
    }

    // ==================== /pins ====================

    private void showAvailableEmojis(Player player) {
        List<String> unlocked = emojiManager.getUnlockedEmojisForDisplay(player.getUniqueId());
        String currentEmojiId = emojiManager.getCurrentEmojiId(player.getUniqueId());

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
            player.sendMessage(Component.text("Снять префикс: /pins clear", NamedTextColor.GRAY));
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

    // ==================== /pins clear ====================

    private void handleClear(CommandSender sender, String[] args) {
        // /pins clear — снять свой префикс
        if (args.length < 2) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Из консоли нужно указать ник: /pins clear <ник>", NamedTextColor.RED));
                return;
            }

            boolean success = emojiManager.clearCurrentEmoji(player.getUniqueId());
            if (success) {
                player.sendMessage(Component.text("Ваш смайлик-префикс снят.", NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text("У вас не установлен смайлик.", NamedTextColor.YELLOW));
            }
            return;
        }

        // /pins clear <ник> — только для админов
        if (!sender.hasPermission("emojiprefix.admin")) {
            sender.sendMessage(Component.text("У вас нет прав снимать префиксы у других игроков!", NamedTextColor.RED));
            return;
        }

        String playerName = args[1];
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            sender.sendMessage(Component.text("Игрок '" + playerName + "' не найден!", NamedTextColor.RED));
            return;
        }

        boolean success = emojiManager.clearCurrentEmoji(target.getUniqueId());
        if (success) {
            sender.sendMessage(Component.text("Вы сняли смайлик-префикс с игрока " + target.getName() + ".", NamedTextColor.GREEN));
            target.sendMessage(Component.text("С вас сняли смайлик-префикс.", NamedTextColor.YELLOW));
        } else {
            sender.sendMessage(Component.text("У игрока " + target.getName() + " нет установленного смайлика.", NamedTextColor.YELLOW));
        }
    }

    // ==================== /pins give ====================

    private void handleGive(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Использование: /pins give <player> <emoji_id>", NamedTextColor.RED));

            StringBuilder sb = new StringBuilder();
            for (String id : emojiManager.getAllEmojiIds()) {
                sb.append(id).append(" ");
            }
            sender.sendMessage(Component.text("Доступные: " + sb.toString().trim(), NamedTextColor.YELLOW));
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
            target.sendMessage(Component.text("Установите: /pins set " + emojiId, NamedTextColor.GRAY));
        } else {
            sender.sendMessage(Component.text("Не удалось выдать смайлик!", NamedTextColor.RED));
        }
    }

    // ==================== /pins take ====================

    private void handleTake(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Использование: /pins take <player> <emoji_id>", NamedTextColor.RED));
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

        boolean success = emojiManager.lockEmoji(target.getUniqueId(), emojiId);

        if (success) {
            String symbol = emojiManager.getEmojiSymbol(emojiId);
            sender.sendMessage(Component.text("Вы забрали смайлик " + symbol + " у игрока " + target.getName() + "!", NamedTextColor.GREEN));
            target.sendMessage(Component.text("У вас забрали смайлик: " + symbol + " (" + emojiId + ")", NamedTextColor.RED));
        } else {
            sender.sendMessage(Component.text("У игрока " + target.getName() + " нет этого смайлика!", NamedTextColor.YELLOW));
        }
    }

    // ==================== /pins takeall ====================

    private void handleTakeAll(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Использование: /pins takeall <player>", NamedTextColor.RED));
            return;
        }

        String playerName = args[1];
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            sender.sendMessage(Component.text("Игрок '" + playerName + "' не найден!", NamedTextColor.RED));
            return;
        }

        int count = emojiManager.lockAllEmojis(target.getUniqueId());

        if (count > 0) {
            sender.sendMessage(Component.text("Вы забрали " + count + " смайлик(ов) у игрока " + target.getName() + "!", NamedTextColor.GREEN));
            target.sendMessage(Component.text("У вас забрали все смайлики (" + count + " шт.)", NamedTextColor.RED));
        } else {
            sender.sendMessage(Component.text("У игрока " + target.getName() + " нет смайликов!", NamedTextColor.YELLOW));
        }
    }

    // ==================== Tab completion ====================

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {

        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            subs.add("set");
            subs.add("clear");
            if (sender.hasPermission("emojiprefix.admin")) {
                subs.add("give");
                subs.add("take");
                subs.add("takeall");
            }
            return subs.stream()
                .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("set")) {
                if (sender instanceof Player player) {
                    return emojiManager.getUnlockedEmojisForDisplay(player.getUniqueId()).stream()
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
                }
            }

            if (args[0].equalsIgnoreCase("give")
                || args[0].equalsIgnoreCase("take")
                || args[0].equalsIgnoreCase("takeall")
                || args[0].equalsIgnoreCase("clear")) {

                if (sender.hasPermission("emojiprefix.admin")) {
                    return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
                }
            }
        }

        if (args.length == 3
            && sender.hasPermission("emojiprefix.admin")
            && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("take"))) {
            return emojiManager.getAllEmojiIds().stream()
                .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
