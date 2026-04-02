package it.stockmonitor.service;

import it.stockmonitor.model.PortfolioAdvice;
import it.stockmonitor.model.PortfolioPosition;
import it.stockmonitor.model.Signal;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Carica il portafoglio da portfolio.properties e genera
 * raccomandazioni operative contestualizzate.
 *
 * Il file viene riletto ad ogni chiamata a getAdvice(), così
 * puoi aggiornare il portafoglio senza riavviare il programma.
 */
public class PortfolioService {

    private static final Logger logger = Logger.getLogger(PortfolioService.class.getName());
    private static final String PORTFOLIO_FILE = "portfolio.properties";

    /**
     * Genera la raccomandazione operativa per un segnale tecnico,
     * tenendo conto della posizione in portafoglio (se presente).
     *
     * @param signal segnale tecnico calcolato da SignalService
     * @return PortfolioAdvice con azione contestualizzata e P&L
     */
    public PortfolioAdvice getAdvice(Signal signal) {
        Map<String, PortfolioPosition> portfolio = loadPortfolio();
        PortfolioPosition position = portfolio.get(signal.getSymbol());

        // position è null se il titolo non è in portafoglio
        PortfolioAdvice advice = new PortfolioAdvice(signal, position);
        logger.info(advice.toString());
        return advice;
    }

    // =========================================================================
    // Caricamento portafoglio
    // =========================================================================

    /**
     * Legge portfolio.properties e costruisce la mappa simbolo → posizione.
     * Cerca prima nel filesystem (directory corrente), poi nel classpath.
     */
    private Map<String, PortfolioPosition> loadPortfolio() {
        Properties props = new Properties();

        File externalFile = new File(PORTFOLIO_FILE);
        if (externalFile.exists()) {
            try (InputStream is = new FileInputStream(externalFile)) {
                props.load(is);
                logger.fine("Portafoglio caricato da filesystem.");
            } catch (Exception e) {
                logger.warning("Errore lettura portfolio esterno: " + e.getMessage());
            }
        } else {
            try (InputStream is = getClass().getClassLoader().getResourceAsStream(PORTFOLIO_FILE)) {
                if (is != null) {
                    props.load(is);
                    logger.fine("Portafoglio caricato dal classpath.");
                } else {
                    logger.info("portfolio.properties non trovato: nessun titolo in portafoglio.");
                    return new HashMap<>();
                }
            } catch (Exception e) {
                logger.warning("Errore lettura portfolio dal classpath: " + e.getMessage());
                return new HashMap<>();
            }
        }

        return parsePositions(props);
    }

    /**
     * Parsa le properties e costruisce le posizioni.
     * Formato atteso:
     *   AVIO.MI.quantita    = 500
     *   AVIO.MI.prezzoCarico = 4.25
     */
    private Map<String, PortfolioPosition> parsePositions(Properties props) {
        Map<String, PortfolioPosition> portfolio = new HashMap<>();

        // Raccoglie i simboli unici dalle chiavi
        for (String key : props.stringPropertyNames()) {
            if (!key.endsWith(".quantita")) continue;

            // Estrae il simbolo: "AVIO.MI.quantita" → "AVIO.MI"
            String symbol = key.substring(0, key.lastIndexOf(".quantita"));

            String qtyStr   = props.getProperty(symbol + ".quantita", "").trim();
            String priceStr = props.getProperty(symbol + ".prezzoCarico", "").trim();

            if (qtyStr.isEmpty() || priceStr.isEmpty()) {
                logger.warning("Dati incompleti per " + symbol + " in portfolio.properties, ignorato.");
                continue;
            }

            try {
                int    qty   = Integer.parseInt(qtyStr);
                double price = Double.parseDouble(priceStr.replace(",", "."));

                if (qty <= 0 || price <= 0) {
                    logger.warning("Valori non validi per " + symbol + " (qty=" + qty
                            + " prezzo=" + price + "), ignorato.");
                    continue;
                }

                portfolio.put(symbol, new PortfolioPosition(symbol, qty, price));
                logger.fine("Posizione caricata: " + symbol + " qty=" + qty + " carico=" + price);

            } catch (NumberFormatException e) {
                logger.warning("Formato non valido per " + symbol + ": " + e.getMessage());
            }
        }

        logger.info("Portafoglio: " + portfolio.size() + " posizioni caricate.");
        return portfolio;
    }
}
