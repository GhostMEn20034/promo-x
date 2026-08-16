package com.promox.core.product.internal.persistence;

import com.promox.core.product.internal.dto.CreateProductRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ProductRepositoryImpl implements ProductRepositoryCustom {

    private final JdbcTemplate jdbcTemplate;
    /*
    * Defined bigger batch size than usually to reduce the amount of DB trips
    * */
    private static final short BATCH_SIZE = 200;

    public ProductRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void saveAllInBatch(Long storefrontId, List<CreateProductRequest> items) {
        if (items.isEmpty()) {
            return;
        }

        String sql = """
            INSERT INTO product (external_id, sku, name, storefront_id, created_at, updated_at)
            VALUES (?, ?, ?, ?, NOW(), NOW())
            """;

        jdbcTemplate.batchUpdate(
            sql,
            items,
            BATCH_SIZE,
            (ps, item) -> {
                ps.setString(1, item.externalId());
                ps.setString(2, item.sku());
                ps.setString(3, item.name());
                ps.setLong(4, storefrontId);
            }
        );
    }
}
