package it.stockmonitor.service;

import it.stockmonitor.model.Signal;
import it.stockmonitor.model.StockQuote;

import java.util.List;
import java.util.logging.Logger;

/**
 * Genera un segnale composito BUY/SELL/HOLD per ogni titolo
 * combinando RSI, SMA crossover, MACD e Bollinger Bands.
 *
 * Score composito:
 *   Ogni indicatore contribuisce con -1, 0 o +1
 *   Totale: da -4 (STRONG SELL) a +4 (STRONG BUY)
 *
 *   >= +3 → STRONG BUY
 *   +1/+2 → BUY
 *      0  → HOLD
 *   -1/-2 → SELL
 *   <= -3 → STRONG SELL
 *
 * Dati minimi necessari: 35 rilevazioni (~12 giorni lavorativi)
 */
public class SignalService {

    private static final Logger logger = Logger.getLogger(SignalService.class.getName());

    // Minimo di dati storici necessari per generare segnali affidabili
    // MACD richiede 26+9=35 punti come minimo
    private static final int MIN_DATA_POINTS = 35;

    private final HistoryService historyService;
    private final IndicatorService indicators;

    public SignalService(HistoryService historyService) {
        this.historyService = historyService;
        this.indicators     = new IndicatorService();
    }

    /**
     * Genera il segnale composito per il titolo appena rilevato.
     *
     * @param quote quotazione più recente
     * @return Signal con tipo, score e dettaglio per ogni indicatore
     */
    public Signal generateSignal(StockQuote quote) {
        String symbol = quote.getSymbol();
        int totalRecords = historyService.countRecords(symbol);

        if (totalRecords < MIN_DATA_POINTS) {
            logger.info(symbol + ": dati insufficienti (" + totalRecords
                    + "/" + MIN_DATA_POINTS + "), segnale non disponibile.");
            return Signal.insufficientData(symbol, quote.getPrice(),
                    quote.getTimestamp(), totalRecords);
        }

        // Carica tutta la storia disponibile (più dati = EMA più accurate)
        List<Double> prices = historyService.loadLastPrices(symbol, totalRecords);

        // --- RSI ---
        double rsiValue = indicators.rsi(prices);
        int rsiScore;
        String rsiDetail;
        if (rsiValue < 0) {
            rsiScore  = 0;
            rsiDetail = "N/D";
        } else if (rsiValue < 30) {
            rsiScore  =  1;
            rsiDetail = String.format("%.1f ↑ (ipervenduto)", rsiValue);
        } else if (rsiValue > 70) {
            rsiScore  = -1;
            rsiDetail = String.format("%.1f ↓ (ipercomprato)", rsiValue);
        } else {
            rsiScore  = 0;
            rsiDetail = String.format("%.1f → (neutro)", rsiValue);
        }

        // --- SMA Crossover 9/21 ---
        int smaCross = indicators.smaCrossover(prices);
        int smaScore;
        String smaDetail;
        switch (smaCross) {
            case  1: smaScore =  1; smaDetail = "Golden cross ↑ (SMA9 > SMA21)"; break;
            case -1: smaScore = -1; smaDetail = "Death cross ↓ (SMA9 < SMA21)";  break;
            default: smaScore =  0; smaDetail = "Nessun crossover →";             break;
        }

        // --- MACD ---
        int macdCross = indicators.macdCrossover(prices);
        int macdScore;
        String macdDetail;
        double[] macdValues = indicators.macd(prices);
        String macdRaw = macdValues != null
                ? String.format("MACD=%.4f Signal=%.4f", macdValues[0], macdValues[1])
                : "";
        switch (macdCross) {
            case  1: macdScore =  1; macdDetail = "Crossover rialzista ↑ " + macdRaw; break;
            case -1: macdScore = -1; macdDetail = "Crossover ribassista ↓ " + macdRaw; break;
            default: macdScore =  0; macdDetail = "Nessun crossover → " + macdRaw;     break;
        }

        // --- Bollinger Bands ---
        int bbSignal = indicators.bollingerSignal(prices);
        int bbScore;
        String bbDetail;
        double[] bands = indicators.bollingerBands(prices);
        String bandsRaw = bands != null
                ? String.format("Upper=%.4f Mid=%.4f Lower=%.4f", bands[0], bands[1], bands[2])
                : "";
        switch (bbSignal) {
            case  1: bbScore =  1; bbDetail = "Sotto lower band ↑ (ipervenduto) " + bandsRaw; break;
            case -1: bbScore = -1; bbDetail = "Sopra upper band ↓ (ipercomprato) " + bandsRaw; break;
            default: bbScore =  0; bbDetail = "Nella banda → " + bandsRaw;                     break;
        }

        // --- Score composito ---
        int totalScore = rsiScore + smaScore + macdScore + bbScore;

        Signal signal = new Signal(
                symbol, quote.getPrice(), quote.getTimestamp(),
                totalScore,
                rsiDetail, macdDetail, smaDetail, bbDetail,
                rsiValue
        );

        logger.info(signal.toString());
        return signal;
    }
}
