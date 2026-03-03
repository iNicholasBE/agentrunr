package io.agentrunr.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class McpPropertiesTest {

    @Test
    void shouldCreateWithEmptyServerList() {
        var props = new McpProperties(null);
        assertNotNull(props.servers());
        assertTrue(props.servers().isEmpty());
    }

    @Test
    void shouldCreateWithServerList() {
        var server = new McpProperties.McpServerConfig(
                "test", "http://localhost:8080/sse", null,
                null, true, null, null, null, null, null);
        var props = new McpProperties(List.of(server));
        assertEquals(1, props.servers().size());
        assertEquals("test", props.servers().get(0).name());
    }

    @Test
    void shouldDefaultTransportToSse() {
        var config = new McpProperties.McpServerConfig(
                "test", "http://localhost/sse", null,
                null, true, null, null, null, null, null);
        assertEquals("sse", config.transport());
        assertTrue(config.isSse());
        assertFalse(config.isStdio());
    }

    @Test
    void shouldRecognizeStdioTransport() {
        var config = new McpProperties.McpServerConfig(
                "fs", null, null,
                null, true, "stdio", "npx", List.of("-y", "mcp-server"), null, null);
        assertEquals("stdio", config.transport());
        assertTrue(config.isStdio());
        assertFalse(config.isSse());
    }

    @Test
    void shouldDefaultHeadersToEmptyMap() {
        var config = new McpProperties.McpServerConfig(
                "test", "http://localhost/sse", null,
                null, true, null, null, null, null, null);
        assertNotNull(config.headers());
        assertTrue(config.headers().isEmpty());
    }

    @Test
    void shouldDefaultArgsToEmptyList() {
        var config = new McpProperties.McpServerConfig(
                "test", null, null,
                null, true, "stdio", "npx", null, null, null);
        assertNotNull(config.args());
        assertTrue(config.args().isEmpty());
    }

    @Test
    void shouldDefaultEnvToEmptyMap() {
        var config = new McpProperties.McpServerConfig(
                "test", null, null,
                null, true, "stdio", "npx", null, null, null);
        assertNotNull(config.env());
        assertTrue(config.env().isEmpty());
    }

    @Test
    void shouldDefaultTimeoutTo30Seconds() {
        var config = new McpProperties.McpServerConfig(
                "test", "http://localhost/sse", null,
                null, true, null, null, null, null, null);
        assertEquals(30, config.requestTimeoutSeconds());
    }

    @Test
    void shouldUseCustomTimeout() {
        var config = new McpProperties.McpServerConfig(
                "test", "http://localhost/sse", null,
                null, true, null, null, null, null, 60);
        assertEquals(60, config.requestTimeoutSeconds());
    }

    @Test
    void shouldMergePasswordIntoResolvedHeaders() {
        var config = new McpProperties.McpServerConfig(
                "test", "http://localhost/sse", "my-secret",
                null, true, null, null, null, null, null);
        var resolved = config.resolvedHeaders();
        assertEquals("my-secret", resolved.get("Password"));
    }

    @Test
    void shouldNotOverrideExistingPasswordHeader() {
        var config = new McpProperties.McpServerConfig(
                "test", "http://localhost/sse", "overridden",
                Map.of("Password", "keep-this"), true, null, null, null, null, null);
        var resolved = config.resolvedHeaders();
        assertEquals("keep-this", resolved.get("Password"));
    }

    @Test
    void shouldMergeCustomHeadersWithPassword() {
        var config = new McpProperties.McpServerConfig(
                "test", "http://localhost/sse", "pw123",
                Map.of("Authorization", "Bearer tok"), true, null, null, null, null, null);
        var resolved = config.resolvedHeaders();
        assertEquals("pw123", resolved.get("Password"));
        assertEquals("Bearer tok", resolved.get("Authorization"));
    }

    @Test
    void shouldReturnEmptyHeadersWhenNoPasswordOrHeaders() {
        var config = new McpProperties.McpServerConfig(
                "test", "http://localhost/sse", null,
                null, true, null, null, null, null, null);
        assertTrue(config.resolvedHeaders().isEmpty());
    }

    @Test
    void shouldIgnoreBlankPassword() {
        var config = new McpProperties.McpServerConfig(
                "test", "http://localhost/sse", "  ",
                null, true, null, null, null, null, null);
        assertTrue(config.resolvedHeaders().isEmpty());
    }

    @Test
    void shouldPreserveEnabledFlag() {
        var enabled = new McpProperties.McpServerConfig(
                "a", "http://localhost/sse", null,
                null, true, null, null, null, null, null);
        var disabled = new McpProperties.McpServerConfig(
                "b", "http://localhost/sse", null,
                null, false, null, null, null, null, null);
        assertTrue(enabled.enabled());
        assertFalse(disabled.enabled());
    }
}
