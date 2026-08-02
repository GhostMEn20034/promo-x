package com.promox.core;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;


public class ModularityTest {
    private final ApplicationModules modules = ApplicationModules.of(PromoXCoreApplication.class);

    @Test
    void verifyModularStructure() {
        modules.verify();
    }
}
