package com.lectus.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public final class Contracts {
    private Contracts() {}
    public record Register(@NotBlank @Email @Size(max=254) String email,
                           @NotBlank @Size(min=10,max=64) String password,
                           @NotBlank @Size(max=40) String displayName) {}
    public record Login(@NotBlank @Email @Size(max=254) String email,
                        @NotBlank @Size(max=64) String password) {}
    public record User(String id, String email, String displayName) {}
    public record Session(String token, String expiresAt, User user) {}
    public record Sku(String id, String productId, String size, String color, int stock) {}
    public record Product(String id, String name, String en, String brand, String category,
                          long priceCents, String image, String description, String tag,
                          List<Sku> skus) {}
    public record CartWrite(@NotBlank @Size(max=36) String skuId,
                            @NotNull @Min(1) @Max(99) Integer quantity,
                            @NotNull Boolean checked) {}
    public record CartItem(String skuId, String productId, String name, String image,
                           String size, String color, long unitPriceCents, int quantity,
                           boolean checked, int stock) {}
    public record AddressWrite(@NotBlank @Size(max=80) String recipient,
                               @NotBlank @Pattern(regexp="\\+?[0-9 ()-]{6,30}") String phone,
                               @NotBlank @Size(min=2,max=200) String region,
                               @NotBlank @Size(min=4,max=300) String detail,
                               @NotNull Boolean isDefault) {}
    public record Address(String id, String recipient, String phone, String region,
                           String detail, boolean isDefault) {}
    public record OrderLine(@NotBlank @Size(max=36) String skuId,
                             @NotNull @Min(1) @Max(99) Integer quantity) {}
    public record OrderWrite(@NotBlank @Size(max=36) String addressId,
                             @NotEmpty @Size(max=50) List<@Valid OrderLine> items,
                             boolean fromCart) {}
    public record OrderItem(String skuId, String productId, String name, String image,
                            String size, String color, long unitPriceCents, int quantity) {}
    public record Order(String id, String status, long totalCents, String currency,
                         Address address, String createdAt, List<OrderItem> items) {}
}
