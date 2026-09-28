package com.example.collab3d.common.messages;

import com.example.collab3d.common.ParticipantResultState;
import com.jme3.network.AbstractMessage;
import com.jme3.network.serializing.Serializable;

/** Server -> client replication of authoritative participant result state. */
@Serializable
public final class ParticipantResultStateMessage extends AbstractMessage {

    private long objectId;
    private long shotsFired;
    private long hitCount;
    private long score;

    public ParticipantResultStateMessage() {
    }

    public ParticipantResultStateMessage(ParticipantResultState state) {
        this(
                state.objectId(),
                state.shotsFired(),
                state.hitCount(),
                state.score());
    }

    public ParticipantResultStateMessage(
            long objectId,
            long shotsFired,
            long hitCount,
            long score) {
        this.objectId = objectId;
        this.shotsFired = shotsFired;
        this.hitCount = hitCount;
        this.score = score;
    }

    public long getObjectId() {
        return objectId;
    }

    public long getShotsFired() {
        return shotsFired;
    }

    public long getHitCount() {
        return hitCount;
    }

    public long getScore() {
        return score;
    }

    public ParticipantResultState toState() {
        return new ParticipantResultState(
                objectId,
                shotsFired,
                hitCount,
                score);
    }
}
