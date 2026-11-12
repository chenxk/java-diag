package com.javadiag.probe.process;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppNameMapperTest {

    @Test
    void mapsSubstringRulesInOrder() {
        AppNameMapper mapper = AppNameMapper.parse(
                "order-service.jar=order-service,com.foo.PayApplication=pay-service", List.of());
        assertEquals("order-service", mapper.resolveName("/opt/order-service.jar"));
        assertEquals("pay-service", mapper.resolveName("com.foo.PayApplication"));
    }

    @Test
    void cliAppMapOverridesEnv() {
        AppNameMapper mapper = AppNameMapper.parse(
                "old.jar=old", List.of("new.jar=new-service"));
        assertEquals("new-service", mapper.resolveName("new.jar"));
    }

    @Test
    void regexRulePrefix() {
        AppNameMapper mapper = AppNameMapper.parse(
                "regex:^com\\.example\\.\\w+=example-app", List.of());
        assertEquals("example-app", mapper.resolveName("com.example.OrderMain"));
    }

    @Test
    void fallbackShortNameForFqcn() {
        assertEquals("BootApp", AppNameMapper.fallbackShortName("com.example.BootApp"));
        assertEquals("my.jar", AppNameMapper.fallbackShortName("C:\\apps\\my.jar"));
    }

    @Test
    void fallbackShortNameNeverReturnsExeForJavaLauncher() {
        assertEquals("unknown", AppNameMapper.fallbackShortName("C:\\Files\\Java\\jdk-17\\bin\\java.exe"));
        assertEquals("unknown", AppNameMapper.fallbackShortName("java.exe"));
    }
}
