package com.sentinelx.retail.bootstrap;

import java.math.BigDecimal;
import java.util.List;

import com.sentinelx.retail.entity.Product;
import com.sentinelx.retail.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds a synthetic retail catalog so the storefront and the SOC dashboard
 * have realistic data on a fresh database. Runs only when the products table
 * is empty, so operator-created catalog entries are never touched.
 */
@Component
public class RetailDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RetailDataSeeder.class);

    private record Seed(String sku, String name, String description, String category, String price, int stock) {
    }

    private static final List<Seed> CATALOG = List.of(
            new Seed("SNX-KEY-001", "SentinelX YubiKey 5 NFC", "Hardware MFA key, FIDO2 certified", "hardware", "49.99", 250),
            new Seed("SNX-KEY-002", "SentinelX YubiKey 5C", "USB-C hardware MFA key", "hardware", "52.99", 180),
            new Seed("SNX-CAM-010", "EdgeCam 4K Dome", "Outdoor security camera with IR", "hardware", "189.00", 60),
            new Seed("SNX-SEN-020", "Door/Window Contact Sensor", "Wireless entry sensor", "hardware", "24.50", 400),
            new Seed("SNX-SUB-ADV", "SOC Console — Advanced (1yr)", "Advanced analytics subscription", "subscription", "1200.00", 999),
            new Seed("SNX-SUB-ENT", "SOC Console — Enterprise (1yr)", "Enterprise tier with SSO + SIEM export", "subscription", "4800.00", 999),
            new Seed("SNX-TRN-PHY", "Blue Team Fundamentals (virtual)", "Live 2-day incident response training", "training", "899.00", 40),
            new Seed("SNX-TRN-RED", "Red Team Ops Bootcamp", "Hands-on offensive security training", "training", "1499.00", 25),
            new Seed("SNX-APP-DEV", "Threat Modeling Workbook", "Practical threat modeling exercises", "training", "39.00", 500),
            new Seed("SNX-RUG-SOC", "SentinelX Ops Rug", "Machine-washable NOC/SOC rug", "merch", "79.00", 120),
            new Seed("SNX-MUG-BLU", "Blue Team Mug", "Ceramic mug, 350ml", "merch", "14.99", 750),
            new Seed("SNX-TSH-BLK", "Threat Hunter Tee", "Organic cotton tee", "merch", "22.00", 300));

    private final ProductRepository products;

    public RetailDataSeeder(ProductRepository products) {
        this.products = products;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (products.count() > 0) {
            return;
        }
        CATALOG.forEach(seed -> {
            Product p = new Product();
            p.setSku(seed.sku());
            p.setName(seed.name());
            p.setDescription(seed.description());
            p.setCategory(seed.category());
            p.setPrice(new BigDecimal(seed.price()));
            p.setCurrency("USD");
            p.setStock(seed.stock());
            p.setActive(true);
            products.save(p);
        });
        log.info("seeded {} synthetic retail products", CATALOG.size());
    }
}