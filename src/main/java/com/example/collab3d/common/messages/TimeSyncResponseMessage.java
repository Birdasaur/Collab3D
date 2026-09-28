package com.example.collab3d.common.messages;

import com.jme3.network.AbstractMessage;
import com.jme3.network.serializing.Serializable;

/**
 * Server-to-client clock synchronization response.
 *
 * <p>The four timestamps used by the client are:</p>
 *
 * <pre>
 * t0 = clientSendTimeNanos
 * t1 = serverReceiveTimeNanos
 * t2 = serverSendTimeNanos
 * t3 = client receive time captured locally when this message arrives
 * </pre>
 *
 * <p>The client then estimates network round-trip delay and the offset between
 * the client and server monotonic clocks without requiring wall-clock time or
 * operating-system clock synchronization.</p>
 */
@Serializable
public final class TimeSyncResponseMessage extends AbstractMessage {

    private long sequence;
    private long clientSendTimeNanos;
    private long serverReceiveTimeNanos;
    private long serverSendTimeNanos;

    /** Required by SpiderMonkey serialization. */
    public TimeSyncResponseMessage() {
    }

    public TimeSyncResponseMessage(
            long sequence,
            long clientSendTimeNanos,
            long serverReceiveTimeNanos,
            long serverSendTimeNanos) {
        this.sequence = sequence;
        this.clientSendTimeNanos = clientSendTimeNanos;
        this.serverReceiveTimeNanos = serverReceiveTimeNanos;
        this.serverSendTimeNanos = serverSendTimeNanos;
    }

    public long getSequence() {
        return sequence;
    }

    public long getClientSendTimeNanos() {
        return clientSendTimeNanos;
    }

    public long getServerReceiveTimeNanos() {
        return serverReceiveTimeNanos;
    }

    public long getServerSendTimeNanos() {
        return serverSendTimeNanos;
    }
}
