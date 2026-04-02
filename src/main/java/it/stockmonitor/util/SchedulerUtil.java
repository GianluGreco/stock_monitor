package it.stockmonitor.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.DayOfWeek;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Calcola il ritardo fino alla prossima esecuzione.
 *
 * Supporta due modalità:
 *
 *   INTERVAL  — esegue ogni N minuti, solo dentro la fascia di mercato
 *               e solo nei giorni lavorativi (lun-ven).
 *               Se fuori fascia, aspetta l'apertura del prossimo giorno lavorativo.
 *
 *   FIXED_TIMES — esegue agli orari fissi configurati (comportamento precedente).
 */
public class SchedulerUtil {

    private final ConfigLoader.ScheduleMode mode;
    private final int             intervalMinutes;
    private final List<LocalTime> scheduledTimes;
    private final LocalTime       marketOpen;
    private final LocalTime       marketClose;

    public SchedulerUtil(ConfigLoader config) {
        this.mode            = config.getScheduleMode();
        this.intervalMinutes = config.getIntervalMinutes();
        this.scheduledTimes  = config.getScheduledTimes();
        this.marketOpen      = config.getMarketOpen();
        this.marketClose     = config.getMarketClose();
    }

    /**
     * Secondi di attesa fino alla prossima esecuzione.
     */
    public long secondsUntilNext() {
        return mode == ConfigLoader.ScheduleMode.INTERVAL
                ? secondsIntervalMode()
                : secondsFixedTimesMode();
    }

    /**
     * Descrizione testuale del prossimo orario (per il log).
     */
    public String nextDescription() {
        LocalDateTime next = LocalDateTime.now().plusSeconds(secondsUntilNext());
        return String.format("%s alle %s",
                next.toLocalDate().equals(LocalDate.now()) ? "oggi" : "il " + next.toLocalDate(),
                next.toLocalTime().truncatedTo(ChronoUnit.MINUTES));
    }

    // =========================================================================
    // Modalità intervallo fisso
    // =========================================================================

    private long secondsIntervalMode() {
        LocalDateTime now      = LocalDateTime.now();
        LocalDateTime nextRun  = now.plusMinutes(intervalMinutes);

        // Se il prossimo run è ancora dentro la fascia di mercato di oggi → OK
        if (isMarketDay(nextRun.toLocalDate())
                && !nextRun.toLocalTime().isBefore(marketOpen)
                && !nextRun.toLocalTime().isAfter(marketClose)) {
            return ChronoUnit.SECONDS.between(now, nextRun);
        }

        // Altrimenti: aspetta l'apertura del prossimo giorno lavorativo
        LocalDate nextDay = nextMarketDay(now.toLocalDate());
        LocalDateTime openTime = nextDay.atTime(marketOpen);
        return ChronoUnit.SECONDS.between(now, openTime);
    }

    // =========================================================================
    // Modalità orari fissi
    // =========================================================================

    private long secondsFixedTimesMode() {
        LocalDateTime now = LocalDateTime.now();
        LocalTime current = now.toLocalTime();

        for (LocalTime t : scheduledTimes) {
            if (current.isBefore(t)) {
                return ChronoUnit.SECONDS.between(now, now.toLocalDate().atTime(t));
            }
        }
        // Tutti passati: vai al primo di domani
        LocalDateTime next = now.toLocalDate().plusDays(1).atTime(scheduledTimes.get(0));
        return ChronoUnit.SECONDS.between(now, next);
    }

    // =========================================================================
    // Utility
    // =========================================================================

    /** Restituisce true se la data è un giorno lavorativo (lun-ven). */
    private boolean isMarketDay(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
    }

    /** Restituisce il prossimo giorno lavorativo dopo la data fornita. */
    private LocalDate nextMarketDay(LocalDate from) {
        LocalDate next = from.plusDays(1);
        while (!isMarketDay(next)) next = next.plusDays(1);
        return next;
    }
}
