package de.mariokorte.huaweiapi.controller;

import de.mariokorte.huaweiapi.service.HuaweiApiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/energy")
public class EnergyController {

    @Autowired
    private HuaweiApiService huaweiApiService;

    @GetMapping("/meterActivePower")
    public int getMeterActivePower(@RequestHeader("username") String username, @RequestHeader("password") String password) {
        return Double.valueOf(huaweiApiService.getMeterActivePower(username, password)).intValue();
    }

    @GetMapping("/invActivePower")
    public int getInvActivePower(@RequestHeader("username") String username, @RequestHeader("password") String password) {
        return Double.valueOf(huaweiApiService.getInvActivePower(username, password)).intValue();
    }

    @GetMapping("/householdActivePower")
    public int getHouseholdActivePower(@RequestHeader("username") String username, @RequestHeader("password") String password) {
        return Double.valueOf(huaweiApiService.getHouseholdActivePower(username, password)).intValue();
    }
}