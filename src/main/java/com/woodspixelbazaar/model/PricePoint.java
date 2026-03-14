package com.woodspixelbazaar.model;

public class PricePoint {
    private final long timestamp;
    private final double buyPrice;
    private final double sellPrice;

    public PricePoint(long timestamp, double buyPrice, double sellPrice) {
        this.timestamp = timestamp;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public double getBuyPrice() {
        return buyPrice;
    }

    public double getSellPrice() {
        return sellPrice;
    }
}
