package com.example.emojiprefix.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class EmojiManager {

    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final Map<String, String> availableEmojis = new LinkedHashMap<>();
    private final Map<UUID, Set<String>> playerUnlockedEmojis = new HashMap<>();
    private final Map<UUID, String> playerCurrentEmoji = new HashMap<>();
    private final Map<UUID, String> playerTeamNames = new HashMap<>();

    private File dataFile;
    private FileConfiguration dataConfig;

    private static final String TEAM_PREFIX = "ep_";

    public EmojiManager(org.bukkit.plugin.java.JavaPlugin plugin) {
        this.plugin = plugin;
        loadDataFile();
    }

    // ==================== Загрузка конфигурации ====================

    public void loadEmojis() {
        availableEmojis.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("emojis");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                availableEmojis.put(key, section.getString(key));
            }
        }
    }

    private void loadDataFile() {
        dataFile = new File(plugin.getDataFolder(), "playerdata.yml");
        if (!dataFile.exists()) {
            dataFile.getParentFile().mkdirs();
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create playerdata.yml!");
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        loadPlayerData();
    }

    private void loadPlayerData() {
        playerUnlockedEmojis.clear();
        playerCurrentEmoji.clear();

        ConfigurationSection playersSection = dataConfig.getConfigurationSection("players");
        if (playersSection != null) {
            for (String uuidStr : playersSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    ConfigurationSection playerSection = playersSection.getConfigurationSection(uuidStr);

                    // Загружаем разблокированные смайлики
                    List<String> unlocked = playerSection.getStringList("unlocked");
                    playerUnlockedEmojis.put(uuid, new HashSet<>(unlocked));

                    // Загружаем текущий выбранный
                    String current = playerSection.getString("current");
                    if (current != null && !current.isEmpty()) {
                        playerCurrentEmoji.put(uuid, current);
                    }
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in playerdata.yml: " + uuidStr);
                }
            }
        }
    }

    public void savePlayerData() {
        dataConfig.set("players", null); // Очищаем старые данные

        for (Map.Entry<UUID, Set<String>> entry : playerUnlockedEmojis.entrySet()) {
            String path = "players." + entry.getKey().toString();
            dataConfig.set(path + ".unlocked", new ArrayList<>(entry.getValue()));

            String current = playerCurrentEmoji.get(entry.getKey());
            if (current != null) {
                dataConfig.set(path + ".current", current);
            }
        }

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save playerdata.yml!");
        }
    }

    // ==================== Управление смайликами ====================

    public Map<String, String> getAvailableEmojis() {
        return Collections.unmodifiableMap(availableEmojis);
    }

    public int getEmojiCount() {
        return availableEmojis.size();
    }

    public Set<String> getUnlockedEmojis(UUID uuid) {
        return playerUnlockedEmojis.getOrDefault(uuid, Collections.emptySet());
    }

    public List<String> getUnlockedEmojisForDisplay(UUID uuid) {
        // Возвращает список смайликов, которые игрок может использовать
        Set<String> unlocked = getUnlockedEmojis(uuid);
        List<String> result = new ArrayList<>();
        for (String emojiId : availableEmojis.keySet()) {
            if (unlocked.contains(emojiId)) {
                result.add(emojiId);
            }
        }
        return result;
    }

    public String getPlayerEmoji(UUID uuid) {
        String emojiId = playerCurrentEmoji.get(uuid);
        if (emojiId == null || !availableEmojis.containsKey(emojiId)) {
            return plugin.getConfig().getString("default-emoji", "");
        }
        return availableEmojis.get(emojiId);
    }

    public boolean unlockEmoji(UUID uuid, String emojiId) {
        if (!availableEmojis.containsKey(emojiId)) {
            return false;
        }

        playerUnlockedEmojis.computeIfAbsent(uuid, k -> new HashSet<>()).add(emojiId);
        savePlayerData();
        return true;
    }

    public boolean setPlayerEmoji(UUID uuid, String emojiId) {
        if (!availableEmojis.containsKey(emojiId)) {
            return false;
        }

        if (!getUnlockedEmojis(uuid).contains(emojiId)) {
            return false; // Не разблокирован
        }

        playerCurrentEmoji.put(uuid, emojiId);
        savePlayerData();

        // Обновляем отображение
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            applyEmoji(player, availableEmojis.get(emojiId));
        }

        return true;
    }

    public void removePlayerEmoji(UUID uuid) {
        playerCurrentEmoji.remove(uuid);
        savePlayerData();

        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            applyEmoji(player, "");
        }
    }

    // ==================== Применение смайлика ====================

    public void applyEmoji(Player player, String emoji) {
        if (player == null || !player.isOnline()) return;

        String prefix = (emoji == null || emoji.isEmpty()) ? "" : emoji + " ";

        // 1. Устанавливаем в TabList
        if (plugin.getConfig().getBoolean("tablist-enabled", true)) {
            Component tabName = MiniMessage.miniMessage().deserialize(prefix + player.getName());
            player.playerListName(tabName);
        }

        // 2. Устанавливаем в Nametag через Scoreboard Team
        if (plugin.getConfig().getBoolean("nametag-enabled", true)) {
            applyNametag(player, prefix);
        }
    }

    private void applyNametag(Player player, String prefix) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        String teamName = TEAM_PREFIX + player.getUniqueId().toString().substring(0, 12);

        Team team = scoreboard.getTeam(teamName);

        if (prefix.isEmpty()) {
            // Убираем игрока из команды, если префикс пустой
            if (team != null) {
                team.removeEntry(player.getName());
                playerTeamNames.remove(player.getUniqueId());
            }
            return;
        }

        // Создаём команду, если её нет
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
        }

        // Устанавливаем префикс (максимум 64 символа для modern versions, но обычно достаточно)
        team.setPrefix(prefix);

        // Добавляем игрока в команду
        if (!team.hasEntry(player.getName())) {
            team.addEntry(player.getName());
        }

        playerTeamNames.put(player.getUniqueId(), teamName);
    }

    public void cleanup() {
        // Убираем все команды, созданные плагином
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        for (String teamName : playerTeamNames.values()) {
            Team team = scoreboard.getTeam(teamName);
            if (team != null) {
                team.unregister();
            }
        }
        playerTeamNames.clear();

        // Сбрасываем TabList у всех игроков
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playerListName(null); // Сбрасываем на стандартное имя
        }
    }

    // ==================== Утилиты ====================

    public boolean isEmojiAvailable(String emojiId) {
        return availableEmojis.containsKey(emojiId);
    }

    public String getEmojiSymbol(String emojiId) {
        return availableEmojis.get(emojiId);
    }

    public List<String> getAllEmojiIds() {
        return new ArrayList<>(availableEmojis.keySet());
    }
}
