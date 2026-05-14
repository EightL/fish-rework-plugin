package com.fishrework.economy;

public record EconomyResult(boolean success, double amount, double balance, String message) {

    public static EconomyResult success(double amount, double balance) {
        return new EconomyResult(true, amount, balance, "");
    }

    public static EconomyResult failure(String message) {
        return new EconomyResult(false, 0.0, 0.0, message == null || message.isBlank() ? "Unknown economy error" : message);
    }
}
