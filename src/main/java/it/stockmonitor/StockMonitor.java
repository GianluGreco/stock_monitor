package it.stockmonitor;

import it.stockmonitor.model.PortfolioAdvice;
import it.stockmonitor.model.Signal;
import it.stockmonitor.model.StockQuote;
import it.stockmonitor.service.*;
import it.stockmonitor.util.ConfigLoader;
import it.stockmonitor.util.SchedulerUtil;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Entry point principale.
 *
 * Pipeline ad ogni rilevazione:
 *   1. Recupera quotazioni da Yahoo Finance
 *   2. Salva storico JSON
 *   3. Calcola indicatori tecnici (RSI, MACD, SMA, Bollinger)
 *   4. Genera segnale composito BUY/SELL/HOLD
 *   5. Arricchisce con contesto portafoglio (P&L + azione consigliata)
 *   6. Scrive su Excel (PORTAFOGLIO + SEGNALI + fogli quotazioni)
 */
public class StockMonitor {

    private static final Logger logger = Logger.getLogger(StockMonitor.class.getName());

    private final ConfigLoader             config;
    private final QuoteService             quoteService;
    private final HistoryService           historyService;
    private final SignalService            signalService;
    private final PortfolioService         portfolioService;
    private final ExcelService             excelService;
    private final SchedulerUtil            scheduler;
    private final ScheduledExecutorService executor;

    public StockMonitor() {
        this.config           = new ConfigLoader();
        this.quoteService     = new QuoteService();
        this.historyService   = new HistoryService(config.getHistoryDir());
        this.signalService    = new SignalService(historyService);
        this.portfolioService = new PortfolioService();
        this.excelService     = new ExcelService(config.getExcelOutputPath());
        this.scheduler        = new SchedulerUtil(config);
        this.executor         = Executors.newSingleThreadScheduledExecutor();
    }

    public void start() {
        logger.info("=== Stock Monitor avviato ===");
        if (config.getScheduleMode() == ConfigLoader.ScheduleMode.INTERVAL) {
            logger.info(String.format("Modalità: ogni %d minuti | Mercato: %s - %s",
                    config.getIntervalMinutes(), config.getMarketOpen(), config.getMarketClose()));
        } else {
            logger.info("Modalità: orari fissi → " + config.getScheduledTimes());
        }
        scheduleNextRun();
    }

    private void scheduleNextRun() {
        long delay = scheduler.secondsUntilNext();
        logger.info(String.format("Prossima rilevazione %s (tra %d minuti)",
                scheduler.nextDescription(), delay / 60));
        executor.schedule(this::runAndReschedule, delay, TimeUnit.SECONDS);
    }

    private void runAndReschedule() {
        run();
        scheduleNextRun();
    }

    private void run() {
        logger.info("--- Avvio rilevazione ---");

        // 1. Quotazioni
        List<StockQuote> quotes = quoteService.fetchQuotes(config.getSymbols());
        if (quotes.isEmpty()) {
            logger.warning("Nessuna quotazione ricevuta.");
            return;
        }

        // 2. Storico
        quotes.forEach(historyService::append);

        // 3. Segnali tecnici
        List<Signal> signals = quotes.stream()
                .map(signalService::generateSignal)
                .collect(Collectors.toList());

        // 4. Consigli portafoglio
        List<PortfolioAdvice> advices = signals.stream()
                .map(portfolioService::getAdvice)
                .collect(Collectors.toList());

        // 5. Log
        advices.forEach(a -> logger.info(a.toString()));

        // 6. Excel
        excelService.appendAll(quotes, signals, advices);

        logger.info("--- Rilevazione completata ---");
    }

    public static void main(String[] args) {
        StockMonitor monitor = new StockMonitor();
        monitor.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() ->
            logger.info("Stock Monitor arrestato.")
        ));
    }
}
