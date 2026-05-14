package com.fishrework.economy;

import com.fishrework.FishRework;
import com.fishrework.util.FormatUtil;
import org.bukkit.entity.Player;

import java.util.Locale;

public final class EconomyManager {

    private final FishRework plugin;
    private final EconomyProvider internalProvider;
    private EconomyProvider provider;

    public EconomyManager(FishRework plugin) {
        this.plugin = plugin;
        this.internalProvider = new InternalEconomyProvider(plugin);
        this.provider = internalProvider;
    }

    public void initialize() {
        provider = selectProvider();
        plugin.getLogger().info("[Fish Rework] Economy provider: " + provider.getDisplayName());
    }

    public void reload() {
        initialize();
    }

    public String getProviderId() {
        return provider.getId();
    }

    public String getProviderDisplayName() {
        return provider.getDisplayName();
    }

    public boolean isExternal() {
        return provider.isExternal();
    }

    public double getBalance(Player player) {
        return provider.getBalance(player);
    }

    public boolean canAfford(Player player, double amount) {
        return getBalance(player) + 0.000001 >= amount;
    }

    public EconomyResult deposit(Player player, double amount) {
        EconomyResult validation = validateAmount(amount);
        return validation != null ? validation : provider.deposit(player, amount);
    }

    public EconomyResult withdraw(Player player, double amount) {
        EconomyResult validation = validateAmount(amount);
        return validation != null ? validation : provider.withdraw(player, amount);
    }

    public EconomyResult setBalance(Player player, double amount) {
        if (!Double.isFinite(amount) || amount < 0.0) {
            return EconomyResult.failure("Amount must be a non-negative number.");
        }
        return provider.setBalance(player, amount);
    }

    public String getCurrencyName() {
        String name = provider.getCurrencyName();
        return name == null || name.isBlank() ? "currency" : name;
    }

    public String format(double amount) {
        String formatted = provider.format(amount);
        if (formatted != null && !formatted.isBlank()) {
            return formatted;
        }
        return FormatUtil.format("%.0f", amount) + " " + getCurrencyName();
    }

    public String transactionFailedMessage(EconomyResult result) {
        String reason = result != null && result.message() != null && !result.message().isBlank()
                ? result.message()
                : "Unknown economy error";
        return plugin.getLanguageManager().getString(
                "economy.transaction_failed",
                "Economy transaction failed: %reason%",
                "reason", reason);
    }

    private EconomyProvider selectProvider() {
        String configured = plugin.getConfig().getString("economy.provider", "internal");
        String mode = configured == null ? "internal" : configured.trim().toLowerCase(Locale.ROOT);
        boolean fallback = plugin.getConfig().getBoolean("economy.fallback_to_internal", true);

        EconomyProvider selected = switch (mode) {
            case "auto" -> firstAvailable(tryVaultUnlocked(), tryVault(), internalProvider);
            case "vault_unlocked", "vaultunlocked", "vault2" -> firstAvailable(tryVaultUnlocked(), fallback ? internalProvider : null);
            case "vault" -> firstAvailable(tryVault(), fallback ? internalProvider : null);
            case "internal", "doubloons", "fishrework" -> internalProvider;
            default -> {
                plugin.getLogger().warning("[Fish Rework] Unknown economy.provider '" + configured + "'. Using internal economy.");
                yield internalProvider;
            }
        };

        if (selected == null) {
            plugin.getLogger().warning("[Fish Rework] Configured economy provider '" + configured
                    + "' is unavailable and fallback_to_internal is false. Using internal economy for this session.");
            return internalProvider;
        }
        return selected;
    }

    private EconomyProvider firstAvailable(EconomyProvider... providers) {
        for (EconomyProvider candidate : providers) {
            if (candidate != null && candidate.isAvailable()) {
                return candidate;
            }
        }
        return null;
    }

    private EconomyProvider tryVault() {
        try {
            EconomyProvider vault = new VaultEconomyProvider(plugin);
            if (!vault.isAvailable()) {
                plugin.getLogger().warning("[Fish Rework] Vault economy provider was requested but no enabled Vault economy service is registered.");
            }
            return vault;
        } catch (Throwable t) {
            plugin.getLogger().warning("[Fish Rework] Vault economy provider is unavailable: " + t.getMessage());
            return null;
        }
    }

    private EconomyProvider tryVaultUnlocked() {
        try {
            EconomyProvider vaultUnlocked = new VaultUnlockedEconomyProvider(plugin);
            if (!vaultUnlocked.isAvailable()) {
                plugin.getLogger().warning("[Fish Rework] VaultUnlocked economy provider was requested but no enabled VaultUnlocked economy service is registered.");
            }
            return vaultUnlocked;
        } catch (Throwable t) {
            plugin.getLogger().warning("[Fish Rework] VaultUnlocked economy provider is unavailable: " + t.getMessage());
            return null;
        }
    }

    private EconomyResult validateAmount(double amount) {
        if (!Double.isFinite(amount) || amount <= 0.0) {
            return EconomyResult.failure("Amount must be greater than zero.");
        }
        return null;
    }
}
