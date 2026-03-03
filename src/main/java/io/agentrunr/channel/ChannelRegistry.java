package io.agentrunr.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Registry that tracks all active channels and remembers the most recently active one.
 * Used by heartbeat and cron jobs to route responses to the right destination.
 *
 * <p>Thread-safe: channels can be registered/unregistered from any thread.</p>
 */
@Component
public class ChannelRegistry {

    private static final Logger log = LoggerFactory.getLogger(ChannelRegistry.class);

    private final Map<String, Channel> channels = new ConcurrentHashMap<>();
    private final AtomicReference<String> lastActiveChannelName = new AtomicReference<>();
    private final RestChannel restChannel = new RestChannel();

    public ChannelRegistry() {
        register(restChannel);
    }

    /**
     * Registers a channel. If it's the first non-REST channel, it becomes the last active.
     */
    public void register(Channel channel) {
        channels.put(channel.getName(), channel);
        log.info("Channel registered: {}", channel.getName());
    }

    /**
     * Unregisters a channel by name.
     */
    public void unregister(String name) {
        channels.remove(name);
        log.info("Channel unregistered: {}", name);
    }

    /**
     * Marks a channel as the most recently active.
     */
    public void markActive(String channelName) {
        if (channels.containsKey(channelName)) {
            lastActiveChannelName.set(channelName);
            log.debug("Last active channel: {}", channelName);
        }
    }

    /**
     * Returns the last active channel, falling back to the REST channel.
     */
    public Channel getLastActiveChannel() {
        String name = lastActiveChannelName.get();
        if (name != null) {
            Channel channel = channels.get(name);
            if (channel != null) return channel;
        }
        return restChannel;
    }

    /**
     * Returns a channel by name, if registered.
     */
    public Optional<Channel> getChannel(String name) {
        return Optional.ofNullable(channels.get(name));
    }

    /**
     * Returns all registered channel names.
     */
    public List<String> listChannels() {
        return List.copyOf(channels.keySet());
    }

    /**
     * Returns the built-in REST channel.
     */
    public RestChannel getRestChannel() {
        return restChannel;
    }

    /**
     * Sends a message to the last active channel.
     */
    public void sendToLastActive(String message) {
        Channel channel = getLastActiveChannel();
        log.debug("Routing message to channel: {}", channel.getName());
        channel.sendMessage(message);
    }
}
