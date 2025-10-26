package com.zetaplugins.cookieclickerz.storage;

import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import com.zetaplugins.cookieclickerz.CookieClickerZ;
import com.zetaplugins.cookieclickerz.util.achievements.Achievement;

import java.io.*;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class MongoDBStorage extends Storage {
    private final Map<UUID, PlayerData> playerDataCache = new ConcurrentHashMap<>();
    private static final String CSV_SEPARATOR = ",";
    private MongoClient mongoClient;
    private MongoDatabase database;
    private MongoCollection<Document> playersCollection;
    private MongoCollection<Document> upgradesCollection;
    private MongoCollection<Document> achievementsCollection;

    public MongoDBStorage(CookieClickerZ plugin) {
        super(plugin);

        FileConfiguration config = getPlugin().getConfig();

        final String HOST = config.getString("storage.host", "localhost");
        final String PORT = config.getString("storage.port", "27017");
        final String DATABASE = config.getString("storage.database", "cookieclicker");
        final String USERNAME = config.getString("storage.username", "");
        final String PASSWORD = config.getString("storage.password", "");

        try {
            String connectionString;
            if (USERNAME.isEmpty() || PASSWORD.isEmpty()) {
                connectionString = "mongodb://" + HOST + ":" + PORT;
            } else {
                connectionString = "mongodb://" + USERNAME + ":" + PASSWORD + "@" + HOST + ":" + PORT;
            }

            mongoClient = MongoClients.create(connectionString);
            database = mongoClient.getDatabase(DATABASE);
            playersCollection = database.getCollection("players");
            upgradesCollection = database.getCollection("upgrades");
            achievementsCollection = database.getCollection("achievements");

        } catch (Exception e) {
            getPlugin().getLogger().severe("Failed to connect to MongoDB: " + e.getMessage());
        }
    }

    @Override
    public void init() {
        try {
            // MongoDB creates collections automatically, but we can verify the connection
            database.listCollectionNames().first();
            getPlugin().getLogger().info("Successfully connected to MongoDB database");
        } catch (Exception e) {
            getPlugin().getLogger().severe("Failed to initialize MongoDB database: " + e.getMessage());
        }
    }

    @Override
    public void save(PlayerData playerData) {
        if (playerData == null) return;

        if (shouldUsePlayerCache() && playerDataCache.containsKey(playerData.getUuid())) {
            playerDataCache.put(playerData.getUuid(), playerData);
            return;
        }

        try {
            // Save player data
            Document playerDoc = new Document("_id", playerData.getUuid().toString())
                    .append("name", playerData.getName())
                    .append("totalCookies", playerData.getTotalCookies().toString())
                    .append("totalClicks", playerData.getTotalClicks())
                    .append("lastLogoutTime", playerData.getLastLogoutTime())
                    .append("cookiesPerClick", playerData.getCookiesPerClick().toString())
                    .append("offlineCookies", playerData.getOfflineCookies().toString())
                    .append("prestige", playerData.getPrestige());

            playersCollection.replaceOne(
                    Filters.eq("_id", playerData.getUuid().toString()),
                    playerDoc,
                    new ReplaceOptions().upsert(true)
            );

            saveUpgrades(playerData);
            saveAchievements(playerData);

        } catch (Exception e) {
            getPlugin().getLogger().severe("Failed to save player data to MongoDB: " + e.getMessage());
        }
    }

    private void saveUpgrades(PlayerData playerData) {
        try {
            String uuid = playerData.getUuid().toString();

            if (playerData.hasRemovedUpgrades()) {
                upgradesCollection.deleteMany(Filters.eq("uuid", uuid));
            }

            for (Map.Entry<String, Integer> entry : playerData.getUpgrades().entrySet()) {
                Document upgradeDoc = new Document("uuid", uuid)
                        .append("upgrade_name", entry.getKey())
                        .append("level", entry.getValue());

                upgradesCollection.replaceOne(
                        Filters.and(
                                Filters.eq("uuid", uuid),
                                Filters.eq("upgrade_name", entry.getKey())
                        ),
                        upgradeDoc,
                        new ReplaceOptions().upsert(true)
                );
            }
        } catch (Exception e) {
            getPlugin().getLogger().severe("Failed to save upgrades to MongoDB: " + e.getMessage());
        }
    }

    private void saveAchievements(PlayerData playerData) {
        try {
            String uuid = playerData.getUuid().toString();

            for (Achievement achievement : playerData.getAchievements()) {
                Document achievementDoc = new Document("uuid", uuid)
                        .append("achievement_name", achievement.getType().getSlug())
                        .append("progress", achievement.getProgress());

                achievementsCollection.replaceOne(
                        Filters.and(
                                Filters.eq("uuid", uuid),
                                Filters.eq("achievement_name", achievement.getType().getSlug())
                        ),
                        achievementDoc,
                        new ReplaceOptions().upsert(true)
                );
            }
        } catch (Exception e) {
            getPlugin().getLogger().severe("Failed to save achievements to MongoDB: " + e.getMessage());
        }
    }

    @Override
    public PlayerData load(UUID uuid) {
        if (shouldUsePlayerCache() && playerDataCache.containsKey(uuid)) {
            return playerDataCache.get(uuid);
        }

        try {
            Document playerDoc = playersCollection.find(Filters.eq("_id", uuid.toString())).first();

            if (playerDoc == null) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null) return null;
                PlayerData newPlayerData = new PlayerData(player.getName(), uuid);
                save(newPlayerData);
                return newPlayerData;
            }

            PlayerData playerData = new PlayerData(playerDoc.getString("name"), uuid);
            playerData.setTotalCookies(new BigInteger(playerDoc.getString("totalCookies")));
            playerData.setTotalClicks(playerDoc.getInteger("totalClicks"));
            playerData.setLastLogoutTime(playerDoc.getLong("lastLogoutTime"));
            playerData.setCookiesPerClick(new BigInteger(playerDoc.getString("cookiesPerClick")));
            playerData.setOfflineCookies(new BigInteger(playerDoc.getString("offlineCookies")));
            playerData.setPrestige(playerDoc.getInteger("prestige", 0));

            loadUpgrades(uuid, playerData);
            loadAchievements(uuid, playerData);

            if (shouldUsePlayerCache()) {
                playerDataCache.put(uuid, playerData);
                if (playerDataCache.size() > getMaxCacheSize()) saveAllCachedData();
            }

            return playerData;
        } catch (Exception e) {
            getPlugin().getLogger().severe("Failed to load player data from MongoDB: " + e.getMessage());
            return null;
        }
    }

    private void loadUpgrades(UUID uuid, PlayerData playerData) {
        try {
            FindIterable<Document> upgrades = upgradesCollection.find(Filters.eq("uuid", uuid.toString()));
            for (Document upgrade : upgrades) {
                String upgradeName = upgrade.getString("upgrade_name");
                int level = upgrade.getInteger("level");
                playerData.addUpgrade(upgradeName, level);
            }
        } catch (Exception e) {
            getPlugin().getLogger().severe("Failed to load upgrades from MongoDB: " + e.getMessage());
        }
    }

    private void loadAchievements(UUID uuid, PlayerData playerData) {
        try {
            FindIterable<Document> achievements = achievementsCollection.find(Filters.eq("uuid", uuid.toString()));
            for (Document achievement : achievements) {
                String achievementSlug = achievement.getString("achievement_name");
                int progress = achievement.getInteger("progress");
                playerData.setAchievementProgress(achievementSlug, progress);
            }
        } catch (Exception e) {
            getPlugin().getLogger().severe("Failed to load achievements from MongoDB: " + e.getMessage());
        }
    }

    @Override
    public String export(String fileName) {
        String filePath = getPlugin().getDataFolder().getPath() + "/" + fileName + ".csv";
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            FindIterable<Document> players = playersCollection.find();
            for (Document player : players) {
                String line = player.getString("_id") + CSV_SEPARATOR +
                        player.getString("name") + CSV_SEPARATOR +
                        player.getString("totalCookies") + CSV_SEPARATOR +
                        player.getInteger("totalClicks") + CSV_SEPARATOR +
                        player.getLong("lastLogoutTime") + CSV_SEPARATOR +
                        player.getString("cookiesPerClick") + CSV_SEPARATOR +
                        player.getString("offlineCookies") + CSV_SEPARATOR +
                        player.getInteger("prestige", 0);
                writer.write(line);
                writer.newLine();
            }
            return filePath;
        } catch (Exception e) {
            getPlugin().getLogger().severe("Failed to export player data to CSV: " + e.getMessage());
            return null;
        }
    }

    @Override
    public void importData(String fileName) {
        String filePath = getPlugin().getDataFolder().getPath() + "/" + fileName;

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] data = line.split(CSV_SEPARATOR);

                if (data.length != 8) {
                    getPlugin().getLogger().severe("Invalid CSV format.");
                    continue;
                }

                try {
                    Document playerDoc = new Document("_id", data[0])
                            .append("name", data[1])
                            .append("totalCookies", data[2])
                            .append("totalClicks", Integer.parseInt(data[3]))
                            .append("lastLogoutTime", Long.parseLong(data[4]))
                            .append("cookiesPerClick", data[5])
                            .append("offlineCookies", data[6])
                            .append("prestige", Integer.parseInt(data[7]));

                    playersCollection.replaceOne(
                            Filters.eq("_id", data[0]),
                            playerDoc,
                            new ReplaceOptions().upsert(true)
                    );
                } catch (Exception e) {
                    getPlugin().getLogger().log(Level.SEVERE, "Failed to import player data: " + e.getMessage(), e);
                }
            }
        } catch (IOException e) {
            getPlugin().getLogger().log(Level.SEVERE, "Failed to read CSV file: " + e.getMessage(), e);
        }
    }

    @Override
    public List<PlayerData> getAllPlayers() {
        List<PlayerData> players = new ArrayList<>();

        try {
            FindIterable<Document> playerDocs = playersCollection.find();
            for (Document doc : playerDocs) {
                PlayerData player = new PlayerData(
                        doc.getString("name"),
                        UUID.fromString(doc.getString("_id"))
                );
                player.setTotalCookies(new BigInteger(doc.getString("totalCookies")));
                player.setTotalClicks(doc.getInteger("totalClicks"));
                player.setLastLogoutTime(doc.getLong("lastLogoutTime"));
                player.setCookiesPerClick(new BigInteger(doc.getString("cookiesPerClick")));
                player.setOfflineCookies(new BigInteger(doc.getString("offlineCookies")));
                player.setPrestige(doc.getInteger("prestige", 0));
                players.add(player);
            }
        } catch (Exception e) {
            getPlugin().getLogger().severe("Failed to load all players from MongoDB: " + e.getMessage());
        }

        return players;
    }

    @Override
    public void saveAllCachedData() {
        if (playerDataCache.isEmpty()) return;

        Map<UUID, PlayerData> snapshot = new HashMap<>(playerDataCache);

        try {
            for (PlayerData data : snapshot.values()) {
                Document playerDoc = new Document("_id", data.getUuid().toString())
                        .append("name", data.getName())
                        .append("totalCookies", data.getTotalCookies().toString())
                        .append("totalClicks", data.getTotalClicks())
                        .append("lastLogoutTime", data.getLastLogoutTime())
                        .append("cookiesPerClick", data.getCookiesPerClick().toString())
                        .append("offlineCookies", data.getOfflineCookies().toString())
                        .append("prestige", data.getPrestige());

                playersCollection.replaceOne(
                        Filters.eq("_id", data.getUuid().toString()),
                        playerDoc,
                        new ReplaceOptions().upsert(true)
                );

                saveUpgrades(data);
                saveAchievements(data);
            }

            playerDataCache.keySet().removeAll(snapshot.keySet());
        } catch (Exception e) {
            getPlugin().getLogger().log(Level.SEVERE, "Failed to flush player data cache: " + e.getMessage(), e);
        }
    }

    @Override
    public void saveAndRemoveFromCache(PlayerData playerData) {
        if (playerData == null) return;

        try {
            // Save player data directly to database
            Document playerDoc = new Document("_id", playerData.getUuid().toString())
                    .append("name", playerData.getName())
                    .append("totalCookies", playerData.getTotalCookies().toString())
                    .append("totalClicks", playerData.getTotalClicks())
                    .append("lastLogoutTime", playerData.getLastLogoutTime())
                    .append("cookiesPerClick", playerData.getCookiesPerClick().toString())
                    .append("offlineCookies", playerData.getOfflineCookies().toString())
                    .append("prestige", playerData.getPrestige());

            playersCollection.replaceOne(
                    Filters.eq("_id", playerData.getUuid().toString()),
                    playerDoc,
                    new ReplaceOptions().upsert(true)
            );

            saveUpgrades(playerData);
            saveAchievements(playerData);

            // Remove from cache after saving to database
            playerDataCache.remove(playerData.getUuid());
        } catch (Exception e) {
            getPlugin().getLogger().severe("Failed to save player data to MongoDB: " + e.getMessage());
        }
    }

    public void close() {
        if (mongoClient != null) {
            mongoClient.close();
        }
    }
}
