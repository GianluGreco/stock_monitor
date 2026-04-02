package it.stockmonitor.model;

import java.time.LocalDateTime;

/**
 * Segnale composito generato dall'analisi tecnica di un titolo.
 * Lo score va da -4 (STRONG SELL) a +4 (STRONG BUY).
 */
public class Signal {

    public enum Type {
        STRONG_BUY("⬆⬆ STRONG BUY"),
        BUY("⬆ BUY"),
        HOLD("➡ HOLD"),
        SELL("⬇ SELL"),
        STRONG_SELL("⬇⬇ STRONG SELL"),
        INSUFFICIENT_DATA("⏳ DATI INSUFFICIENTI");

        private final String label;
        Type(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    private final String symbol;
    private final double price;
    private final LocalDateTime timestamp;
    private final int score;           // da -4 a +4
    private final Type type;

    // Dettaglio per indicatore (per trasparenza nel foglio Excel)
    private final String rsiDetail;
    private final String macdDetail;
    private final String smaDetail;
    private final String bollingerDetail;
    private final double rsiValue;

    public Signal(String symbol, double price, LocalDateTime timestamp,
                  int score,
                  String rsiDetail, String macdDetail,
                  String smaDetail, String bollingerDetail,
                  double rsiValue) {
        this.symbol          = symbol;
        this.price           = price;
        this.timestamp       = timestamp;
        this.score           = score;
        this.rsiDetail       = rsiDetail;
        this.macdDetail      = macdDetail;
        this.smaDetail       = smaDetail;
        this.bollingerDetail = bollingerDetail;
        this.rsiValue        = rsiValue;
        this.type            = scoreToType(score);
    }

    /** Segnale speciale quando non ci sono abbastanza dati storici. */
    public static Signal insufficientData(String symbol, double price,
                                          LocalDateTime timestamp, int dataPoints) {
        return new Signal(symbol, price, timestamp, 0,
                "Dati: " + dataPoints + " (min 14)", "-", "-", "-", 0);
    }

    private Type scoreToType(int score) {
        if (score >= 3)  return Type.STRONG_BUY;
        if (score >= 1)  return Type.BUY;
        if (score == 0)  return Type.HOLD;
        if (score >= -2) return Type.SELL;
        return Type.STRONG_SELL;
    }

    public String getSymbol()          { return symbol; }
    public double getPrice()           { return price; }
    public LocalDateTime getTimestamp(){ return timestamp; }
    public int getScore()              { return score; }
    public Type getType()              { return type; }
    public String getRsiDetail()       { return rsiDetail; }
    public String getMacdDetail()      { return macdDetail; }
    public String getSmaDetail()       { return smaDetail; }
    public String getBollingerDetail() { return bollingerDetail; }
    public double getRsiValue()        { return rsiValue; }

    @Override
    public String toString() {
        return String.format("[%s] %s | Score: %+d | RSI: %s | MACD: %s | SMA: %s | BB: %s",
                symbol, type.getLabel(), score, rsiDetail, macdDetail, smaDetail, bollingerDetail);
    }
}
