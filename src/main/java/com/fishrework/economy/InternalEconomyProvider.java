package com.fishrework.economy;

import com.fishrework.FishRework;
import com.fishrework.model.PlayerData;
import com.fishrework.util.FormatUtil;
import org.bukkit.entity.Player;

public final class InternalEconomyProvider implements EconomyProvider {

    private final FishRework plugin;

    public InternalEconomyProvider(FishRework plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return "internal";
    }

    @Override
    public String getDisplayName() {
        return "Fish Rework";
    }

    @Override
    public boolean isExternal() {
        return false;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public double getBalance(Player player) {
        PlayerData data = getData(player);
        return data != null ? data.getBalance() : 0.0;
    }

    @Override
    public EconomyResult deposit(Player player, double amount) {
        PlayerData data = getData(player);
        if (data == null) {
            return EconomyResult.failure("Player data is not loaded.");
        }
        double before = data.getBalance();
        if (before + amount > PlayerData.DEFAULT_MAX_BALANCE + 0.000001) {
            return EconomyResult.failure("Balance limit would be exceeded.");
        }
        data.addBalance(amount);
        plugin.getDatabaseManager().saveBalance(player.getUniqueId(), data.getBalance());
        return EconomyResult.success(data.getBalance() - before, data.getBalance());
    }

    @Override
    public EconomyResult withdraw(Player player, double amount) {
        PlayerData data = getData(player);
        if (data == null) {
            return EconomyResult.failure("Player data is not loaded.");
        }
        if (!data.deductBalance(amount)) {
            return EconomyResult.failure("Insufficient balance.");
        }
        plugin.getDatabaseManager().saveBalance(player.getUniqueId(), data.getBalance());
        return EconomyResult.success(amount, data.getBalance());
    }

    @Override
    public EconomyResult setBalance(Player player, double amount) {
        PlayerData data = getData(player);
        if (data == null) {
            return EconomyResult.failure("Player data is not loaded.");
        }
        data.setBalance(amount);
        plugin.getDatabaseManager().saveBalance(player.getUniqueId(), data.getBalance());
        return EconomyResult.success(amount, data.getBalance());
    }

    @Override
    public String getCurrencyName() {
        String configured = plugin.getConfig().getString("economy.currency_name", "Doubloons");
        return configured == null || configured.isBlank() ? "Doubloons" : configured.trim();
    }

    @Override
    public String format(double amount) {
        return FormatUtil.format("%.0f", amount) + " " + getCurrencyName();
    }

    private PlayerData getData(Player player) {
        return player == null ? null : plugin.getPlayerData(player.getUniqueId());
    }
}
