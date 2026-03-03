package io.agentrunr.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Telegram channel adapter that wraps the existing TelegramChannel to implement the Channel interface.
 * Routes messages to a specific Telegram chat ID.
 */
public class TelegramBotChannel implements Channel {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotChannel.class);

    private final TelegramChannel telegramChannel;
    private final long chatId;

    public TelegramBotChannel(TelegramChannel telegramChannel, long chatId) {
        this.telegramChannel = telegramChannel;
        this.chatId = chatId;
    }

    @Override
    public void sendMessage(String message) {
        log.debug("Sending message via Telegram to chat {}", chatId);
        telegramChannel.sendMessage(chatId, message);
    }

    @Override
    public String getName() {
        return "telegram-" + chatId;
    }

    public long getChatId() {
        return chatId;
    }
}
