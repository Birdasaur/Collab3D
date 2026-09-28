package com.example.collab3d.client;

import com.example.collab3d.common.Pose3d;
import com.example.collab3d.common.Quaterniond;
import javafx.scene.Node;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Transform;

final class FxPoseCodec {
    private FxPoseCodec() {
    }

    static Pose3d capture(Node node, long sampleTimeNanos) {
        Transform t = node.getLocalToSceneTransform();

        Vector c0 = new Vector(t.getMxx(), t.getMyx(), t.getMzx()).normalized();
        Vector rawC1 = new Vector(t.getMxy(), t.getMyy(), t.getMzy());
        Vector c1 = rawC1.subtract(c0.scale(rawC1.dot(c0))).normalized();
        Vector c2 = c0.cross(c1).normalized();
        if (c2.dot(new Vector(t.getMxz(), t.getMyz(), t.getMzz())) < 0.0) {
            c2 = c2.scale(-1.0);
        }
        c1 = c2.cross(c0).normalized();

        Quaterniond rotation = matrixToQuaternion(
                c0.x, c1.x, c2.x,
                c0.y, c1.y, c2.y,
                c0.z, c1.z, c2.z);
        return new Pose3d(
                t.getTx(),
                t.getTy(),
                t.getTz(),
                rotation,
                sampleTimeNanos).normalized();
    }

    static Affine toAffine(Pose3d pose) {
        Pose3d p = pose.normalized();
        Quaterniond q = p.orientation();
        double x = q.x();
        double y = q.y();
        double z = q.z();
        double w = q.w();

        double xx = x * x;
        double yy = y * y;
        double zz = z * z;
        double xy = x * y;
        double xz = x * z;
        double yz = y * z;
        double wx = w * x;
        double wy = w * y;
        double wz = w * z;

        return new Affine(
                1.0 - 2.0 * (yy + zz), 2.0 * (xy - wz), 2.0 * (xz + wy), p.x(),
                2.0 * (xy + wz), 1.0 - 2.0 * (xx + zz), 2.0 * (yz - wx), p.y(),
                2.0 * (xz - wy), 2.0 * (yz + wx), 1.0 - 2.0 * (xx + yy), p.z());
    }

    private static Quaterniond matrixToQuaternion(
            double m00, double m01, double m02,
            double m10, double m11, double m12,
            double m20, double m21, double m22) {

        double x;
        double y;
        double z;
        double w;
        double trace = m00 + m11 + m22;

        if (trace > 0.0) {
            double s = Math.sqrt(trace + 1.0) * 2.0;
            w = 0.25 * s;
            x = (m21 - m12) / s;
            y = (m02 - m20) / s;
            z = (m10 - m01) / s;
        } else if (m00 > m11 && m00 > m22) {
            double s = Math.sqrt(1.0 + m00 - m11 - m22) * 2.0;
            w = (m21 - m12) / s;
            x = 0.25 * s;
            y = (m01 + m10) / s;
            z = (m02 + m20) / s;
        } else if (m11 > m22) {
            double s = Math.sqrt(1.0 + m11 - m00 - m22) * 2.0;
            w = (m02 - m20) / s;
            x = (m01 + m10) / s;
            y = 0.25 * s;
            z = (m12 + m21) / s;
        } else {
            double s = Math.sqrt(1.0 + m22 - m00 - m11) * 2.0;
            w = (m10 - m01) / s;
            x = (m02 + m20) / s;
            y = (m12 + m21) / s;
            z = 0.25 * s;
        }
        return new Quaterniond(x, y, z, w).normalized();
    }

    private record Vector(double x, double y, double z) {
        Vector subtract(Vector other) {
            return new Vector(x - other.x, y - other.y, z - other.z);
        }

        Vector scale(double factor) {
            return new Vector(x * factor, y * factor, z * factor);
        }

        double dot(Vector other) {
            return x * other.x + y * other.y + z * other.z;
        }

        Vector cross(Vector other) {
            return new Vector(
                    y * other.z - z * other.y,
                    z * other.x - x * other.z,
                    x * other.y - y * other.x);
        }

        Vector normalized() {
            double length = Math.sqrt(x * x + y * y + z * z);
            if (length < 1.0e-12 || !Double.isFinite(length)) {
                return new Vector(0.0, 0.0, 1.0);
            }
            return scale(1.0 / length);
        }
    }
}
