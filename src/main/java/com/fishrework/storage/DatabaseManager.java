package com.fishrework.storage;

import com.fishrework.FishRework;
import com.fishrework.model.ParticleDetailMode;
import com.fishrework.model.AutoSellMode;
import com.fishrework.model.PlayerData;
import com.fishrework.model.SeaCreatureMessageMode;
import com.fishrework.model.Skill;

import java.io.File;
import java.sql.*;
import java.util.UUID;
import java.util.logging.Level;
import java.util.Base64;

public class DatabaseManager {

    private static final String TABLE_PLAYER_FISH_BAG = "player_fish_bag";
    private static final String TABLE_PLAYER_LAVA_BAG = "player_lava_bag";
    private static final String BAG_LABEL_FISH = "fish bag";
    private static final String BAG_LABEL_LAVA = "lava bag";

    private final FishRework plugin;
    private Connection connection;
    /** Single lock for all database operations — prevents concurrent access on the shared SQLite connection. */
    private final Object dbLock = new Object();

    public DatabaseManager(FishRework plugin) {
        this.plugin = plugin;
        connect();
        if (connection != null) {
            initTable();
        }
    }

    private void connect() {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }

            File dbFile = new File(dataFolder, "data.db");
            if (!dbFile.exists()) {
                dbFile.createNewFile();
            }

            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            // Enable WAL for better concurrent read performance
            try (Statement s = connection.createStatement()) {
                s.execute("PRAGMA journal_mode=WAL;");
            }
            plugin.getLogger().info("[Fish Rework] Connected to SQLite database.");

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[Fish Rework] Failed to connect to database", e);
            connection = null;
        }
    }

    private boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean validateConnection() {
        synchronized (dbLock) {
            if (!isConnected()) return false;
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("SELECT 1");
                return true;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "[Fish Rework] Database connection validation failed", e);
                return false;
            }
        }
    }

    private void initTable() {
        synchronized (dbLock) {
        if (!isConnected()) return;
        try (Statement stmt = connection.createStatement()) {
            String skillsSql = "CREATE TABLE IF NOT EXISTS player_skills (" +
                    "uuid VARCHAR(36) NOT NULL, " +
                    "skill VARCHAR(32) NOT NULL, " +
                    "xp DOUBLE NOT NULL DEFAULT 0, " +
                    "level INT NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY (uuid, skill));";
            stmt.execute(skillsSql);
            
            String collectionSql = "CREATE TABLE IF NOT EXISTS fish_collection (" +
                    "uuid VARCHAR(36) NOT NULL, " +
                    "mob_id VARCHAR(32) NOT NULL, " +
                    "count INT NOT NULL DEFAULT 0, " +
                    "max_weight DOUBLE NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY (uuid, mob_id));";
            stmt.execute(collectionSql);
            
            String artifactSql = "CREATE TABLE IF NOT EXISTS artifact_collection (" +
                    "uuid VARCHAR(36) NOT NULL, " +
                    "artifact_id VARCHAR(64) NOT NULL, " +
                    "PRIMARY KEY (uuid, artifact_id));";
            stmt.execute(artifactSql);

            String settingsSql = "CREATE TABLE IF NOT EXISTS player_settings (" +
                    "uuid VARCHAR(36) NOT NULL, " +
                    "setting_key VARCHAR(64) NOT NULL, " +
                    "setting_value VARCHAR(128) NOT NULL, " +
                    "PRIMARY KEY (uuid, setting_key));";
            stmt.execute(settingsSql);

            String economySql = "CREATE TABLE IF NOT EXISTS player_economy (" +
                    "uuid VARCHAR(36) NOT NULL PRIMARY KEY, " +
                    "balance DOUBLE NOT NULL DEFAULT 0);";
            stmt.execute(economySql);

            String fishBagSql = "CREATE TABLE IF NOT EXISTS " + TABLE_PLAYER_FISH_BAG + " (" +
                    "uuid VARCHAR(36) NOT NULL PRIMARY KEY, " +
                    "contents TEXT);";
            stmt.execute(fishBagSql);

                String lavaBagSql = "CREATE TABLE IF NOT EXISTS " + TABLE_PLAYER_LAVA_BAG + " (" +
                    "uuid VARCHAR(36) NOT NULL PRIMARY KEY, " +
                    "contents TEXT);";
                stmt.execute(lavaBagSql);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Fish Rework] Failed to initialize database tables", e);
        }
        } // end synchronized
    }

    public void updateCollection(UUID uuid, String mobId, double weight) {
        synchronized (dbLock) {
        if (!isConnected()) return;
        String sql = "INSERT INTO fish_collection (uuid, mob_id, count, max_weight) VALUES (?, ?, 1, ?) " +
                     "ON CONFLICT(uuid, mob_id) DO UPDATE SET " +
                     "count = count + 1, " +
                     "max_weight = MAX(max_weight, ?);";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, mobId);
            ps.setDouble(3, weight);
            ps.setDouble(4, weight);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Fish Rework] Failed to update fish collection for " + uuid, e);
        }
        } // end synchronized
    }

    // ── Artifact Collection ──

    public void saveArtifact(UUID uuid, String artifactId) {
        synchronized (dbLock) {
        if (!isConnected()) return;
        String sql = "INSERT OR IGNORE INTO artifact_collection (uuid, artifact_id) VALUES (?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, artifactId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Fish Rework] Failed to save artifact for " + uuid, e);
        }
        } // end synchronized
    }

    public java.util.Set<String> loadArtifacts(UUID uuid) {
        synchronized (dbLock) {
        java.util.Set<String> artifacts = new java.util.HashSet<>();
        if (!isConnected()) return artifacts;
        String sql = "SELECT artifact_id FROM artifact_collection WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                artifacts.add(rs.getString("artifact_id"));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Fish Rework] Failed to load artifacts for " + uuid, e);
        }
        return artifacts;
        } // end synchronized
    }
    
    // Returns a map of MobID -> {Count, MaxWeight}
    public java.util.Map<String, double[]> loadCollection(UUID uuid) {
        synchronized (dbLock) {
        java.util.Map<String, double[]> collection = new java.util.HashMap<>();
        if (!isConnected()) return collection;
        String sql = "SELECT mob_id, count, max_weight FROM fish_collection WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                collection.put(rs.getString("mob_id"), new double[]{rs.getInt("count"), rs.getDouble("max_weight")});
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Fish Rework] Failed to load fish collection for " + uuid, e);
        }
        return collection;
        } // end synchronized
    }

    public void saveSetting(UUID uuid, String key, String value) {
        synchronized (dbLock) {
        if (!isConnected()) return;
        String sql = "INSERT OR REPLACE INTO player_settings (uuid, setting_key, setting_value) VALUES (?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, key);
            ps.setString(3, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Fish Rework] Failed to save setting for " + uuid, e);
        }
        } // end synchronized
    }

    public java.util.Map<String, String> loadSettings(UUID uuid) {
        synchronized (dbLock) {
        java.util.Map<String, String> settings = new java.util.HashMap<>();
        if (!isConnected()) return settings;
        String sql = "SELECT setting_key, setting_value FROM player_settings WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                settings.put(rs.getString("setting_key"), rs.getString("setting_value"));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Fish Rework] Failed to load settings for " + uuid, e);
        }
        return settings;
        } // end synchronized
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public PlayerData loadPlayer(UUID uuid) {
        synchronized (dbLock) {
        PlayerData data = new PlayerData(uuid);
        if (!isConnected()) return data;
        String sql = "SELECT skill, xp, level FROM player_skills WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                try {
                    Skill skill = Skill.valueOf(rs.getString("skill"));
                    data.setXp(skill, rs.getDouble("xp"));
                    data.setLevel(skill, rs.getInt("level"));
                } catch (IllegalArgumentException e) {
                    // Ignore unknown skills (e.g. if removed)
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Fish Rework] Failed to load player data for " + uuid, e);
        }

        // Load collection (still within the same lock scope)
        java.util.Map<String, double[]> collection = loadCollectionInternal(uuid);
        for (String mobId : collection.keySet()) {
            data.addCaughtMob(mobId);
        }
        java.util.Set<String> artifacts = loadArtifactsInternal(uuid);
        for (String artifactId : artifacts) {
            data.addArtifact(artifactId);
        }
        java.util.Map<String, String> settings = loadSettingsInternal(uuid);
        if (settings.containsKey("dmg_indicator")) {
            data.setDamageIndicatorsEnabled(Boolean.parseBoolean(settings.get("dmg_indicator")));
        }
        if (settings.containsKey("auto_sell_mode")) {
            AutoSellMode mode = AutoSellMode.fromInput(settings.get("auto_sell_mode"));
            if (mode != null) {
                data.getSession().setAutoSellMode(mode);
            }
        } else if (settings.containsKey("auto_sell")) {
            boolean enabled = Boolean.parseBoolean(settings.get("auto_sell"));
            data.getSession().setAutoSellMode(enabled ? AutoSellMode.OTHER : AutoSellMode.OFF);
        }
        if (settings.containsKey("tips_notifications")) {
            data.setFishingTipsEnabled(Boolean.parseBoolean(settings.get("tips_notifications")));
        }
        if (settings.containsKey("language")) {
            data.setLanguageLocale(settings.get("language"));
        } else if (settings.containsKey("locale")) {
            data.setLanguageLocale(settings.get("locale"));
        }
        if (settings.containsKey("particle_mode")) {
            ParticleDetailMode mode = ParticleDetailMode.fromInput(settings.get("particle_mode"));
            if (mode != null) {
                data.setParticleDetailMode(mode);
            }
        }
        if (settings.containsKey("sea_creature_message_mode")) {
            SeaCreatureMessageMode mode = SeaCreatureMessageMode.fromInput(settings.get("sea_creature_message_mode"));
            if (mode != null) {
                data.setSeaCreatureMessageMode(mode);
            }
        }
        loadBalanceInternal(uuid, data);
        loadFishBagInternal(uuid, data);
        loadLavaBagInternal(uuid, data);
        return data;
        } // end synchronized
    }

    public java.util.LinkedHashMap<UUID, Integer> getTopPlayers(Skill skill, int limit) {
        synchronized (dbLock) {
        java.util.LinkedHashMap<UUID, Integer> top = new java.util.LinkedHashMap<>();
        if (!isConnected()) return top;
        String sql = "SELECT uuid, level FROM player_skills WHERE skill = ? ORDER BY level DESC, xp DESC LIMIT ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, skill.name());
            ps.setInt(2, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                top.put(UUID.fromString(rs.getString("uuid")), rs.getInt("level"));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Fish Rework] Failed to load top players for " + skill, e);
        }
        return top;
        } // end synchronized
    }

    public void resetData(UUID uuid) {
        synchronized (dbLock) {
        if (!isConnected()) return;
        String sqlSkills    = "DELETE FROM player_skills WHERE uuid = ?";
        String sqlCollection = "DELETE FROM fish_collection WHERE uuid = ?";
        String sqlArtifacts = "DELETE FROM artifact_collection WHERE uuid = ?";
        String sqlEconomy   = "DELETE FROM player_economy WHERE uuid = ?";
        String sqlFishBag   = "DELETE FROM " + TABLE_PLAYER_FISH_BAG + " WHERE uuid = ?";
        String sqlLavaBag   = "DELETE FROM " + TABLE_PLAYER_LAVA_BAG + " WHERE uuid = ?";
        String sqlSettings  = "DELETE FROM player_settings WHERE uuid = ?";

        try {
            connection.setAutoCommit(false);

            for (String sql : new String[]{sqlSkills, sqlCollection, sqlArtifacts,
                    sqlEconomy, sqlFishBag, sqlLavaBag, sqlSettings}) {
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setString(1, uuid.toString());
                    ps.executeUpdate();
                }
            }

            connection.commit();
        } catch (SQLException e) {
            try { connection.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            plugin.getLogger().log(Level.SEVERE, "[Fish Rework] Failed to reset data for " + uuid, e);
        } finally {
            try { connection.setAutoCommit(true); } catch (SQLException e) { e.printStackTrace(); }
        }
        } // end synchronized
    }
    
    public void savePlayer(PlayerData data) {
        synchronized (dbLock) {
        if (!isConnected()) return;
        String sql = "INSERT OR REPLACE INTO player_skills (uuid, skill, xp, level) VALUES (?, ?, ?, ?)";
        try {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                for (Skill skill : Skill.values()) {
                    ps.setString(1, data.getUuid().toString());
                    ps.setString(2, skill.name());
                    ps.setDouble(3, data.getXp(skill));
                    ps.setInt(4, data.getLevel(skill));
                    ps.addBatch();
                }
                ps.executeBatch();
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Fish Rework] Failed to save player data for " + data.getUuid(), e);
        }
        saveBalanceInternal(data.getUuid(), data.getBalance());
        saveFishBagInternal(data.getUuid(), data.getFishBagContents());
        saveLavaBagInternal(data.getUuid(), data.getLavaBagContents());
        } // end synchronized
    }

    // ── Economy ──

    /** Public method — acquires lock then delegates. */
    public void saveBalance(UUID uuid, double balance) {
        synchronized (dbLock) { saveBalanceInternal(uuid, balance); }
    }

    private void saveBalanceInternal(UUID uuid, double balance) {
        if (!isConnected()) return;
        String sql = "INSERT OR REPLACE INTO player_economy (uuid, balance) VALUES (?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setDouble(2, balance);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Fish Rework] Failed to save balance for " + uuid, e);
        }
    }

    private void loadBalanceInternal(UUID uuid, PlayerData data) {
        if (!isConnected()) return;
        String sql = "SELECT balance FROM player_economy WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                data.setBalance(rs.getDouble("balance"));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Fish Rework] Failed to load balance for " + uuid, e);
        }
    }

    // ── Fish Bag ──

    /** Public method — acquires lock then delegates. */
    public void saveFishBag(UUID uuid, org.bukkit.inventory.ItemStack[] contents) {
        synchronized (dbLock) { saveFishBagInternal(uuid, contents); }
    }

    private void saveFishBagInternal(UUID uuid, org.bukkit.inventory.ItemStack[] contents) {
        saveBagInternal(uuid, contents, TABLE_PLAYER_FISH_BAG, BAG_LABEL_FISH);
    }

    private void loadFishBagInternal(UUID uuid, PlayerData data) {
        org.bukkit.inventory.ItemStack[] loaded = loadBagContentsInternal(uuid, TABLE_PLAYER_FISH_BAG, BAG_LABEL_FISH);
        if (loaded != null) {
            data.setFishBagContents(loaded);
        }
    }

    // ── Lava Bag ──

    /** Public method — acquires lock then delegates. */
    public void saveLavaBag(UUID uuid, org.bukkit.inventory.ItemStack[] contents) {
        synchronized (dbLock) { saveLavaBagInternal(uuid, contents); }
    }

    private void saveLavaBagInternal(UUID uuid, org.bukkit.inventory.ItemStack[] contents) {
        saveBagInternal(uuid, contents, TABLE_PLAYER_LAVA_BAG, BAG_LABEL_LAVA);
    }

    private void loadLavaBagInternal(UUID uuid, PlayerData data) {
        org.bukkit.inventory.ItemStack[] loaded = loadBagContentsInternal(uuid, TABLE_PLAYER_LAVA_BAG, BAG_LABEL_LAVA);
        if (loaded != null) {
            data.setLavaBagContents(loaded);
        }
    }

    private void saveBagInternal(UUID uuid, org.bukkit.inventory.ItemStack[] contents,
                                 String tableName, String bagLabel) {
        if (!isConnected()) return;
        String sql = "INSERT OR REPLACE INTO " + tableName + " (uuid, contents) VALUES (?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            if (contents == null) {
                ps.setNull(2, Types.VARCHAR);
            } else {
                byte[] bytes = serializeItemStacks(contents);
                ps.setString(2, Base64.getEncoder().encodeToString(bytes));
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Fish Rework] Failed to save " + bagLabel + " for " + uuid, e);
        }
    }

    private org.bukkit.inventory.ItemStack[] loadBagContentsInternal(UUID uuid, String tableName, String bagLabel) {
        if (!isConnected()) return null;
        String sql = "SELECT contents FROM " + tableName + " WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                return null;
            }

            String b64 = rs.getString("contents");
            if (b64 == null || b64.isEmpty()) {
                return null;
            }
            byte[] bytes = Base64.getDecoder().decode(b64);
            return deserializeItemStacks(bytes);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Fish Rework] Failed to load " + bagLabel + " for " + uuid, e);
            return null;
        }
    }

    // ── Internal helpers (no-lock, must be called within synchronized block) ──

    private java.util.Map<String, double[]> loadCollectionInternal(UUID uuid) {
        java.util.Map<String, double[]> collection = new java.util.HashMap<>();
        if (!isConnected()) return collection;
        String sql = "SELECT mob_id, count, max_weight FROM fish_collection WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                collection.put(rs.getString("mob_id"), new double[]{rs.getInt("count"), rs.getDouble("max_weight")});
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Fish Rework] Failed to load fish collection for " + uuid, e);
        }
        return collection;
    }

    private java.util.Set<String> loadArtifactsInternal(UUID uuid) {
        java.util.Set<String> artifacts = new java.util.HashSet<>();
        if (!isConnected()) return artifacts;
        String sql = "SELECT artifact_id FROM artifact_collection WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) { artifacts.add(rs.getString("artifact_id")); }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Fish Rework] Failed to load artifacts (internal) for " + uuid, e);
        }
        return artifacts;
    }

    private java.util.Map<String, String> loadSettingsInternal(UUID uuid) {
        java.util.Map<String, String> settings = new java.util.HashMap<>();
        if (!isConnected()) return settings;
        String sql = "SELECT setting_key, setting_value FROM player_settings WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                settings.put(rs.getString("setting_key"), rs.getString("setting_value"));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Fish Rework] Failed to load settings (internal) for " + uuid, e);
        }
        return settings;
    }

    /**
     * Serializes an ItemStack array to bytes using Bukkit serialization.
     */
    private byte[] serializeItemStacks(org.bukkit.inventory.ItemStack[] items) {
        try {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            org.bukkit.util.io.BukkitObjectOutputStream oos = new org.bukkit.util.io.BukkitObjectOutputStream(baos);
            oos.writeInt(items.length);
            for (org.bukkit.inventory.ItemStack item : items) {
                oos.writeObject(item);
            }
            oos.close();
            return baos.toByteArray();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to serialize fish bag contents", e);
            return new byte[0];
        }
    }

    /**
     * Deserializes an ItemStack array from bytes.
     */
    private org.bukkit.inventory.ItemStack[] deserializeItemStacks(byte[] data) {
        try {
            java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(data);
            org.bukkit.util.io.BukkitObjectInputStream ois = new org.bukkit.util.io.BukkitObjectInputStream(bais);
            int size = ois.readInt();
            org.bukkit.inventory.ItemStack[] items = new org.bukkit.inventory.ItemStack[size];
            for (int i = 0; i < size; i++) {
                items[i] = (org.bukkit.inventory.ItemStack) ois.readObject();
            }
            ois.close();
            return items;
        } catch (ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to deserialize fish bag contents (unknown class - possible version mismatch)", e);
            return null;
        } catch (java.io.IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to deserialize fish bag contents (I/O error)", e);
            return null;
        }
    }
}
