package com.examly.springapp.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "investments")
public class Investment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long investmentId;

    @Column(nullable = false)
    @NotBlank
    @Size(max = 120)
    private String name;

    @Column(unique = true)
    @Size(max = 30)
    private String symbol;

    @Size(max = 60)
    private String exchange;

    @Size(max = 60)
    private String market;

    @Size(max = 40)
    private String assetClass;

    @Size(max = 10)
    private String currency;

    @Column(nullable = false, columnDefinition = "TEXT")
    @NotBlank
    @Size(max = 4000)
    private String description;

    @Column(nullable = false)
    @NotBlank
    private String type;

    @Column(nullable = false)
    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    private Double purchasePrice;

    @Column(nullable = false)
    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    private Double currentPrice;

    @Column(nullable = false)
    @NotNull
    @Min(1)
    private Integer quantity;

    @Column(nullable = false)
    @NotBlank
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$")
    private String purchaseDate;

    @Column(nullable = false)
    @NotBlank
    private String status;

    /**
     * Stable identifier of the generated seed record this row was created from.
     * NULL for investments created through the API. The unique constraint makes
     * seed initialization idempotent: a restart can only insert rows whose
     * seedKey is not present yet, and it never updates or deletes existing rows.
     */
    @Column(unique = true, length = 80)
    private String seedKey;

    public Investment() {}

    public Investment(Long investmentId, String name, String description, String type,
                      Double purchasePrice, Double currentPrice, Integer quantity,
                      String purchaseDate, String status) {
        this.investmentId = investmentId;
        this.name = name;
        this.description = description;
        this.type = type;
        this.purchasePrice = purchasePrice;
        this.currentPrice = currentPrice;
        this.quantity = quantity;
        this.purchaseDate = purchaseDate;
        this.status = status;
    }

    public Long getInvestmentId() { return investmentId; }
    public void setInvestmentId(Long investmentId) { this.investmentId = investmentId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getExchange() { return exchange; }
    public void setExchange(String exchange) { this.exchange = exchange; }

    public String getMarket() { return market; }
    public void setMarket(String market) { this.market = market; }

    public String getAssetClass() { return assetClass; }
    public void setAssetClass(String assetClass) { this.assetClass = assetClass; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Double getPurchasePrice() { return purchasePrice; }
    public void setPurchasePrice(Double purchasePrice) { this.purchasePrice = purchasePrice; }

    public Double getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(Double currentPrice) { this.currentPrice = currentPrice; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public String getPurchaseDate() { return purchaseDate; }
    public void setPurchaseDate(String purchaseDate) { this.purchaseDate = purchaseDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSeedKey() { return seedKey; }
    public void setSeedKey(String seedKey) { this.seedKey = seedKey; }
}