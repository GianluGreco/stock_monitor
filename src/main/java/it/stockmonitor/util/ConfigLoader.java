package it.stockmonitor.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Carica e valida la configurazione da config.properties.
 *
 * Supporta due modalità di schedulazione:
 *   - Intervallo fisso: schedule.interval.minutes=30
 *   - Orari fissi:      schedule.times=09:30,12:30,17:30
 *
 * Se entrambi sono presenti, ha precedenza l'intervallo fisso.
 */
public class ConfigLoader {

    private static final Logger logger = Logger.getLogger(ConfigLoader.class.getName());
    private static final String CONFIG_FILE = "config.properties";

    public enum ScheduleMode { INTERVAL, FIXED_TIMES }

    private final List<String>    symbols;
    private final ScheduleMode    scheduleMode;
    private final int             intervalMinutes;   // valido solo se INTERVAL
    private final List<LocalTime> scheduledTimes;    // valido solo se FIXED_TIMES
    private final LocalTime       marketOpen;
    private final LocalTime       marketClose;
    private final String          excelOutputPath;
    private final String          historyDir;

    public ConfigLoader() {
        Properties props  = loadProperties();
        this.symbols      = parseSymbols(props);
        this.excelOutputPath = props.getProperty("excel.output.path", "output/quotazioni.xlsx").trim();
        this.historyDir   = props.getProperty("history.dir", "history").trim();
        this.marketOpen   = parseTime(props, "market.open",  "09:00");
        this.marketClose  = parseTime(props, "market.close", "17:30");

        // Determina modalità: intervallo fisso ha precedenza
        String intervalProp = props.getProperty("schedule.interval.minutes", "").trim();
        if (!intervalProp.isEmpty()) {
            this.scheduleMode    = ScheduleMode.INTERVAL;
            this.intervalMinutes = parseInterval(intervalProp);
            this.scheduledTimes  = null;
        } else {
            this.scheduleMode    = ScheduleMode.FIXED_TIMES;
            this.intervalMinutes = 0;
            this.scheduledTimes  = parseTimes(props);
        }

        logConfiguration();
    }

    // -------------------------------------------------------------------------
    // Getter
    // -------------------------------------------------------------------------

    public List<String>    getSymbols()         { return symbols; }
    public ScheduleMode    getScheduleMode()     { return scheduleMode; }
    public int             getIntervalMinutes()  { return intervalMinutes; }
    public List<LocalTime> getScheduledTimes()   { return scheduledTimes; }
    public LocalTime       getMarketOpen()       { return marketOpen; }
    public LocalTime       getMarketClose()      { return marketClose; }
    public String          getExcelOutputPath()  { return excelOutputPath; }
    public String          getHistoryDir()       { return historyDir; }

    // -------------------------------------------------------------------------
    // Parsing
    // -------------------------------------------------------------------------

    private Properties loadProperties() {
        Properties props = new Properties();
        File externalFile = new File(CONFIG_FILE);
        if (externalFile.exists()) {
            try (InputStream is = new FileInputStream(externalFile)) {
                props.load(is);
                logger.info("Config caricata da filesystem: " + externalFile.getAbsolutePath());
                return props;
            } catch (IOException e) {
                logger.warning("Errore lettura config esterna: " + e.getMessage());
            }
        }
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (is == null) throw new RuntimeException(CONFIG_FILE + " non trovato.");
            props.load(is);
            logger.info("Config caricata dal classpath (JAR).");
        } catch (IOException e) {
            throw new RuntimeException("Impossibile caricare la configurazione: " + e.getMessage(), e);
        }
        return props;
    }

    private List<String> parseSymbols(Properties props) {
        String raw = props.getProperty("symbols", "").trim();
        if (raw.isEmpty()) throw new RuntimeException("'symbols' non configurato in config.properties.");
        return Arrays.stream(raw.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private int parseInterval(String value) {
        try {
            int minutes = Integer.parseInt(value);
            if (minutes < 1) throw new RuntimeException("'schedule.interval.minutes' deve essere >= 1.");
            return minutes;
        } catch (NumberFormatException e) {
            throw new RuntimeException("Valore non valido per 'schedule.interval.minutes': " + value);
        }
    }

    private List<LocalTime> parseTimes(Properties props) {
        String raw = props.getProperty("schedule.times", "").trim();
        if (raw.isEmpty()) throw new RuntimeException(
                "Nessuna modalità di schedulazione configurata. " +
                "Imposta 'schedule.interval.minutes' oppure 'schedule.times'.");
        return Arrays.stream(raw.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .map(t -> {
                    try { return LocalTime.parse(t); }
                    catch (DateTimeParseException e) {
                        throw new RuntimeException("Orario non valido: '" + t + "'. Usa HH:mm.");
                    }
                })
                .sorted().collect(Collectors.toList());
    }

    private LocalTime parseTime(Properties props, String key, String defaultValue) {
        try { return LocalTime.parse(props.getProperty(key, defaultValue).trim()); }
        catch (DateTimeParseException e) {
            logger.warning("Valore non valido per '" + key + "', uso default " + defaultValue);
            return LocalTime.parse(defaultValue);
        }
    }

    private void logConfiguration() {
        logger.info("=== Configurazione caricata ===");
        logger.info("Titoli:   " + symbols);
        logger.info("Modalità: " + scheduleMode);
        if (scheduleMode == ScheduleMode.INTERVAL) {
            logger.info("Intervallo: ogni " + intervalMinutes + " minuti");
            logger.info("Mercato:   " + marketOpen + " - " + marketClose);
        } else {
            logger.info("Orari:    " + scheduledTimes);
        }
        logger.info("Excel:    " + excelOutputPath);
        logger.info("History:  " + historyDir);
    }
}
