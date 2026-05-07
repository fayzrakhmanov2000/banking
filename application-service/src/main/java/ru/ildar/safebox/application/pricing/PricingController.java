package ru.ildar.safebox.application.pricing;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/pricing")
public class PricingController {

    private final PricingService service;

    public PricingController(PricingService service) {
        this.service = service;
    }

    public record PriceRequest(@NotBlank String cellType, @Min(1) int days, String segment) {}
    public record PriceResponse(BigDecimal price) {}

    @PostMapping("/calculate")
    public PriceResponse calculate(@RequestBody PriceRequest req) {
        return new PriceResponse(service.calculate(req.cellType(), req.days(),
                req.segment() == null ? "STANDARD" : req.segment()));
    }
}
