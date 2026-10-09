package com.examly.springapp.config;

import com.examly.springapp.model.Investment;
import com.examly.springapp.repository.InvestmentRepo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Loads the generated demonstration dataset (data/investments.json) into MySQL on
 * startup so no manual upload, manual entry or SQL script is ever required.
 *
 * Guarantees:
 *  - Idempotent: records are matched by the stable {@code seedKey} column, which is
 *    unique in the schema, so restarting the application never creates duplicates.
 *  - Non-destructive: nothing is ever deleted and no existing row is ever updated.
 *    Investments created or edited by Admins / Super Admins (rows without a seedKey,
 *    or seeded rows that were edited) are left exactly as they are.
 *  - Scoped: only the investments table is touched - users, inquiries and feedback
 *    are never read or written by this initializer.
 */
@Component
@Order(3)
public class InvestmentSeedInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(InvestmentSeedInitializer.class);

    static final String SEED_RESOURCE = "classpath:data/investments.json";
    static final int MIN_RECORDS = 300;

    private static final Pattern DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    private final InvestmentRepo investmentRepo;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    public InvestmentSeedInitializer(InvestmentRepo investmentRepo,
                                     ResourceLoader resourceLoader,
                                     ObjectMapper objectMapper) {
        this.investmentRepo = investmentRepo;
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void run(String... args) {
        try {
            seed();
        } catch (Exception exception) {
            // Never block application startup because of seed data problems.
            logger.error("Investment seed initialization failed; existing data was left untouched", exception);
        }
    }

    private void seed() throws Exception {
        Resource resource = resourceLoader.getResource(SEED_RESOURCE);
        if (!resource.exists()) {
            logger.error("Seed resource {} not found - no seed investments were loaded", SEED_RESOURCE);
            return;
        }

        JsonNode root = objectMapper.readTree(resource.getInputStream());
        JsonNode arrayNode = root.isArray() ? root : root.get("investments");
        if (arrayNode == null || !arrayNode.isArray()) {
            logger.error("Seed dataset is invalid: expected a JSON array or an \"investments\" array. Seeding aborted.");
            return;
        }

        List<String> validationErrors = new ArrayList<>();
        List<Investment> seedRecords = new ArrayList<>();
        Set<String> fileSeedKeys = new HashSet<>();
        Set<String> fileSymbols = new HashSet<>();

        int index = 0;
        for (JsonNode node : arrayNode) {
            index++;
            Investment investment = parseAndValidate(node, "investments[" + index + "]", validationErrors);
            if (investment == null) {
                continue;
            }
            if (!fileSeedKeys.add(investment.getSeedKey())) {
                validationErrors.add("investments[" + index + "]: duplicate seedKey " + investment.getSeedKey());
            }
            if (investment.getSymbol() != null && !fileSymbols.add(investment.getSymbol())) {
                validationErrors.add("investments[" + index + "]: duplicate symbol " + investment.getSymbol());
            }
            seedRecords.add(investment);
        }

        if (!validationErrors.isEmpty()) {
            logger.error("Seed dataset validation failed with {} problem(s); no records were inserted. First problems: {}",
                    validationErrors.size(), validationErrors.subList(0, Math.min(10, validationErrors.size())));
            return;
        }

        if (seedRecords.size() < MIN_RECORDS) {
            logger.error("Seed dataset contains only {} valid records but {} are required; no records were inserted.",
                    seedRecords.size(), MIN_RECORDS);
            return;
        }

        // Which seed records already exist in MySQL?
        Set<String> existingSeedKeys = new HashSet<>();
        for (Investment existing : investmentRepo.findBySeedKeyIn(fileSeedKeys)) {
            existingSeedKeys.add(existing.getSeedKey());
        }

        // Never fight the unique symbol constraint (a user-created row may own a symbol).
        Set<String> existingSymbols = new HashSet<>();
        List<String> symbolsToCheck = new ArrayList<>(fileSymbols);
        if (!symbolsToCheck.isEmpty()) {
            for (Investment existing : investmentRepo.findBySymbolIn(symbolsToCheck)) {
                if (existing.getSymbol() != null) {
                    existingSymbols.add(existing.getSymbol().toUpperCase(Locale.ROOT));
                }
            }
        }

        List<Investment> toInsert = new ArrayList<>();
        int alreadyPresent = 0;
        int symbolConflicts = 0;

        for (Investment candidate : seedRecords) {
            if (existingSeedKeys.contains(candidate.getSeedKey())) {
                alreadyPresent++;
                continue;
            }
            if (candidate.getSymbol() != null
                    && existingSymbols.contains(candidate.getSymbol().toUpperCase(Locale.ROOT))) {
                symbolConflicts++;
                logger.info("Seed record {} ({}) skipped: symbol already used by an existing investment",
                        candidate.getSeedKey(), candidate.getSymbol());
                continue;
            }
            toInsert.add(candidate);
        }

        if (!toInsert.isEmpty()) {
            List<Investment> batch = new ArrayList<>();
            for (Investment investment : toInsert) {
                batch.add(investment);
                if (batch.size() >= 100) {
                    investmentRepo.saveAll(batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                investmentRepo.saveAll(batch);
            }
        }

        logger.info("Investment seed initialization complete: dataset={}, already present={}, inserted={}, " +
                        "skipped (symbol conflict)={}, total investments in database={}",
                SEED_RESOURCE, alreadyPresent, toInsert.size(), symbolConflicts, investmentRepo.count());
    }

    private Investment parseAndValidate(JsonNode node, String where, List<String> errors) {
        if (node == null || !node.isObject()) {
            errors.add(where + ": not a JSON object");
            return null;
        }

        int errorsBefore = errors.size();

        String seedKey = text(node, "seedKey");
        String name = text(node, "name");
        String description = text(node, "description");
        String type = text(node, "type");
        String status = text(node, "status");
        String purchaseDate = text(node, "purchaseDate");
        String symbol = text(node, "symbol");

        if (isBlank(seedKey) || seedKey.length() > 80) errors.add(where + ": seedKey is required (max 80 chars)");
        if (isBlank(name) || name.length() > 120) errors.add(where + ": name is required (max 120 chars)");
        if (isBlank(description) || description.length() > 4000) errors.add(where + ": description is required (max 4000 chars)");
        if (isBlank(type)) errors.add(where + ": type is required");
        if (isBlank(status)) errors.add(where + ": status is required");
        if (isBlank(purchaseDate) || !DATE_PATTERN.matcher(purchaseDate).matches()) {
            errors.add(where + ": purchaseDate must match yyyy-MM-dd");
        }
        if (symbol != null && symbol.length() > 30) errors.add(where + ": symbol max 30 chars");

        Double purchasePrice = positiveNumber(node, "purchasePrice");
        Double currentPrice = positiveNumber(node, "currentPrice");
        Integer quantity = positiveInteger(node, "quantity");

        if (purchasePrice == null) errors.add(where + ": purchasePrice must be a number > 0");
        if (currentPrice == null) errors.add(where + ": currentPrice must be a number > 0");
        if (quantity == null) errors.add(where + ": quantity must be an integer >= 1");

        if (lengthExceeds(node, "exchange", 60)) errors.add(where + ": exchange max 60 chars");
        if (lengthExceeds(node, "market", 60)) errors.add(where + ": market max 60 chars");
        if (lengthExceeds(node, "assetClass", 40)) errors.add(where + ": assetClass max 40 chars");
        if (lengthExceeds(node, "currency", 10)) errors.add(where + ": currency max 10 chars");

        if (errors.size() > errorsBefore) {
            return null;
        }

        Investment investment = new Investment();
        investment.setSeedKey(seedKey);
        investment.setName(name);
        investment.setSymbol(symbol);
        investment.setExchange(text(node, "exchange"));
        investment.setMarket(text(node, "market"));
        investment.setAssetClass(text(node, "assetClass"));
        investment.setCurrency(text(node, "currency"));
        investment.setDescription(description);
        investment.setType(type);
        investment.setPurchasePrice(purchasePrice);
        investment.setCurrentPrice(currentPrice);
        investment.setQuantity(quantity);
        investment.setPurchaseDate(purchaseDate);
        investment.setStatus(status);
        return investment;
    }

    private boolean lengthExceeds(JsonNode node, String field, int max) {
        JsonNode value = node.get(field);
        return value != null && !value.isNull() && value.isTextual() && value.asText().length() > max;
    }

    private Double positiveNumber(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isNumber()) return null;
        double raw = value.asDouble();
        return raw > 0 && Double.isFinite(raw) ? raw : null;
    }

    private Integer positiveInteger(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.canConvertToInt()) return null;
        int raw = value.asInt();
        return raw >= 1 ? raw : null;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        String raw = value.asText(null);
        if (raw == null) return null;
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Exposed for verification tooling / tests. */
    static Set<String> keysOf(List<Investment> investments) {
        Set<String> keys = new LinkedHashSet<>();
        for (Investment investment : investments) {
            keys.add(investment.getSeedKey());
        }
        return keys;
    }
}
