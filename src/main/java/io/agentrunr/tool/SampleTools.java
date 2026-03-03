package io.agentrunr.tool;

import io.agentrunr.core.Agent;
import io.agentrunr.core.AgentResult;
import io.agentrunr.core.ToolRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Sample tools demonstrating the agent tool system.
 * Registers tools at startup for use by agents.
 */
@Component
public class SampleTools {

    private static final Logger log = LoggerFactory.getLogger(SampleTools.class);

    private final ToolRegistry toolRegistry;

    public SampleTools(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    @PostConstruct
    public void registerTools() {
        toolRegistry.registerAgentTool("get_current_time",
                "Get the current date and time.",
                """
                {"type":"object","properties":{}}""",
                (args, ctx) -> {
                    String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    return AgentResult.of("Current time: " + time);
                });

        toolRegistry.registerAgentTool("greet_user",
                "Greet a user by name.",
                """
                {"type":"object","properties":{"name":{"type":"string","description":"Name to greet"}},"required":[]}""",
                (args, ctx) -> {
                    String name = (String) args.getOrDefault("name", ctx.get("user_name", "stranger"));
                    ctx.set("greeted", "true");
                    return AgentResult.withContext(
                            "Hello, " + name + "! How can I help you today?",
                            Map.of("user_name", name)
                    );
                });

        toolRegistry.registerAgentTool("transfer_to_billing",
                "Transfer the conversation to a billing specialist.",
                """
                {"type":"object","properties":{}}""",
                (args, ctx) -> {
                    Agent billingAgent = new Agent(
                            "BillingAgent",
                            "gpt-4.1",
                            "You are a billing specialist. Help users with invoices, payments, and refunds. Be professional and thorough.",
                            java.util.List.of("get_current_time")
                    );
                    return AgentResult.handoff(billingAgent);
                });

        toolRegistry.registerAgentTool("transfer_to_technical",
                "Transfer the conversation to a technical support specialist.",
                """
                {"type":"object","properties":{}}""",
                (args, ctx) -> {
                    Agent techAgent = new Agent(
                            "TechnicalAgent",
                            "gpt-4.1",
                            "You are a technical support specialist. Help users troubleshoot issues with patience and clarity.",
                            java.util.List.of("get_current_time")
                    );
                    return AgentResult.handoff(techAgent);
                });

        log.info("Registered {} sample tools", 4);
    }
}
