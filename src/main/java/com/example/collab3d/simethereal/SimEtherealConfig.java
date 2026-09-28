package com.example.collab3d.simethereal;

import com.simsilica.ethereal.net.ObjectStateProtocol;
import com.simsilica.ethereal.zone.ZoneGrid;
import com.simsilica.mathd.Vec3i;
import com.simsilica.mathd.bits.QuatBits;
import com.simsilica.mathd.bits.Vec3Bits;

/**
 * Shared SimEthereal protocol/grid configuration used by the server and clients.
 *
 * <p>This class intentionally contains the SimEthereal/SimMath types.  The rest
 * of the application uses the neutral Pose3d model instead.</p>
 */
final class SimEtherealConfig {
    private static final int ZONE_SIZE = 512;
    private static final int POSITION_BITS = 21;
    private static final int ROTATION_BITS = 16;
    private static final int ZONE_BITS = 8;
    private static final int OBJECT_ID_BITS = 64;

    private final ObjectStateProtocol objectProtocol;
    private final ZoneGrid zoneGrid;
    private final Vec3i zoneRadius;

    private SimEtherealConfig(
            ObjectStateProtocol objectProtocol,
            ZoneGrid zoneGrid,
            Vec3i zoneRadius) {
        this.objectProtocol = objectProtocol;
        this.zoneGrid = zoneGrid;
        this.zoneRadius = zoneRadius;
    }

    static SimEtherealConfig create() {
        Vec3Bits positionBits = new Vec3Bits(
                -32.0f,
                ZONE_SIZE + 32.0f,
                POSITION_BITS);
        QuatBits rotationBits = new QuatBits(ROTATION_BITS);

        ObjectStateProtocol protocol = new ObjectStateProtocol(
                ZONE_BITS,
                OBJECT_ID_BITS,
                positionBits,
                rotationBits);

        ZoneGrid grid = new ZoneGrid(
                ZONE_SIZE,
                ZONE_SIZE,
                ZONE_SIZE);

        Vec3i radius = new Vec3i(1, 1, 1);

        return new SimEtherealConfig(protocol, grid, radius);
    }

    ObjectStateProtocol objectProtocol() {
        return objectProtocol;
    }

    ZoneGrid zoneGrid() {
        return zoneGrid;
    }

    Vec3i zoneRadius() {
        return zoneRadius;
    }
}
