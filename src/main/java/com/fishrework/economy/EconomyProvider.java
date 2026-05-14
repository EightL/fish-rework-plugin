package com.fishrework.economy;

import org.bukkit.entity.Player;

public interface EconomyProvider {

    String getId();

    String getDisplayName();

    boolean isExternal();

    boolean isAvailable();

    double getBalance(Player player);

    EconomyResult deposit(Player player, double amount);

    EconomyResult withdraw(Player player, double amount);

    EconomyResult setBalance(Player player, double amount);

    String getCurrencyName();

    String format(double amount);
}
