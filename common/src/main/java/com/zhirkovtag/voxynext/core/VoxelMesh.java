package com.zhirkovtag.voxynext.core;

import java.util.Arrays;

/**
 * Compact mesh representation independent from the Minecraft renderer.
 * Four vertices per visible cell are emitted with a single index pattern.
 */
public final class VoxelMesh {
    public static final int VERTEX_STRIDE = 6;
    private float[] vertices;
    private int[] indices;
    private int vertexCount;
    private int indexCount;

    public VoxelMesh(int expectedCells) {
        int v = Math.max(16, expectedCells * 4);
        vertices = new float[v * VERTEX_STRIDE];
        indices = new int[Math.max(24, expectedCells * 6)];
    }

    public void quad(float x0, float y0, float z0, float x1, float y1, float z1,
                     float x2, float y2, float z2, float x3, float y3, float z3,
                     float r, float g, float b) {
        int base = vertexCount;
        vertex(x0,y0,z0,r,g,b); vertex(x1,y1,z1,r,g,b);
        vertex(x2,y2,z2,r,g,b); vertex(x3,y3,z3,r,g,b);
        index(base); index(base + 1); index(base + 2);
        index(base); index(base + 2); index(base + 3);
    }

    public float[] vertices() { return Arrays.copyOf(vertices, vertexCount * VERTEX_STRIDE); }
    public int[] indices() { return Arrays.copyOf(indices, indexCount); }
    public int vertexCount() { return vertexCount; }
    public int indexCount() { return indexCount; }

    private void vertex(float x,float y,float z,float r,float g,float b) {
        ensureVertices(vertexCount + 1);
        int p = vertexCount++ * VERTEX_STRIDE;
        vertices[p]=x; vertices[p+1]=y; vertices[p+2]=z;
        vertices[p+3]=r; vertices[p+4]=g; vertices[p+5]=b;
    }

    private void index(int i) {
        ensureIndices(indexCount + 1);
        indices[indexCount++] = i;
    }

    private void ensureVertices(int n) {
        if (n * VERTEX_STRIDE > vertices.length) vertices = Arrays.copyOf(vertices, Math.max(n * VERTEX_STRIDE, vertices.length * 2));
    }

    private void ensureIndices(int n) {
        if (n > indices.length) indices = Arrays.copyOf(indices, Math.max(n, indices.length * 2));
    }
}
