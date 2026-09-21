package com.gelox.backend.voz.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class VozDtoValidacionTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void interpretarRequestValidoNoTieneViolaciones() {
        var request = new VozInterpretarRequest("vende dos festival", 0.9);
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void interpretarRequestRechazaTextoVacio() {
        var request = new VozInterpretarRequest("", 0.9);
        Set<ConstraintViolation<VozInterpretarRequest>> violaciones = validator.validate(request);
        assertThat(violaciones).isNotEmpty();
    }

    @Test
    void interpretarRequestRechazaConfianzaFueraDeRango() {
        assertThat(validator.validate(new VozInterpretarRequest("vende dos festival", 1.5))).isNotEmpty();
        assertThat(validator.validate(new VozInterpretarRequest("vende dos festival", -0.1))).isNotEmpty();
    }

    @Test
    void confirmarRequestRechazaCamposNulos() {
        assertThat(validator.validate(new VozConfirmarRequest(null, true))).isNotEmpty();
        assertThat(validator.validate(new VozConfirmarRequest(UUID.randomUUID(), null))).isNotEmpty();
    }

    @Test
    void confirmarRequestValidoNoTieneViolaciones() {
        assertThat(validator.validate(new VozConfirmarRequest(UUID.randomUUID(), false))).isEmpty();
    }
}
