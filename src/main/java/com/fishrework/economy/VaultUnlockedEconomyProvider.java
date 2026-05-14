package com.fishrework.economy;

import com.fishrework.FishRework;
import com.fishrework.util.FormatUtil;
import net.milkbowl.vault2.economy.Economy;
import net.milkbowl.vault2.economy.EconomyResponse;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.math.BigDecimal;
import java.util.UUID;

public final class VaultUnlockedEconomyProvider implements EconomyProvider {

    private final FishRework plugin;
    private final Economy economy;
    private final String pluginName;
    private final String currency;
    private final boolean useConfiguredCurrency;
    private final boolean useWorldAccounts;

    public VaultUnlockedEconomyProvider(FishRework plugin) {
        this.plugin = plugin;
        this.pluginName = plugin.getDescription().getName();
        RegisteredServiceProvider<Economy> registration = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        this.economy = registration != null ? registration.getProvider() : null;
        String configuredCurrency = plugin.getConfig().getString("economy.vault_unlocked.currency", "");
        this.currency = configuredCurrency == null ? "" : configuredCurrency.trim();
        this.useConfiguredCurrency = !this.currency.isBlank();
        this.useWorldAccounts = plugin.getConfig().getBoolean("economy.vault_unlocked.use_world_accounts", false);
    }

    @Override
    public String getId() {
        return "vault_unlocked";
    }

    @Override
    public String getDisplayName() {
        return economy != null ? "VaultUnlocked (" + economy.getName() + ")" : "VaultUnlocked";
    }

    @Override
    public boolean isExternal() {
        return true;
    }

    @Override
    public boolean isAvailable() {
        return economy != null && economy.isEnabled();
    }

    @Override
    public double getBalance(Player player) {
        if (!isAvailable() || player == null) {
            return 0.0;
        }
        ensureAccount(player);
        BigDecimal balance = useConfiguredCurrency
                ? economy.balance(pluginName, player.getUniqueId(), worldName(player), currency)
                : useWorldAccounts
                        ? economy.balance(pluginName, player.getUniqueId(), worldName(player))
                        : economy.balance(pluginName, player.getUniqueId());
        return balance.doubleValue();
    }

    @Override
    public EconomyResult deposit(Player player, double amount) {
        if (!isAvailable()) {
            return EconomyResult.failure("VaultUnlocked economy provider is not available.");
        }
        if (player == null) {
            return EconomyResult.failure("Player is not available.");
        }
        ensureAccount(player);
        BigDecimal value = BigDecimal.valueOf(amount);
        EconomyResponse response = useConfiguredCurrency
                ? economy.deposit(pluginName, player.getUniqueId(), worldName(player), currency, value)
                : useWorldAccounts
                        ? economy.deposit(pluginName, player.getUniqueId(), worldName(player), value)
                        : economy.deposit(pluginName, player.getUniqueId(), value);
        return fromResponse(response);
    }

    @Override
    public EconomyResult deposit(UUID uuid, String playerName, double amount) {
        if (!isAvailable()) {
            return EconomyResult.failure("VaultUnlocked economy provider is not available.");
        }
        if (uuid == null) {
            return EconomyResult.failure("Player UUID is not available.");
        }
        ensureAccount(uuid, playerName);
        BigDecimal value = BigDecimal.valueOf(amount);
        String world = plugin.getServer().getWorlds().isEmpty() ? null : plugin.getServer().getWorlds().get(0).getName();
        EconomyResponse response = useConfiguredCurrency
                ? economy.deposit(pluginName, uuid, world, currency, value)
                : useWorldAccounts
                        ? economy.deposit(pluginName, uuid, world, value)
                        : economy.deposit(pluginName, uuid, value);
        return fromResponse(response);
    }

    @Override
    public EconomyResult withdraw(Player player, double amount) {
        if (!isAvailable()) {
            return EconomyResult.failure("VaultUnlocked economy provider is not available.");
        }
        if (player == null) {
            return EconomyResult.failure("Player is not available.");
        }
        ensureAccount(player);
        BigDecimal value = BigDecimal.valueOf(amount);
        EconomyResponse response = useConfiguredCurrency
                ? economy.withdraw(pluginName, player.getUniqueId(), worldName(player), currency, value)
                : useWorldAccounts
                        ? economy.withdraw(pluginName, player.getUniqueId(), worldName(player), value)
                        : economy.withdraw(pluginName, player.getUniqueId(), value);
        return fromResponse(response);
    }

    @Override
    public EconomyResult setBalance(Player player, double amount) {
        BigDecimal current = BigDecimal.valueOf(getBalance(player));
        BigDecimal target = BigDecimal.valueOf(amount);
        int compare = current.compareTo(target);
        if (compare == 0) {
            return EconomyResult.success(0.0, current.doubleValue());
        }
        return compare < 0
                ? deposit(player, target.subtract(current).doubleValue())
                : withdraw(player, current.subtract(target).doubleValue());
    }

    @Override
    public String getCurrencyName() {
        String configuredName = plugin.getConfig().getString("economy.vault_unlocked.currency_name", "");
        if (configuredName != null && !configuredName.isBlank()) {
            return configuredName.trim();
        }
        if (economy != null) {
            if (useConfiguredCurrency) {
                return currency;
            }
            String plural = economy.defaultCurrencyNamePlural(pluginName);
            if (plural != null && !plural.isBlank()) {
                return plural;
            }
        }
        return "currency";
    }

    @Override
    public String format(double amount) {
        if (economy != null) {
            BigDecimal value = BigDecimal.valueOf(amount);
            String formatted = useConfiguredCurrency
                    ? economy.format(pluginName, value, currency)
                    : economy.format(pluginName, value);
            if (formatted != null && !formatted.isBlank()) {
                return formatted;
            }
        }
        return FormatUtil.format("%.2f", amount) + " " + getCurrencyName();
    }

    private void ensureAccount(Player player) {
        if (economy == null || player == null) {
            return;
        }
        if (!economy.hasAccount(player.getUniqueId())) {
            economy.createAccount(player.getUniqueId(), player.getName(), true);
        }
    }

    private void ensureAccount(UUID uuid, String playerName) {
        if (economy == null || uuid == null) {
            return;
        }
        if (!economy.hasAccount(uuid)) {
            economy.createAccount(uuid, playerName == null || playerName.isBlank() ? uuid.toString() : playerName, true);
        }
    }

    private String worldName(Player player) {
        return player.getWorld().getName();
    }

    private EconomyResult fromResponse(EconomyResponse response) {
        if (response == null) {
            return EconomyResult.failure("VaultUnlocked returned no transaction response.");
        }
        if (!response.transactionSuccess()) {
            return EconomyResult.failure(response.errorMessage);
        }
        return EconomyResult.success(response.amount.doubleValue(), response.balance.doubleValue());
    }
}
