package com.heng;

import com.heng.controller.OrderController.CreateRequest;
import com.heng.controller.OrderController.ItemRequest;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrderRequestTests {
    @Test
    void nestedOrderItemsRequireSkuAndQuantity() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertFalse(validator.validate(new CreateRequest(
                    List.of(new ItemRequest(null, 1, null)), 1L, null, 0, null, 100L)).isEmpty());
            assertFalse(validator.validate(new CreateRequest(
                    List.of(new ItemRequest(1L, null, null)), 1L, null, 0, null, 100L)).isEmpty());
            assertFalse(validator.validate(new CreateRequest(
                    List.of(new ItemRequest(1L, 1000, null)), 1L, null, 0, null, 100L)).isEmpty());
            assertFalse(validator.validate(new CreateRequest(
                    Arrays.asList((ItemRequest) null), 1L, null, 0, null, 100L)).isEmpty());
            assertTrue(validator.validate(new CreateRequest(
                    List.of(new ItemRequest(1L, 1, null)), 1L, null, 0, null, 100L)).isEmpty());
        }
    }
}
