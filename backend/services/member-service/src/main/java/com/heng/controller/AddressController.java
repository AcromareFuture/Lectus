package com.heng.controller;

import com.heng.biz.MemberCenterService;
import com.heng.domain.VMemberAddress;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/addresses")
public class AddressController {
    private final MemberCenterService memberCenterService;

    public AddressController(MemberCenterService memberCenterService) {
        this.memberCenterService = memberCenterService;
    }

    @GetMapping
    public ApiResponse<List<VMemberAddress>> list(@RequestAttribute("viridac.memberId") Long memberId) {
        return ApiResponse.ok(memberCenterService.addresses(memberId));
    }

    @PostMapping
    public ApiResponse<VMemberAddress> create(@RequestAttribute("viridac.memberId") Long memberId,
                                             @RequestBody VMemberAddress form) {
        return ApiResponse.ok(memberCenterService.saveAddress(memberId, null, form));
    }

    @PutMapping("/{id}")
    public ApiResponse<VMemberAddress> update(@RequestAttribute("viridac.memberId") Long memberId,
                                             @PathVariable Long id, @RequestBody VMemberAddress form) {
        return ApiResponse.ok(memberCenterService.saveAddress(memberId, id, form));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@RequestAttribute("viridac.memberId") Long memberId, @PathVariable Long id) {
        memberCenterService.deleteAddress(memberId, id);
        return ApiResponse.ok(null);
    }

    @PutMapping("/{id}/default")
    public ApiResponse<Void> setDefault(@RequestAttribute("viridac.memberId") Long memberId, @PathVariable Long id) {
        memberCenterService.setDefaultAddress(memberId, id);
        return ApiResponse.ok(null);
    }
}
