package com.example.collab3d.common.messages;

import com.jme3.network.AbstractMessage;
import com.jme3.network.serializing.Serializable;

/**
 * Client-to-server clock synchronization request.
 *
 * <p>The client captures {@code clientSendTimeNanos} from its own monotonic
 * {@link System#nanoTime()} clock immediately before sending this message. The
 * server echoes that value in {@link TimeSyncResponseMessage} and adds server
 * receive/send timestamps from its own monotonic clock domain.</p>
 *
 * <p>The sequence number is not used to order pose traffic. It exists only to
 * correlate clock-sync requests and responses and to reject stale responses.</p>
 */
@Serializable
public final class TimeSyncRequestMessage extends AbstractMessage {

    private long sequence;
    private long clientSendTimeNanos;

    /** Required by SpiderMonkey serialization. */
    public TimeSyncRequestMessage() {
    }

    public TimeSyncRequestMessage(
            long sequence,
            long clientSendTimeNanos) {
        this.sequence = sequence;
        this.clientSendTimeNanos = clientSendTimeNanos;
    }

    public long getSequence() {
        return sequence;
    }

    public long getClientSendTimeNanos() {
        return clientSendTimeNanos;
    }
}
