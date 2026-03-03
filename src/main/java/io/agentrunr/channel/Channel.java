package io.agentrunr.channel;

/**
 * A message delivery channel for the agent.
 * Implementations route agent responses to their destination (REST, Telegram, WebSocket, etc.).
 */
public interface Channel {

    /**
     * Sends a message through this channel.
     *
     * @param message the message content to deliver
     */
    void sendMessage(String message);

    /**
     * Returns the display name of this channel.
     */
    String getName();
}
