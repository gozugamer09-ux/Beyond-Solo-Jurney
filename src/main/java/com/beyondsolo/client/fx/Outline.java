package com.beyondsolo.client.fx;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Vector3f;

import java.util.Arrays;

/**
 * The outline of a model, read off it while it is drawn: the edges on its silhouette, where the series draws the
 * burning edge of dark power and the flames that lick off it ({@link Aura}).
 *
 * <p>{@link #tap} wraps a buffer source: every vertex goes on to be drawn as usual, and every entity quad (vanilla
 * model parts and GeckoLib cubes alike, both drawn in the {@code NEW_ENTITY} format) is kept, corners and normal, in
 * the space it was drawn in: in the world that is camera-relative and lined up with the world (the camera turns in
 * the model-view matrix, not the pose), on a screen it is the screen's own space. {@link #solve} then finds the
 * silhouette edges: the edges of the faces turned to the eye whose neighbour across the edge is turned away. Entity
 * models are boxes, so the neighbour across an edge faces the way from the face's middle out to that edge, which is all
 * the test needs: no list of neighbours, any model, one pass. Edges where two boxes touch (an arm against the body)
 * count too, which draws the light that shows through the seams of a shadow's armour.</p>
 *
 * <p>One shared outline, reset by {@link #begin}: everything here runs on the render thread, one model at a time.</p>
 */
public final class Outline {
    /** Floats kept per quad: four corners, then the normal. */
    private static final int QUAD = 15;
    /** Floats kept per silhouette edge: both ends, the outward direction, the length, 1 if on the outer silhouette. */
    static final int EDGE = 11;
    /** Cells across the coverage mask that tells the outer silhouette from the seams inside it. */
    private static final int GRID = 64;
    private static final int MAX_QUADS = 8192;
    private static final Outline SHARED = new Outline();

    private float[] quads = new float[QUAD * 512];
    private int quadCount;
    private float[] edges = new float[EDGE * 512];
    private int edgeCount;
    private float length;
    private float outerLength;
    private float eyeX, eyeY, eyeZ;
    private boolean ortho;
    private boolean[] front = new boolean[512];
    private final boolean[] mask = new boolean[GRID * GRID];
    private final float[] proj = new float[2];
    // the view's axes, to lay the model flat on the mask: left, up and forward (in the world, the camera's)
    private float lx, ly, lz, ux, uy, uz, fx, fy, fz;
    private float minX, minY, cellX, cellY;

    private Outline() {}

    /** The shared outline, emptied. */
    public static Outline begin() {
        SHARED.quadCount = 0;
        SHARED.edgeCount = 0;
        SHARED.length = 0;
        SHARED.outerLength = 0;
        return SHARED;
    }

    /** A buffer source that draws through {@code inner} and keeps every entity quad drawn through it. */
    public MultiBufferSource tap(MultiBufferSource inner) {
        return type -> {
            VertexConsumer out = inner.getBuffer(type);
            return type.format() == DefaultVertexFormat.NEW_ENTITY ? new Tap(out) : out;
        };
    }

    /** A buffer source that draws nothing and keeps every entity quad: to read the shape of a model drawn already. */
    public MultiBufferSource record() {
        return type -> type.format() == DefaultVertexFormat.NEW_ENTITY ? new Tap(null) : NOTHING;
    }

    public int quads() {
        return quadCount;
    }

    public int edges() {
        return edgeCount;
    }

    /** The silhouette's total length (in the units it was drawn in), seams inside it included. */
    public float length() {
        return length;
    }

    /** The length of the outer silhouette only (in the units it was drawn in). */
    public float outerLength() {
        return outerLength;
    }

    /** True if edge {@code i} is on the outer silhouette, false for a seam inside it (an arm against the body). */
    public boolean outer(int i) {
        return edges[i * EDGE + 10] > 0.5f;
    }

    public boolean ortho() {
        return ortho;
    }

    /** The eye the silhouette was found for (with {@link #ortho}, the direction toward the viewer). */
    public float eyeX() {
        return eyeX;
    }

    public float eyeY() {
        return eyeY;
    }

    public float eyeZ() {
        return eyeZ;
    }

    float edge(int i, int field) {
        return edges[i * EDGE + field];
    }

    /** Finds the silhouette as the world's camera sees it (the model drawn in the world, camera-relative). */
    public Outline solveWorld() {
        Camera c = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f l = c.getLeftVector(), u = c.getUpVector(), f = c.getLookVector();
        lx = l.x();
        ly = l.y();
        lz = l.z();
        ux = u.x();
        uy = u.y();
        uz = u.z();
        fx = f.x();
        fy = f.y();
        fz = f.z();
        return solve(0, 0, 0, false);
    }

    /** Finds the silhouette of a model drawn on a screen: seen from far away, looking down the screen's depth. */
    public Outline solveScreen() {
        lx = 1;
        ly = 0;
        lz = 0;
        ux = 0;
        uy = 1;
        uz = 0;
        fx = 0;
        fy = 0;
        fz = -1;
        return solve(0, 0, 1, true);
    }

    /**
     * Finds the silhouette as seen from {@code (ex, ey, ez)}, in the space the model was drawn in (the camera sits at
     * the origin in the world). With {@code ortho} the view comes from far away along {@code (ex, ey, ez)}, the
     * direction toward the viewer: a model drawn on a screen. Then tells the outer silhouette from the seams inside
     * it: the model's faces are laid flat on a coarse mask, and an edge is outer when the mask is empty just past it.
     */
    private Outline solve(float ex, float ey, float ez, boolean ortho) {
        this.eyeX = ex;
        this.eyeY = ey;
        this.eyeZ = ez;
        this.ortho = ortho;
        edgeCount = 0;
        length = 0;
        outerLength = 0;
        if (front.length < quadCount) front = new boolean[Math.max(quadCount, front.length * 2)];
        float[] q = quads;
        for (int f = 0; f < quadCount; f++) {
            front[f] = false;
            int o = f * QUAD;
            float cx = (q[o] + q[o + 3] + q[o + 6] + q[o + 9]) * 0.25f;
            float cy = (q[o + 1] + q[o + 4] + q[o + 7] + q[o + 10]) * 0.25f;
            float cz = (q[o + 2] + q[o + 5] + q[o + 8] + q[o + 11]) * 0.25f;
            // the face's own normal from its diagonals (the model's normal can be zero or flat-shaded oddly)
            float d1x = q[o + 6] - q[o], d1y = q[o + 7] - q[o + 1], d1z = q[o + 8] - q[o + 2];
            float d2x = q[o + 9] - q[o + 3], d2y = q[o + 10] - q[o + 4], d2z = q[o + 11] - q[o + 5];
            float nx = d1y * d2z - d1z * d2y, ny = d1z * d2x - d1x * d2z, nz = d1x * d2y - d1y * d2x;
            float area = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (area < 1.0e-7f) continue;
            nx /= area;
            ny /= area;
            nz /= area;
            // the winding may run either way: the model's normal says which side is out
            if (nx * q[o + 12] + ny * q[o + 13] + nz * q[o + 14] < 0) {
                nx = -nx;
                ny = -ny;
                nz = -nz;
            }
            float vx = ortho ? ex : ex - cx, vy = ortho ? ey : ey - cy, vz = ortho ? ez : ez - cz;
            if (nx * vx + ny * vy + nz * vz <= 0) continue;
            front[f] = true;
            for (int k = 0; k < 4; k++) {
                int a = o + k * 3, b = o + ((k + 1) & 3) * 3;
                float ax = q[a], ay = q[a + 1], az = q[a + 2];
                float bx = q[b], by = q[b + 1], bz = q[b + 2];
                float dx = bx - ax, dy = by - ay, dz = bz - az;
                float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (len < 1.0e-4f) continue;
                float mx = (ax + bx) * 0.5f, my = (ay + by) * 0.5f, mz = (az + bz) * 0.5f;
                // outward along the face, away from its middle
                float ox = mx - cx, oy = my - cy, oz = mz - cz;
                float on = ox * nx + oy * ny + oz * nz;
                ox -= nx * on;
                oy -= ny * on;
                oz -= nz * on;
                float ol = (float) Math.sqrt(ox * ox + oy * oy + oz * oz);
                if (ol < 1.0e-6f) continue;
                ox /= ol;
                oy /= ol;
                oz /= ol;
                float wx = ortho ? ex : ex - mx, wy = ortho ? ey : ey - my, wz = ortho ? ez : ez - mz;
                // the neighbour across this edge faces (ox, oy, oz): on the silhouette when that turns away from the eye
                if (ox * wx + oy * wy + oz * wz >= 0) continue;
                pushEdge(ax, ay, az, bx, by, bz, ox, oy, oz, len);
            }
        }
        classify();
        return this;
    }

    /** Marks each edge outer or a seam, from a coarse mask of what the model's front faces cover. */
    private void classify() {
        if (edgeCount == 0) return;
        float[] q = quads;
        // the bounds of the front faces, laid flat
        float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
        for (int f = 0; f < quadCount; f++) {
            if (!front[f]) continue;
            int o = f * QUAD;
            for (int k = 0; k < 4; k++) {
                if (!project(q[o + k * 3], q[o + k * 3 + 1], q[o + k * 3 + 2])) {
                    allOuter();
                    return;
                }
                x0 = Math.min(x0, proj[0]);
                y0 = Math.min(y0, proj[1]);
                x1 = Math.max(x1, proj[0]);
                y1 = Math.max(y1, proj[1]);
            }
        }
        if (x1 <= x0 || y1 <= y0) {
            allOuter();
            return;
        }
        float padX = (x1 - x0) * 0.06f, padY = (y1 - y0) * 0.06f;
        minX = x0 - padX;
        minY = y0 - padY;
        cellX = (x1 - x0 + 2 * padX) / GRID;
        cellY = (y1 - y0 + 2 * padY) / GRID;
        Arrays.fill(mask, false);
        float[] gx = new float[4], gy = new float[4];
        for (int f = 0; f < quadCount; f++) {
            if (!front[f]) continue;
            int o = f * QUAD;
            float bx0 = Float.MAX_VALUE, by0 = Float.MAX_VALUE, bx1 = -Float.MAX_VALUE, by1 = -Float.MAX_VALUE;
            for (int k = 0; k < 4; k++) {
                project(q[o + k * 3], q[o + k * 3 + 1], q[o + k * 3 + 2]);
                gx[k] = (proj[0] - minX) / cellX;
                gy[k] = (proj[1] - minY) / cellY;
                bx0 = Math.min(bx0, gx[k]);
                by0 = Math.min(by0, gy[k]);
                bx1 = Math.max(bx1, gx[k]);
                by1 = Math.max(by1, gy[k]);
            }
            int i0 = Math.max(0, (int) Math.floor(bx0 - 0.5f)), i1 = Math.min(GRID - 1, (int) Math.ceil(bx1 - 0.5f));
            int j0 = Math.max(0, (int) Math.floor(by0 - 0.5f)), j1 = Math.min(GRID - 1, (int) Math.ceil(by1 - 0.5f));
            for (int j = j0; j <= j1; j++) {
                float py = j + 0.5f;
                for (int i = i0; i <= i1; i++) {
                    if (mask[j * GRID + i]) continue;
                    float px = i + 0.5f;
                    int pos = 0, neg = 0;
                    for (int k = 0; k < 4; k++) {
                        int n = (k + 1) & 3;
                        float cr = (gx[n] - gx[k]) * (py - gy[k]) - (gy[n] - gy[k]) * (px - gx[k]);
                        if (cr > 0) pos++;
                        else if (cr < 0) neg++;
                    }
                    if (pos == 0 || neg == 0) mask[j * GRID + i] = true;
                }
            }
        }
        for (int e = 0; e < edgeCount; e++) {
            int o = e * EDGE;
            boolean outer = false;
            for (int s = 0; s < 2 && !outer; s++) {
                float t = s == 0 ? 0.3f : 0.7f;
                float px = edges[o] + (edges[o + 3] - edges[o]) * t;
                float py = edges[o + 1] + (edges[o + 4] - edges[o + 1]) * t;
                float pz = edges[o + 2] + (edges[o + 5] - edges[o + 2]) * t;
                if (!project(px, py, pz)) {
                    outer = true;
                    break;
                }
                float sx = proj[0], sy = proj[1];
                project(px + edges[o + 6] * 0.01f, py + edges[o + 7] * 0.01f, pz + edges[o + 8] * 0.01f);
                float dx = (proj[0] - sx) / cellX, dy = (proj[1] - sy) / cellY;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                // two and a half cells out from the edge, across the mask
                float k = d > 1.0e-6f ? 2.5f / d : 0;
                int ci = (int) Math.floor((sx - minX) / cellX + dx * k), cj = (int) Math.floor((sy - minY) / cellY + dy * k);
                outer = ci < 0 || cj < 0 || ci >= GRID || cj >= GRID || !mask[cj * GRID + ci];
            }
            edges[o + 10] = outer ? 1 : 0;
            if (outer) outerLength += edges[o + 9];
        }
    }

    private void allOuter() {
        outerLength = 0;
        for (int e = 0; e < edgeCount; e++) {
            edges[e * EDGE + 10] = 1;
            outerLength += edges[e * EDGE + 9];
        }
    }

    /** Lays a point flat on the mask's plane ({@link #proj}); false if it is behind the eye. */
    private boolean project(float x, float y, float z) {
        if (ortho) {
            proj[0] = x * lx + y * ly + z * lz;
            proj[1] = x * ux + y * uy + z * uz;
            return true;
        }
        float d = x * fx + y * fy + z * fz;
        if (d < 0.05f) return false;
        proj[0] = (x * lx + y * ly + z * lz) / d;
        proj[1] = (x * ux + y * uy + z * uz) / d;
        return true;
    }

    private void pushEdge(float ax, float ay, float az, float bx, float by, float bz, float ox, float oy, float oz, float len) {
        if ((edgeCount + 1) * EDGE > edges.length) edges = Arrays.copyOf(edges, edges.length * 2);
        int e = edgeCount * EDGE;
        edges[e] = ax;
        edges[e + 1] = ay;
        edges[e + 2] = az;
        edges[e + 3] = bx;
        edges[e + 4] = by;
        edges[e + 5] = bz;
        edges[e + 6] = ox;
        edges[e + 7] = oy;
        edges[e + 8] = oz;
        edges[e + 9] = len;
        edges[e + 10] = 1;
        edgeCount++;
        length += len;
    }

    private void pushQuad(float[] corners) {
        if (quadCount >= MAX_QUADS) return;
        if ((quadCount + 1) * QUAD > quads.length) quads = Arrays.copyOf(quads, quads.length * 2);
        System.arraycopy(corners, 0, quads, quadCount * QUAD, QUAD);
        quadCount++;
    }

    /** Takes vertices and draws nothing. */
    private static final VertexConsumer NOTHING = new Tap0();

    /** Hands every vertex on (unless it records only) and keeps the corners of each quad drawn through it. */
    private final class Tap implements VertexConsumer {
        private final VertexConsumer out;
        private final float[] quad = new float[QUAD];
        private int corner;

        Tap(VertexConsumer out) {
            this.out = out;
        }

        private void corner(float x, float y, float z) {
            int i = corner * 3;
            quad[i] = x;
            quad[i + 1] = y;
            quad[i + 2] = z;
            if (++corner == 4) {
                corner = 0;
                pushQuad(quad);
            }
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            if (out != null) out.addVertex(x, y, z);
            corner(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            if (out != null) out.setColor(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            if (out != null) out.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            if (out != null) out.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            if (out != null) out.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            if (out != null) out.setNormal(x, y, z);
            // the normal of a quad's first corner arrives once that corner is in
            if (corner == 1) {
                quad[12] = x;
                quad[13] = y;
                quad[14] = z;
            }
            return this;
        }

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz) {
            if (out != null) out.addVertex(x, y, z, color, u, v, overlay, light, nx, ny, nz);
            if (corner == 0) {
                quad[12] = nx;
                quad[13] = ny;
                quad[14] = nz;
            }
            corner(x, y, z);
        }
    }

    /** Draws nothing at all (a render type the outline does not read, while it only records). */
    private static final class Tap0 implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }
    }
}
