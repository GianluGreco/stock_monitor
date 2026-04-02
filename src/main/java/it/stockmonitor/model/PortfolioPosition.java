package it.stockmonitor.model;

/**
 * Rappresenta una posizione nel portafoglio personale.
 * Caricata da portfolio.properties.
 */
public class PortfolioPosition {

    private final String symbol;
    private final int    quantity;      // numero di azioni possedute
    private final double loadPrice;     // prezzo medio di carico (EUR)

    public PortfolioPosition(String symbol, int quantity, double loadPrice) {
        this.symbol    = symbol;
        this.quantity  = quantity;
        this.loadPrice = loadPrice;
    }

    public String getSymbol()    { return symbol; }
    public int    getQuantity()  { return quantity; }
    public double getLoadPrice() { return loadPrice; }

    /** Valore di carico totale della posizione. */
    public double totalCost() {
        return quantity * loadPrice;
    }

    /** Valore corrente della posizione al prezzo di mercato fornito. */
    public double currentValue(double marketPrice) {
        return quantity * marketPrice;
    }

    /** P&L assoluto in EUR. */
    public double pnl(double marketPrice) {
        return currentValue(marketPrice) - totalCost();
    }

    /** P&L percentuale rispetto al prezzo di carico. */
    public double pnlPercent(double marketPrice) {
        if (loadPrice == 0) return 0;
        return ((marketPrice - loadPrice) / loadPrice) * 100;
    }

    @Override
    public String toString() {
        return String.format("PortfolioPosition{%s qty=%d carico=%.4f}", symbol, quantity, loadPrice);
    }
}
