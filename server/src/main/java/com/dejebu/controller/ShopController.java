package com.dejebu.controller;

import com.dejebu.dto.ShopListRequest;
import com.dejebu.dto.ShopListResponse;
import com.dejebu.dto.ShopPurchaseRequest;
import com.dejebu.dto.ShopPurchaseResponse;
import com.dejebu.dto.ShopSellRequest;
import com.dejebu.entity.User;
import com.dejebu.service.AuthService;
import com.dejebu.service.ShopService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shop")
public class ShopController {

    private final AuthService authService;
    private final ShopService shopService;

    public ShopController(AuthService authService, ShopService shopService) {
        this.authService = authService;
        this.shopService = shopService;
    }

    @PostMapping("/list")
    public ShopListResponse list(@Valid @RequestBody ShopListRequest request) {
        User user = authService.validateToken(request.token())
                .orElseThrow(() -> new IllegalArgumentException("登入已失效，請重新登入"));
        return shopService.listShop(user, request.npcId());
    }

    @PostMapping("/buy")
    public ShopPurchaseResponse buy(@Valid @RequestBody ShopPurchaseRequest request) {
        User user = authService.validateToken(request.token())
                .orElseThrow(() -> new IllegalArgumentException("登入已失效，請重新登入"));
        return shopService.purchase(user, request.npcId(), request.itemId());
    }

    @PostMapping("/sell")
    public ShopPurchaseResponse sell(@Valid @RequestBody ShopSellRequest request) {
        User user = authService.validateToken(request.token())
                .orElseThrow(() -> new IllegalArgumentException("登入已失效，請重新登入"));
        return shopService.sell(user, request.npcId(), request.itemId());
    }
}
