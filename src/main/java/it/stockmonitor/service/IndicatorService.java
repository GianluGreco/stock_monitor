package it.stockmonitor.service;

import java.util.List;

/**
 * Calcola gli indicatori tecnici a partire da una serie storica di prezzi.
 *
 * Tutti i metodi sono stateless e ricevono i prezzi come parametro,
 * dal più vecchio [0] al più recente [n-1].
 */
public class IndicatorService {

    // =========================================================================
    // RSI — Relative Strength Index (periodo 14)
    // =========================================================================

    public static final int RSI_PERIOD = 14;

    /**
     * Calcola l'RSI sull'ultimo punto della serie.
     * Richiede almeno RSI_PERIOD + 1 prezzi.
     *
     * @return valore RSI [0-100], oppure -1 se dati insufficienti
     */
    public double rsi(List<Double> prices) {
        if (prices.size() < RSI_PERIOD + 1) return -1;

        // Usiamo gli ultimi RSI_PERIOD+1 prezzi
        int start = prices.size() - RSI_PERIOD - 1;
        double avgGain = 0, avgLoss = 0;

        for (int i = start + 1; i <= start + RSI_PERIOD; i++) {
            double delta = prices.get(i) - prices.get(i - 1);
            if (delta > 0) avgGain += delta;
            else           avgLoss += Math.abs(delta);
        }

        avgGain /= RSI_PERIOD;
        avgLoss /= RSI_PERIOD;

        if (avgLoss == 0) return 100;

        double rs = avgGain / avgLoss;
        return 100 - (100 / (1 + rs));
    }

    // =========================================================================
    // SMA — Simple Moving Average
    // =========================================================================

    /**
     * Calcola la SMA degli ultimi 'period' prezzi.
     *
     * @return media, oppure Double.NaN se dati insufficienti
     */
    public double sma(List<Double> prices, int period) {
        if (prices.size() < period) return Double.NaN;

        double sum = 0;
        int from = prices.size() - period;
        for (int i = from; i < prices.size(); i++) {
            sum += prices.get(i);
        }
        return sum / period;
    }

    /**
     * Verifica il crossover tra SMA veloce (9) e SMA lenta (21).
     *
     * @return  1 = golden cross (SMA9 supera SMA21, segnale BUY)
     *         -1 = death cross  (SMA9 scende sotto SMA21, segnale SELL)
     *          0 = nessun crossover, o dati insufficienti
     */
    public int smaCrossover(List<Double> prices) {
        int FAST = 9, SLOW = 21;
        if (prices.size() < SLOW + 1) return 0;

        // SMA corrente e precedente per entrambe le medie
        List<Double> prev = prices.subList(0, prices.size() - 1);
        List<Double> curr = prices;

        double fastNow  = sma(curr, FAST);
        double slowNow  = sma(curr, SLOW);
        double fastPrev = sma(prev, FAST);
        double slowPrev = sma(prev, SLOW);

        if (Double.isNaN(fastNow) || Double.isNaN(slowNow)
         || Double.isNaN(fastPrev) || Double.isNaN(slowPrev)) return 0;

        boolean crossUp   = fastPrev <= slowPrev && fastNow > slowNow;
        boolean crossDown = fastPrev >= slowPrev && fastNow < slowNow;

        if (crossUp)   return 1;
        if (crossDown) return -1;
        return 0;
    }

    // =========================================================================
    // MACD — Moving Average Convergence Divergence (12, 26, 9)
    // =========================================================================

    private static final int MACD_FAST   = 12;
    private static final int MACD_SLOW   = 26;
    private static final int MACD_SIGNAL =  9;

    /**
     * Calcola la linea MACD e la linea segnale usando EMA.
     * Richiede almeno MACD_SLOW + MACD_SIGNAL prezzi.
     *
     * @return array [macdLine, signalLine], oppure null se dati insufficienti
     */
    public double[] macd(List<Double> prices) {
        int required = MACD_SLOW + MACD_SIGNAL;
        if (prices.size() < required) return null;

        // Calcola la serie MACD (EMA12 - EMA26) su tutti i punti disponibili
        int n = prices.size();
        double[] macdLine = new double[n - MACD_SLOW + 1];

        for (int i = MACD_SLOW - 1; i < n; i++) {
            double emaFast = ema(prices, i, MACD_FAST);
            double emaSlow = ema(prices, i, MACD_SLOW);
            macdLine[i - (MACD_SLOW - 1)] = emaFast - emaSlow;
        }

        // Linea segnale = EMA9 della linea MACD
        if (macdLine.length < MACD_SIGNAL) return null;

        double signal = 0;
        for (int i = 0; i < MACD_SIGNAL; i++) {
            signal += macdLine[macdLine.length - MACD_SIGNAL + i];
        }
        signal /= MACD_SIGNAL;

        return new double[]{ macdLine[macdLine.length - 1], signal };
    }

    /**
     * Verifica il crossover MACD rispetto alla linea segnale.
     *
     * @return  1 = MACD supera signal (BUY)
     *         -1 = MACD scende sotto signal (SELL)
     *          0 = nessun crossover o dati insufficienti
     */
    public int macdCrossover(List<Double> prices) {
        if (prices.size() < MACD_SLOW + MACD_SIGNAL + 1) return 0;

        double[] curr = macd(prices);
        double[] prev = macd(prices.subList(0, prices.size() - 1));

        if (curr == null || prev == null) return 0;

        boolean crossUp   = prev[0] <= prev[1] && curr[0] > curr[1];
        boolean crossDown = prev[0] >= prev[1] && curr[0] < curr[1];

        if (crossUp)   return  1;
        if (crossDown) return -1;
        return 0;
    }

    // =========================================================================
    // BOLLINGER BANDS (periodo 20, deviazione standard 2)
    // =========================================================================

    private static final int   BB_PERIOD = 20;
    private static final double BB_STD   =  2.0;

    /**
     * Calcola le bande di Bollinger.
     * Richiede almeno BB_PERIOD prezzi.
     *
     * @return array [upperBand, middleBand, lowerBand], oppure null se dati insufficienti
     */
    public double[] bollingerBands(List<Double> prices) {
        if (prices.size() < BB_PERIOD) return null;

        double middle = sma(prices, BB_PERIOD);
        int from = prices.size() - BB_PERIOD;
        double variance = 0;
        for (int i = from; i < prices.size(); i++) {
            variance += Math.pow(prices.get(i) - middle, 2);
        }
        double stdDev = Math.sqrt(variance / BB_PERIOD);

        return new double[]{
            middle + BB_STD * stdDev,  // upper
            middle,                    // middle
            middle - BB_STD * stdDev   // lower
        };
    }

    /**
     * Valuta la posizione del prezzo rispetto alle bande di Bollinger.
     *
     * @return  1 = prezzo sotto banda inferiore (BUY - ipervenduto)
     *         -1 = prezzo sopra banda superiore (SELL - ipercomprato)
     *          0 = prezzo nella banda o dati insufficienti
     */
    public int bollingerSignal(List<Double> prices) {
        double[] bands = bollingerBands(prices);
        if (bands == null || prices.isEmpty()) return 0;

        double lastPrice = prices.get(prices.size() - 1);
        if (lastPrice < bands[2]) return  1;  // sotto lower band
        if (lastPrice > bands[0]) return -1;  // sopra upper band
        return 0;
    }

    // =========================================================================
    // EMA — Exponential Moving Average (metodo di supporto)
    // =========================================================================

    /**
     * Calcola l'EMA di 'period' periodi fino all'indice 'endIndex' nella serie.
     */
    private double ema(List<Double> prices, int endIndex, int period) {
        int startIndex = endIndex - period + 1;
        if (startIndex < 0) return prices.get(endIndex);

        double k = 2.0 / (period + 1);
        double ema = prices.get(startIndex);
        for (int i = startIndex + 1; i <= endIndex; i++) {
            ema = prices.get(i) * k + ema * (1 - k);
        }
        return ema;
    }
}
