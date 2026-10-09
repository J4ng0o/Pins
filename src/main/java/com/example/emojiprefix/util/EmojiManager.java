package com.example.emojiprefix.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class EmojiManager {

    private final JavaPlugin plugin;
    private final Map<String, String> availableEmojis = new LinkedHashMap<>();
    private final Map<UUID, Set<String>> playerUnlockedEmojis = new HashMap<>();
    private final Map<UUID, String> playerCurrentEmoji = new HashMap<>();
    private final Map<UUID, String> playerTeamNames = new HashMap<>();

    private File dataFile;
    private FileConfiguration dataConfig;

    private static final String TEAM_PREFIX = "ep_";

    public EmojiManager(JavaPlugin plugin) {
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

                    List<String> unlocked = playerSection.getStringList("unlocked");
                    playerUnlockedEmojis.put(uuid, new HashSet<>(unlocked));

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
        dataConfig.set("players", null);

        // Объединяем все UUID, у которых что-то есть (unlocked или current)
        Set<UUID> allUuids = new HashSet<>();
        allUuids.addAll(playerUnlockedEmojis.keySet());
        allUuids.addAll(playerCurrentEmoji.keySet());

        for (UUID uuid : allUuids) {
            String path = "players." + uuid.toString();

            Set<String> unlocked = playerUnlockedEmojis.get(uuid);
            if (unlocked != null && !unlocked.isEmpty()) {
                dataConfig.set(path + ".unlocked", new ArrayList<>(unlocked));
            }

            String current = playerCurrentEmoji.get(uuid);
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

    public String getCurrentEmojiId(UUID uuid) {
        return playerCurrentEmoji.get(uuid);
    }

    public boolean unlockEmoji(UUID uuid, String emojiId) {
        if (!availableEmojis.containsKey(emojiId)) {
            return false;
        }
        playerUnlockedEmojis.computeIfAbsent(uuid, k -> new HashSet<>()).add(emojiId);
        savePlayerData();
        return true;
    }

    /**
     * Забирает смайлик у игрока. Если он был установлен как префикс — снимает и префикс.
     * @return true, если смайлик был и его удалось забрать
     */
    public boolean lockEmoji(UUID uuid, String emojiId) {
        if (!availableEmojis.containsKey(emojiId)) {
            return false;
        }

        Set<String> unlocked = playerUnlockedEmojis.get(uuid);
        if (unlocked == null || !unlocked.contains(emojiId)) {
            return false;
        }

        unlocked.remove(emojiId);
        if (unlocked.isEmpty()) {
            playerUnlockedEmojis.remove(uuid);
        }

        // Если этот смайлик был установлен как текущий — сбрасываем
        if (emojiId.equals(playerCurrentEmoji.get(uuid))) {
            playerCurrentEmoji.remove(uuid);

            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                applyEmoji(player, "");
            }
        }

        savePlayerData();
        return true;
    }

    /**
     * Забирает ВСЕ смайлики у игрока и снимает активный префикс.
     * @return количество забранных смайликов
     */
    public int lockAllEmojis(UUID uuid) {
        Set<String> unlocked = playerUnlockedEmojis.get(uuid);
        int count = (unlocked == null) ? 0 : unlocked.size();

        boolean hadCurrent = playerCurrentEmoji.containsKey(uuid);

        if (unlocked != null) {
            unlocked.clear();
            playerUnlockedEmojis.remove(uuid);
        }
        playerCurrentEmoji.remove(uuid);

        if (hadCurrent || count > 0) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                applyEmoji(player, "");
            }
        }

        savePlayerData();
        return count;
    }

    public boolean setPlayerEmoji(UUID uuid, String emojiId) {
        if (!availableEmojis.containsKey(emojiId)) {
            return false;
        }
        if (!getUnlockedEmojis(uuid).contains(emojiId)) {
            return false;
        }

        playerCurrentEmoji.put(uuid, emojiId);
        savePlayerData();

        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            applyEmoji(player, availableEmojis.get(emojiId));
        }
        return true;
    }

    /**
     * Снимает активный смайлик с ника игрока, НЕ удаляя его из разблокированных.
     * @return true, если что-то было снято
     */
    public boolean clearCurrentEmoji(UUID uuid) {
        if (!playerCurrentEmoji.containsKey(uuid)) {
            return false;
        }

        playerCurrentEmoji.remove(uuid);
        savePlayerData();

        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            applyEmoji(player, "");
        }
        return true;
    }

    // ==================== Применение смайлика ====================

    public void applyEmoji(Player player, String emoji) {
        if (player == null || !player.isOnline()) return;

        String prefix = (emoji == null || emoji.isEmpty()) ? "" : emoji + " ";

        // 1. Устанавливаем в TabList
        if (plugin.getConfig().getBoolean("tablist-enabled", true)) {
            if (prefix.isEmpty()) {
                player.playerListName(Component.text(player.getName()));
            } else {
                Component tabName = MiniMessage.miniMessage().deserialize(prefix + player.getName());
                player.playerListName(tabName);
            }
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
            if (team != null) {
                team.removeEntry(player.getName());
                playerTeamNames.remove(player.getUniqueId());
            }
            return;
        }

        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
        }

        team.setPrefix(prefix);

        if (!team.hasEntry(player.getName())) {
            team.addEntry(player.getName());
        }

        playerTeamNames.put(player.getUniqueId(), teamName);
    }

    public void cleanup() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        for (String teamName : playerTeamNames.values()) {
            Team team = scoreboard.getTeam(teamName);
            if (team != null) {
                team.unregister();
            }
        }
        playerTeamNames.clear();

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playerListName(null);
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
