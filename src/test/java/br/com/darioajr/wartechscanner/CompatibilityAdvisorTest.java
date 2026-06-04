/*
 * Copyright 2024-present Dario Alves Junior
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package br.com.darioajr.wartechscanner;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CompatibilityAdvisorTest {

    private static ScanResult resultWith(String... techNames) {
        var result = new ScanResult();
        for (var name : techNames) {
            var t = new DetectedTechnology(name);
            t.score = 10;
            result.technologies.add(t);
        }
        return result;
    }

    // ── EAP 8 ────────────────────────────────────────────────────────────────

    @Test
    void eap8_alwaysAddsNamespaceHint() {
        var hints = CompatibilityAdvisor.advise(resultWith(), new MigrationTarget("8.0", 0));
        assertTrue(hints.stream().anyMatch(h -> h.contains("javax.* → jakarta.*")));
    }

    @Test
    void eap8_ejb_addsEjbHints() {
        var hints = CompatibilityAdvisor.advise(resultWith("EJB"), new MigrationTarget("8.0", 0));
        assertTrue(hints.stream().anyMatch(h -> h.contains("javax.ejb.*")));
    }

    @Test
    void eap8_jpa_addsJpaHint() {
        var hints = CompatibilityAdvisor.advise(resultWith("JPA"), new MigrationTarget("8.0", 0));
        assertTrue(hints.stream().anyMatch(h -> h.contains("javax.persistence.*")));
    }

    @Test
    void eap8_hibernate_addsOrmHint() {
        var hints = CompatibilityAdvisor.advise(resultWith("Hibernate"), new MigrationTarget("8.1", 0));
        assertTrue(hints.stream().anyMatch(h -> h.contains("hbm.xml")));
    }

    @Test
    void eap8_spring_addsSpringHint() {
        var hints = CompatibilityAdvisor.advise(resultWith("Spring"), new MigrationTarget("8.0", 0));
        assertTrue(hints.stream().anyMatch(h -> h.contains("Spring")));
    }

    // ── EAP 7 ────────────────────────────────────────────────────────────────

    @Test
    void eap7_addsNamespaceWarning() {
        var hints = CompatibilityAdvisor.advise(resultWith(), new MigrationTarget("7.4", 0));
        assertTrue(hints.stream().anyMatch(h -> h.contains("javax.* is still used")));
    }

    @Test
    void eap7_ejb_addsEntityBeanWarning() {
        var hints = CompatibilityAdvisor.advise(resultWith("EJB"), new MigrationTarget("7.4", 0));
        assertTrue(hints.stream().anyMatch(h -> h.contains("entity beans")));
    }

    // ── Java version ─────────────────────────────────────────────────────────

    @Test
    void java17_addsStrongEncapsulationHint() {
        var hints = CompatibilityAdvisor.advise(resultWith(), new MigrationTarget(null, 17));
        assertTrue(hints.stream().anyMatch(h -> h.contains("strong encapsulation")));
    }

    @Test
    void java21_addsVirtualThreadsHint() {
        var hints = CompatibilityAdvisor.advise(resultWith(), new MigrationTarget(null, 21));
        assertTrue(hints.stream().anyMatch(h -> h.contains("Virtual threads")));
    }

    @Test
    void java11_jaxws_addsRemovalHint() {
        var hints = CompatibilityAdvisor.advise(resultWith("JAX-WS/SOAP"), new MigrationTarget(null, 11));
        assertTrue(hints.stream().anyMatch(h -> h.contains("JAX-WS")));
    }

    // ── No target ────────────────────────────────────────────────────────────

    @Test
    void noTarget_returnsEmptyList() {
        var hints = CompatibilityAdvisor.advise(resultWith("EJB", "JPA"), new MigrationTarget(null, 0));
        assertTrue(hints.isEmpty());
    }

    // ── MigrationTarget helpers ──────────────────────────────────────────────

    @Test
    void migrationTarget_isEap8OrLater() {
        assertTrue(new MigrationTarget("8.0", 0).isEap8OrLater());
        assertTrue(new MigrationTarget("8.1", 0).isEap8OrLater());
        assertFalse(new MigrationTarget("7.4", 0).isEap8OrLater());
        assertFalse(new MigrationTarget(null, 0).isEap8OrLater());
    }

    @Test
    void migrationTarget_isEap7() {
        assertTrue(new MigrationTarget("7.0", 0).isEap7());
        assertTrue(new MigrationTarget("7.4", 0).isEap7());
        assertFalse(new MigrationTarget("8.0", 0).isEap7());
        assertFalse(new MigrationTarget("6.4", 0).isEap7());
    }
}
