package com.examly.springapp.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record InvestmentDTO(
        Long investmentId,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 30) String symbol,
        @Size(max = 60) String exchange,
        @Size(max = 60) String market,
        @Size(max = 40) String assetClass,
        @Size(max = 10) String currency,
        @NotBlank @Size(max = 4000) String description,
        @NotBlank String type,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) Double purchasePrice,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) Double currentPrice,
        @NotNull @Min(1) Integer quantity,
        @NotBlank @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$") String purchaseDate,
        @NotBlank String status) {
}
