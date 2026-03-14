package com.woodspixelbazaar.model;

public class TransactionRecord {
    private final long timestamp;
    private final String playerName;
    private final String itemId;
    private final String type;
    private final int amount;
    private final double unitPrice;
    private final double totalPrice;

    public TransactionRecord(long timestamp, String playerName, String itemId, String type, int amount, double unitPrice, double totalPrice) {
        this.timestamp = timestamp;
        this.playerName = playerName;
        this.itemId = itemId;
        this.type = type;
        this.amount = amount;
        this.unitPrice = unitPrice;
        this.totalPrice = totalPrice;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getItemId() {
        return itemId;
    }

    public String getType() {
        return type;
    }

    public int getAmount() {
        return amount;
    }

    public double getUnitPrice() {
        return unitPrice;
    }

    public double getTotalPrice() {
        return totalPrice;
    }
}
