package com.fishrework.economy;

import com.fishrework.FishRework;
import com.fishrework.util.FormatUtil;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class VaultEconomyProvider implements EconomyProvider {

    private final FishRework plugin;
    private final Economy economy;
    private final boolean useWorldAccounts;

    public VaultEconomyProvider(FishRework plugin) {
        this.plugin = plugin;
        RegisteredServiceProvider<Economy> registration = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        this.economy = registration != null ? registration.getProvider() : null;
        this.useWorldAccounts = plugin.getConfig().getBoolean("economy.vault.use_world_accounts", false);
    }

    @Override
    public String getId() {
        return "vault";
    }

    @Override
    public String getDisplayName() {
        return economy != null ? "Vault (" + economy.getName() + ")" : "Vault";
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
        return useWorldAccounts
                ? economy.getBalance(player, player.getWorld().getName())
                : economy.getBalance(player);
    }

    @Override
    public EconomyResult deposit(Player player, double amount) {
        if (!isAvailable()) {
            return EconomyResult.failure("Vault economy provider is not available.");
        }
        if (player == null) {
            return EconomyResult.failure("Player is not available.");
        }
        ensureAccount(player);
        EconomyResponse response = useWorldAccounts
                ? economy.depositPlayer(player, player.getWorld().getName(), amount)
                : economy.depositPlayer(player, amount);
        return fromResponse(response);
    }

    @Override
    public EconomyResult withdraw(Player player, double amount) {
        if (!isAvailable()) {
            return EconomyResult.failure("Vault economy provider is not available.");
        }
        if (player == null) {
            return EconomyResult.failure("Player is not available.");
        }
        ensureAccount(player);
        EconomyResponse response = useWorldAccounts
                ? economy.withdrawPlayer(player, player.getWorld().getName(), amount)
                : economy.withdrawPlayer(player, amount);
        return fromResponse(response);
    }

    @Override
    public EconomyResult setBalance(Player player, double amount) {
        double current = getBalance(player);
        double delta = amount - current;
        if (Math.abs(delta) < 0.000001) {
            return EconomyResult.success(0.0, current);
        }
        return delta > 0 ? deposit(player, delta) : withdraw(player, -delta);
    }

    @Override
    public String getCurrencyName() {
        String configured = plugin.getConfig().getString("economy.vault.currency_name", "");
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        if (economy != null) {
            String plural = economy.currencyNamePlural();
            if (plural != null && !plural.isBlank()) {
                return plural;
            }
        }
        return "currency";
    }

    @Override
    public String format(double amount) {
        if (economy != null) {
            String formatted = economy.format(amount);
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
        try {
            if (useWorldAccounts && !economy.hasAccount(player, player.getWorld().getName())) {
                economy.createPlayerAccount(player, player.getWorld().getName());
            } else if (!useWorldAccounts && !economy.hasAccount(player)) {
                economy.createPlayerAccount(player);
            }
        } catch (UnsupportedOperationException ignored) {
            // Some Vault providers create accounts lazily and do not implement explicit creation.
        }
    }

    private EconomyResult fromResponse(EconomyResponse response) {
        if (response == null) {
            return EconomyResult.failure("Vault returned no transaction response.");
        }
        if (!response.transactionSuccess()) {
            return EconomyResult.failure(response.errorMessage);
        }
        return EconomyResult.success(response.amount, response.balance);
    }
}
