package io.agentrunr.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * REST API channel. Buffers messages for polling by the web UI.
 * Messages sent here are accumulated and can be retrieved via the chat endpoint.
 */
public class RestChannel implements Channel {

    private static final Logger log = LoggerFactory.getLogger(RestChannel.class);

    private final ConcurrentLinkedQueue<String> pendingMessages = new ConcurrentLinkedQueue<>();

    @Override
    public void sendMessage(String message) {
        log.debug("REST channel buffering message: {}...", message.substring(0, Math.min(50, message.length())));
        pendingMessages.add(message);
    }

    @Override
    public String getName() {
        return "rest";
    }

    /**
     * Drains all pending messages.
     */
    public java.util.List<String> drainMessages() {
        var messages = new java.util.ArrayList<String>();
        String msg;
        while ((msg = pendingMessages.poll()) != null) {
            messages.add(msg);
        }
        return messages;
    }

    /**
     * Returns true if there are pending messages.
     */
    public boolean hasPendingMessages() {
        return !pendingMessages.isEmpty();
    }
}
