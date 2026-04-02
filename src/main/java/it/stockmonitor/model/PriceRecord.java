package it.stockmonitor.model;

import java.time.LocalDateTime;

/**
 * Singolo punto storico di prezzo, persistito in JSON.
 */
public class PriceRecord {

    private String symbol;
    private double price;
    private double changePercent;
    private String timestamp; // ISO string per serializzazione JSON semplice

    // Costruttore vuoto richiesto da Jackson
    public PriceRecord() {}

    public PriceRecord(String symbol, double price, double changePercent, LocalDateTime timestamp) {
        this.symbol        = symbol;
        this.price         = price;
        this.changePercent = changePercent;
        this.timestamp     = timestamp.toString();
    }

    public String getSymbol()        { return symbol; }
    public double getPrice()         { return price; }
    public double getChangePercent() { return changePercent; }
    public String getTimestamp()     { return timestamp; }

    public void setSymbol(String symbol)               { this.symbol = symbol; }
    public void setPrice(double price)                 { this.price = price; }
    public void setChangePercent(double changePercent) { this.changePercent = changePercent; }
    public void setTimestamp(String timestamp)         { this.timestamp = timestamp; }

    public LocalDateTime getLocalDateTime() {
        return LocalDateTime.parse(timestamp);
    }
}
