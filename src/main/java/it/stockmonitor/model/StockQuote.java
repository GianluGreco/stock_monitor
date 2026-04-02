package it.stockmonitor.model;

import java.time.LocalDateTime;

/**
 * Rappresenta una singola quotazione di un titolo azionario.
 */
public class StockQuote {

    private String symbol;       // Es. AVIO.MI
    private String name;         // Nome leggibile. Es. Avio S.p.A.
    private double price;        // Prezzo corrente
    private double change;       // Variazione assoluta rispetto alla chiusura precedente
    private double changePercent; // Variazione percentuale
    private LocalDateTime timestamp; // Momento della rilevazione

    public StockQuote(String symbol, String name, double price,
                      double change, double changePercent, LocalDateTime timestamp) {
        this.symbol = symbol;
        this.name = name;
        this.price = price;
        this.change = change;
        this.changePercent = changePercent;
        this.timestamp = timestamp;
    }

    public String getSymbol() { return symbol; }
    public String getName() { return name; }
    public double getPrice() { return price; }
    public double getChange() { return change; }
    public double getChangePercent() { return changePercent; }
    public LocalDateTime getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        return String.format("[%s] %s: %.4f EUR (%.2f%%) @ %s",
                symbol, name, price, changePercent, timestamp);
    }
}
