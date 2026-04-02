package it.stockmonitor.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import it.stockmonitor.model.PriceRecord;
import it.stockmonitor.model.StockQuote;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Gestisce la persistenza dello storico prezzi in file JSON.
 * Un file per simbolo: history/AVIO_MI.json, history/FCT_MI.json, ...
 *
 * Lo storico cresce indefinitamente; non viene troncato perché
 * più dati = indicatori più affidabili.
 */
public class HistoryService {

    private static final Logger logger = Logger.getLogger(HistoryService.class.getName());

    private final String historyDir;
    private final ObjectMapper mapper;

    public HistoryService(String historyDir) {
        this.historyDir = historyDir;
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        new File(historyDir).mkdirs();
    }

    /**
     * Aggiunge la quotazione corrente allo storico del titolo e salva su disco.
     *
     * @param quote quotazione appena rilevata
     */
    public void append(StockQuote quote) {
        List<PriceRecord> history = load(quote.getSymbol());
        history.add(new PriceRecord(
                quote.getSymbol(),
                quote.getPrice(),
                quote.getChangePercent(),
                quote.getTimestamp()
        ));
        save(quote.getSymbol(), history);
        logger.fine("Storico aggiornato per " + quote.getSymbol()
                + " (" + history.size() + " rilevazioni totali)");
    }

    /**
     * Carica l'intero storico di un simbolo dal file JSON.
     * Restituisce lista vuota se il file non esiste ancora.
     *
     * @param symbol ticker (es. "AVIO.MI")
     * @return lista ordinata di PriceRecord (dal più vecchio al più recente)
     */
    public List<PriceRecord> load(String symbol) {
        File file = historyFile(symbol);
        if (!file.exists()) return new ArrayList<>();

        try {
            return mapper.readValue(file, new TypeReference<List<PriceRecord>>() {});
        } catch (Exception e) {
            logger.warning("Errore lettura storico per " + symbol + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Restituisce solo gli ultimi N prezzi (i più recenti).
     * Utile per gli indicatori che richiedono una finestra temporale fissa.
     */
    public List<Double> loadLastPrices(String symbol, int n) {
        List<PriceRecord> all = load(symbol);
        int from = Math.max(0, all.size() - n);
        List<Double> prices = new ArrayList<>();
        for (int i = from; i < all.size(); i++) {
            prices.add(all.get(i).getPrice());
        }
        return prices;
    }

    /**
     * Numero totale di rilevazioni disponibili per un simbolo.
     */
    public int countRecords(String symbol) {
        return load(symbol).size();
    }

    // -------------------------------------------------------------------------

    private void save(String symbol, List<PriceRecord> history) {
        try {
            mapper.writeValue(historyFile(symbol), history);
        } catch (Exception e) {
            logger.severe("Errore salvataggio storico per " + symbol + ": " + e.getMessage());
        }
    }

    /** Converte il simbolo in nome file sicuro: AVIO.MI → AVIO_MI.json */
    private File historyFile(String symbol) {
        String safeName = symbol.replace(".", "_").replace("/", "_");
        return new File(historyDir, safeName + ".json");
    }
}
