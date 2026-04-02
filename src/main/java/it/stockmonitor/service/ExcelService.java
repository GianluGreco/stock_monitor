package it.stockmonitor.service;

import it.stockmonitor.model.PortfolioAdvice;
import it.stockmonitor.model.Signal;
import it.stockmonitor.model.StockQuote;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.logging.Logger;

/**
 * Scrive su Excel tre tipologie di fogli:
 *
 *   1. "PORTAFOGLIO" — azioni contestuali con P&L per i titoli posseduti
 *   2. "SEGNALI"     — tutti i segnali tecnici (buy/sell/hold)
 *   3. Un foglio per ogni simbolo — storico delle quotazioni
 */
public class ExcelService {

    private static final Logger logger = Logger.getLogger(ExcelService.class.getName());
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final String SHEET_PORTFOLIO = "PORTAFOGLIO";
    private static final String SHEET_SIGNALS   = "SEGNALI";

    private final String filePath;

    public ExcelService(String filePath) {
        this.filePath = filePath;
    }

    // =========================================================================
    // API pubblica
    // =========================================================================

    public void appendAll(List<StockQuote> quotes, List<Signal> signals, List<PortfolioAdvice> advices) {
        if (quotes == null || quotes.isEmpty()) return;

        Workbook workbook = loadOrCreateWorkbook();

        // 1. Foglio PORTAFOGLIO (solo se ci sono titoli in portafoglio)
        boolean hasPortfolio = advices.stream().anyMatch(PortfolioAdvice::isInPortfolio);
        if (hasPortfolio) {
            Sheet portfolioSheet = getOrCreateSheet(workbook, SHEET_PORTFOLIO, 0,
                    this::createPortfolioHeader);
            advices.stream()
                   .filter(PortfolioAdvice::isInPortfolio)
                   .forEach(a -> appendPortfolioRow(workbook, portfolioSheet, a));
        }

        // 2. Foglio SEGNALI
        Sheet signalsSheet = getOrCreateSheet(workbook, SHEET_SIGNALS, hasPortfolio ? 1 : 0,
                this::createSignalsHeader);
        signals.stream()
               .filter(s -> s.getType() != Signal.Type.INSUFFICIENT_DATA)
               .forEach(s -> appendSignalRow(workbook, signalsSheet, s));

        // 3. Fogli quotazioni (uno per simbolo)
        for (StockQuote quote : quotes) {
            Sheet quoteSheet = getOrCreateQuoteSheet(workbook, quote.getSymbol());
            appendQuoteRow(workbook, quoteSheet, quote);
        }

        saveWorkbook(workbook);
        logger.info("Excel aggiornato: " + filePath);
    }

    // =========================================================================
    // Foglio PORTAFOGLIO
    // =========================================================================

    private void createPortfolioHeader(Workbook wb, Sheet sheet) {
        Row titleRow = sheet.createRow(0);
        Cell title = titleRow.createCell(0);
        title.setCellValue("📊 Portafoglio Personale — Azioni Consigliate");
        title.setCellStyle(titleStyle(wb, IndexedColors.DARK_GREEN));
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 10));

        String[] cols = {
            "Data", "Ora", "Simbolo", "Prezzo", "Azione Consigliata",
            "Quantità", "Prezzo Carico", "Valore Carico (€)",
            "Valore Attuale (€)", "P&L (€)", "P&L %"
        };
        int[] widths = {4500, 3500, 4000, 4500, 8000, 4000, 5000, 6000, 6500, 5500, 4500};
        Row header = sheet.createRow(1);
        CellStyle hs = headerStyle(wb, IndexedColors.DARK_GREEN);
        for (int i = 0; i < cols.length; i++) {
            Cell c = header.createCell(i);
            c.setCellValue(cols[i]);
            c.setCellStyle(hs);
            sheet.setColumnWidth(i, widths[i]);
        }
    }

    private void appendPortfolioRow(Workbook wb, Sheet sheet, PortfolioAdvice advice) {
        int idx = Math.max(sheet.getLastRowNum() + 1, 2);
        Row row = sheet.createRow(idx);
        CellStyle base = dataStyle(wb);

        Signal s = advice.getSignal();
        setStr(row, 0, s.getTimestamp().format(DATE_FMT), base);
        setStr(row, 1, s.getTimestamp().format(TIME_FMT), base);
        setStr(row, 2, s.getSymbol(), base);
        setNum(row, 3, s.getPrice(), base);
        setStr(row, 4, advice.getAction().getLabel(), actionStyle(wb, advice.getAction()));

        if (advice.getPosition() != null) {
            setNum(row, 5, advice.getPosition().getQuantity(), base);
            setNum(row, 6, advice.getPosition().getLoadPrice(), base);
            setNum(row, 7, advice.getPosition().totalCost(), base);
            setNum(row, 8, advice.getCurrentValue(), base);
            setNum(row, 9, advice.getPnl(), pnlStyle(wb, advice.getPnl() >= 0));
            setNum(row, 10, advice.getPnlPercent(), pnlStyle(wb, advice.getPnl() >= 0));
        }
    }

    // =========================================================================
    // Foglio SEGNALI
    // =========================================================================

    private void createSignalsHeader(Workbook wb, Sheet sheet) {
        Row titleRow = sheet.createRow(0);
        Cell title = titleRow.createCell(0);
        title.setCellValue("📈 Segnali di Trading — Analisi Tecnica Automatica");
        title.setCellStyle(titleStyle(wb, IndexedColors.DARK_TEAL));
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 9));

        String[] cols = {
            "Data", "Ora", "Simbolo", "Prezzo (EUR)",
            "Segnale", "Score", "RSI", "MACD", "SMA 9/21", "Bollinger Bands"
        };
        int[] widths = {4500, 3500, 4000, 4500, 6000, 3500, 8000, 12000, 8000, 14000};
        Row header = sheet.createRow(1);
        CellStyle hs = headerStyle(wb, IndexedColors.DARK_TEAL);
        for (int i = 0; i < cols.length; i++) {
            Cell c = header.createCell(i);
            c.setCellValue(cols[i]);
            c.setCellStyle(hs);
            sheet.setColumnWidth(i, widths[i]);
        }
    }

    private void appendSignalRow(Workbook wb, Sheet sheet, Signal signal) {
        int idx = Math.max(sheet.getLastRowNum() + 1, 2);
        Row row = sheet.createRow(idx);
        CellStyle base = dataStyle(wb);

        setStr(row, 0, signal.getTimestamp().format(DATE_FMT), base);
        setStr(row, 1, signal.getTimestamp().format(TIME_FMT), base);
        setStr(row, 2, signal.getSymbol(), base);
        setNum(row, 3, signal.getPrice(), base);
        setStr(row, 4, signal.getType().getLabel(), signalStyle(wb, signal.getType()));
        setNum(row, 5, signal.getScore(), signalStyle(wb, signal.getType()));
        setStr(row, 6, signal.getRsiDetail(), base);
        setStr(row, 7, signal.getMacdDetail(), base);
        setStr(row, 8, signal.getSmaDetail(), base);
        setStr(row, 9, signal.getBollingerDetail(), base);
    }

    // =========================================================================
    // Fogli quotazioni
    // =========================================================================

    private Sheet getOrCreateQuoteSheet(Workbook wb, String symbol) {
        Sheet sheet = wb.getSheet(symbol);
        if (sheet == null) {
            sheet = wb.createSheet(symbol);
            createQuoteHeader(wb, sheet, symbol);
        }
        return sheet;
    }

    private void createQuoteHeader(Workbook wb, Sheet sheet, String symbol) {
        Row titleRow = sheet.createRow(0);
        Cell title = titleRow.createCell(0);
        title.setCellValue("Quotazioni: " + symbol);
        title.setCellStyle(titleStyle(wb, IndexedColors.DARK_BLUE));
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 5));

        String[] cols = {"Data", "Ora", "Simbolo", "Nome", "Prezzo (EUR)", "Variazione %"};
        int[] widths = {4500, 3500, 4000, 9000, 5000, 5000};
        Row header = sheet.createRow(1);
        CellStyle hs = headerStyle(wb, IndexedColors.GREY_50_PERCENT);
        for (int i = 0; i < cols.length; i++) {
            Cell c = header.createCell(i);
            c.setCellValue(cols[i]);
            c.setCellStyle(hs);
            sheet.setColumnWidth(i, widths[i]);
        }
    }

    private void appendQuoteRow(Workbook wb, Sheet sheet, StockQuote quote) {
        int idx = Math.max(sheet.getLastRowNum() + 1, 2);
        Row row = sheet.createRow(idx);
        CellStyle base = dataStyle(wb);

        setStr(row, 0, quote.getTimestamp().format(DATE_FMT), base);
        setStr(row, 1, quote.getTimestamp().format(TIME_FMT), base);
        setStr(row, 2, quote.getSymbol(), base);
        setStr(row, 3, quote.getName(), base);
        setNum(row, 4, quote.getPrice(), base);
        setNum(row, 5, quote.getChangePercent(), pnlStyle(wb, quote.getChangePercent() >= 0));
    }

    // =========================================================================
    // Utility
    // =========================================================================

    @FunctionalInterface
    private interface SheetInitializer {
        void init(Workbook wb, Sheet sheet);
    }

    private Sheet getOrCreateSheet(Workbook wb, String name, int position, SheetInitializer initializer) {
        Sheet sheet = wb.getSheet(name);
        if (sheet == null) {
            sheet = wb.createSheet(name);
            wb.setSheetOrder(name, position);
            initializer.init(wb, sheet);
        }
        return sheet;
    }

    private Workbook loadOrCreateWorkbook() {
        File file = new File(filePath);
        if (file.exists()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                return new XSSFWorkbook(fis);
            } catch (Exception e) {
                logger.warning("File Excel non leggibile, ne creo uno nuovo: " + e.getMessage());
            }
        }
        return new XSSFWorkbook();
    }

    private void saveWorkbook(Workbook wb) {
        new File(filePath).getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            wb.write(fos);
            wb.close();
        } catch (Exception e) {
            logger.severe("Errore salvataggio Excel: " + e.getMessage());
        }
    }

    private void setStr(Row row, int col, String value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value != null ? value : "");
        c.setCellStyle(style);
    }

    private void setNum(Row row, int col, double value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value);
        c.setCellStyle(style);
    }

    // =========================================================================
    // Stili
    // =========================================================================

    private CellStyle titleStyle(Workbook wb, IndexedColors bg) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 13);
        f.setColor(IndexedColors.WHITE.getIndex());
        s.setFont(f);
        s.setFillForegroundColor(bg.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        return s;
    }

    private CellStyle headerStyle(Workbook wb, IndexedColors bg) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setColor(IndexedColors.WHITE.getIndex());
        s.setFont(f);
        s.setFillForegroundColor(bg.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        border(s);
        return s;
    }

    private CellStyle dataStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        border(s);
        return s;
    }

    private CellStyle pnlStyle(Workbook wb, boolean positive) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setColor(positive ? IndexedColors.GREEN.getIndex() : IndexedColors.RED.getIndex());
        s.setFont(f);
        border(s);
        return s;
    }

    private CellStyle signalStyle(Workbook wb, Signal.Type type) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        short fontColor = IndexedColors.WHITE.getIndex();
        short bgColor;
        switch (type) {
            case STRONG_BUY:  bgColor = IndexedColors.GREEN.getIndex(); break;
            case BUY:         bgColor = IndexedColors.LIGHT_GREEN.getIndex(); fontColor = IndexedColors.BLACK.getIndex(); break;
            case SELL:        bgColor = IndexedColors.CORAL.getIndex(); fontColor = IndexedColors.BLACK.getIndex(); break;
            case STRONG_SELL: bgColor = IndexedColors.RED.getIndex(); break;
            default:          bgColor = IndexedColors.GREY_25_PERCENT.getIndex(); fontColor = IndexedColors.BLACK.getIndex(); break;
        }
        f.setColor(fontColor);
        s.setFont(f);
        s.setFillForegroundColor(bgColor);
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        border(s);
        return s;
    }

    private CellStyle actionStyle(Workbook wb, PortfolioAdvice.Action action) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        short fontColor = IndexedColors.WHITE.getIndex();
        short bgColor;
        switch (action) {
            case AUMENTA_POSIZIONE:
            case ACQUISTA_FORTE:    bgColor = IndexedColors.GREEN.getIndex(); break;
            case MANTIENI_AUMENTA:
            case VALUTA_ACQUISTO:   bgColor = IndexedColors.LIGHT_GREEN.getIndex(); fontColor = IndexedColors.BLACK.getIndex(); break;
            case MANTIENI:
            case ASPETTA:           bgColor = IndexedColors.GREY_25_PERCENT.getIndex(); fontColor = IndexedColors.BLACK.getIndex(); break;
            case RIDUCI_POSIZIONE:
            case EVITA:             bgColor = IndexedColors.CORAL.getIndex(); fontColor = IndexedColors.BLACK.getIndex(); break;
            case ESCI_POSIZIONE:
            case STAI_FUORI:        bgColor = IndexedColors.RED.getIndex(); break;
            default:                bgColor = IndexedColors.GREY_25_PERCENT.getIndex(); fontColor = IndexedColors.BLACK.getIndex(); break;
        }
        f.setColor(fontColor);
        s.setFont(f);
        s.setFillForegroundColor(bgColor);
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        border(s);
        return s;
    }

    private void border(CellStyle s) {
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
    }
}
