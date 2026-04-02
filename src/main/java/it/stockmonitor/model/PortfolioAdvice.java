package it.stockmonitor.model;

/**
 * Raccomandazione operativa contestualizzata al portafoglio.
 * Combina il segnale tecnico con la posizione posseduta (se presente).
 */
public class PortfolioAdvice {

    public enum Action {
        // Titolo in portafoglio
        AUMENTA_POSIZIONE   ("⬆⬆ AUMENTA POSIZIONE",   true),
        MANTIENI_AUMENTA    ("⬆ MANTIENI / VALUTA AUMENTO", true),
        MANTIENI            ("➡ MANTIENI",               true),
        RIDUCI_POSIZIONE    ("⬇ VALUTA DI RIDURRE",     true),
        ESCI_POSIZIONE      ("⬇⬇ ESCI DALLA POSIZIONE", true),

        // Titolo non in portafoglio
        ACQUISTA_FORTE      ("⬆⬆ FORTE SEGNALE ACQUISTO", false),
        VALUTA_ACQUISTO     ("⬆ VALUTA ACQUISTO",          false),
        ASPETTA             ("➡ ASPETTA",                   false),
        EVITA               ("⬇ EVITA",                    false),
        STAI_FUORI          ("⬇⬇ STAI FUORI",              false);

        private final String label;
        private final boolean inPortfolio;

        Action(String label, boolean inPortfolio) {
            this.label       = label;
            this.inPortfolio = inPortfolio;
        }

        public String getLabel()       { return label; }
        public boolean isInPortfolio() { return inPortfolio; }
    }

    private final Signal           signal;
    private final PortfolioPosition position;    // null se non in portafoglio
    private final Action           action;
    private final double           pnl;          // P&L assoluto EUR (0 se non in portafoglio)
    private final double           pnlPercent;   // P&L % (0 se non in portafoglio)
    private final double           currentValue; // Valore corrente posizione

    public PortfolioAdvice(Signal signal, PortfolioPosition position) {
        this.signal   = signal;
        this.position = position;

        if (position != null) {
            this.pnl          = position.pnl(signal.getPrice());
            this.pnlPercent   = position.pnlPercent(signal.getPrice());
            this.currentValue = position.currentValue(signal.getPrice());
            this.action       = resolveActionInPortfolio(signal.getType());
        } else {
            this.pnl          = 0;
            this.pnlPercent   = 0;
            this.currentValue = 0;
            this.action       = resolveActionNotInPortfolio(signal.getType());
        }
    }

    private Action resolveActionInPortfolio(Signal.Type type) {
        switch (type) {
            case STRONG_BUY:  return Action.AUMENTA_POSIZIONE;
            case BUY:         return Action.MANTIENI_AUMENTA;
            case HOLD:        return Action.MANTIENI;
            case SELL:        return Action.RIDUCI_POSIZIONE;
            case STRONG_SELL: return Action.ESCI_POSIZIONE;
            default:          return Action.MANTIENI; // INSUFFICIENT_DATA
        }
    }

    private Action resolveActionNotInPortfolio(Signal.Type type) {
        switch (type) {
            case STRONG_BUY:  return Action.ACQUISTA_FORTE;
            case BUY:         return Action.VALUTA_ACQUISTO;
            case HOLD:        return Action.ASPETTA;
            case SELL:        return Action.EVITA;
            case STRONG_SELL: return Action.STAI_FUORI;
            default:          return Action.ASPETTA;
        }
    }

    public boolean isInPortfolio()      { return position != null; }
    public Signal getSignal()           { return signal; }
    public PortfolioPosition getPosition() { return position; }
    public Action getAction()           { return action; }
    public double getPnl()              { return pnl; }
    public double getPnlPercent()       { return pnlPercent; }
    public double getCurrentValue()     { return currentValue; }

    @Override
    public String toString() {
        String base = String.format("[%s] %s | Score: %+d | Azione: %s",
                signal.getSymbol(), signal.getType().getLabel(),
                signal.getScore(), action.getLabel());

        if (isInPortfolio()) {
            return base + String.format(" | P&L: %+.2f EUR (%+.2f%%) | Valore: %.2f EUR",
                    pnl, pnlPercent, currentValue);
        }
        return base;
    }
}
