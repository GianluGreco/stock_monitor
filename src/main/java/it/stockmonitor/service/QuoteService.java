package it.stockmonitor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.stockmonitor.model.StockQuote;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Recupera le quotazioni da Yahoo Finance.
 *
 * Yahoo Finance richiede un "crumb token" per autenticare le richieste.
 * Il crumb viene ottenuto automaticamente all'avvio e rinnovato in caso
 * di errore 401.
 */
public class QuoteService {

    private static final Logger logger = Logger.getLogger(QuoteService.class.getName());

    private static final String URL_CONSENT  = "https://fc.yahoo.com";
    private static final String URL_CRUMB    = "https://query2.finance.yahoo.com/v1/test/getcrumb";
    private static final String URL_QUOTE    = "https://query2.finance.yahoo.com/v7/finance/quote?symbols=%s&crumb=%s";

    private static final String USER_AGENT   =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/120.0.0.0 Safari/537.36";

    private final HttpClient   httpClient;
    private final ObjectMapper mapper;
    private String             crumb = null;

    public QuoteService() {
        // CookieManager necessario per mantenere la sessione Yahoo
        CookieManager cookieManager = new CookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);

        this.httpClient = HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .build();
        this.mapper = new ObjectMapper();
    }

    // =========================================================================
    // API pubblica
    // =========================================================================

    public List<StockQuote> fetchQuotes(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) return new ArrayList<>();

        // Ottieni il crumb se non lo abbiamo ancora
        if (crumb == null) {
            crumb = fetchCrumb();
        }

        if (crumb == null) {
            logger.severe("Impossibile ottenere il crumb token da Yahoo Finance.");
            return new ArrayList<>();
        }

        List<StockQuote> quotes = callQuoteApi(symbols);

        // Se riceviamo 401, il crumb è scaduto: rinnova e riprova una volta
        if (quotes == null) {
            logger.warning("Crumb scaduto, rinnovo...");
            crumb = fetchCrumb();
            if (crumb != null) {
                quotes = callQuoteApi(symbols);
            }
        }

        return quotes != null ? quotes : new ArrayList<>();
    }

    // =========================================================================
    // Crumb
    // =========================================================================

    /**
     * Ottiene il crumb token da Yahoo Finance in due passi:
     *   1. Visita fc.yahoo.com per ottenere i cookie di sessione
     *   2. Chiama l'endpoint getcrumb per ottenere il token
     */
    private String fetchCrumb() {
        try {
            // Passo 1: ottieni i cookie di sessione
            HttpRequest consentRequest = HttpRequest.newBuilder()
                    .uri(URI.create(URL_CONSENT))
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();
            httpClient.send(consentRequest, HttpResponse.BodyHandlers.discarding());

            // Passo 2: ottieni il crumb
            HttpRequest crumbRequest = HttpRequest.newBuilder()
                    .uri(URI.create(URL_CRUMB))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "*/*")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(crumbRequest,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 && !response.body().isBlank()) {
                String token = response.body().trim();
                logger.info("Crumb token ottenuto: " + token.substring(0, Math.min(8, token.length())) + "...");
                return token;
            } else {
                logger.warning("Risposta getcrumb inattesa: HTTP " + response.statusCode());
                return null;
            }

        } catch (Exception e) {
            logger.severe("Errore durante il fetch del crumb: " + e.getMessage());
            return null;
        }
    }

    // =========================================================================
    // Chiamata API quotazioni
    // =========================================================================

    /**
     * Chiama l'API quote di Yahoo Finance.
     *
     * @return lista di quotazioni, null se 401 (crumb scaduto), lista vuota se altro errore
     */
    private List<StockQuote> callQuoteApi(List<String> symbols) {
        String symbolsParam = String.join(",", symbols);
        String url = String.format(URL_QUOTE, symbolsParam, crumb);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 401) {
                return null; // segnala al chiamante di rinnovare il crumb
            }

            if (response.statusCode() != 200) {
                logger.warning("Risposta HTTP inattesa: " + response.statusCode());
                return new ArrayList<>();
            }

            return parseResponse(response.body());

        } catch (Exception e) {
            logger.severe("Errore chiamata API: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    // =========================================================================
    // Parsing JSON
    // =========================================================================

    private List<StockQuote> parseResponse(String json) throws Exception {
        List<StockQuote> quotes = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        JsonNode root = mapper.readTree(json);
        JsonNode results = root.path("quoteResponse").path("result");

        if (results.isMissingNode() || !results.isArray()) {
            logger.warning("Nessun risultato nel JSON di risposta.");
            return quotes;
        }

        for (JsonNode node : results) {
            String symbol    = node.path("symbol").asText("N/A");
            String name      = node.path("longName").asText(
                               node.path("shortName").asText(symbol));
            double price     = node.path("regularMarketPrice").asDouble(0.0);
            double change    = node.path("regularMarketChange").asDouble(0.0);
            double changePct = node.path("regularMarketChangePercent").asDouble(0.0);

            quotes.add(new StockQuote(symbol, name, price, change, changePct, now));
            logger.info("Quotazione OK: " + symbol + " = " + price + " EUR");
        }

        return quotes;
    }
}
