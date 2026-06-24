package com.oblivionburn.nlp.engine.neural;

import java.util.Random;

/**
 * A single Transformer block with:
 * - Multi‑head self‑attention (4 heads)
 * - Feed‑forward network (dim → 4*dim → dim)
 * - Residual connections + layer normalization
 */
public class TransformerBlock {
    private final int dim;
    private final int heads;

    // Attention weights
    private final Tensor Wq, Wk, Wv, Wo;
    // Feed‑forward weights
    private final Tensor W1, b1, W2, b2;

    public TransformerBlock(int dim, int heads, Random rng) {
        this.dim = dim;
        this.heads = heads;
        int headDim = dim / heads;

        Wq = Tensor.randn(dim, dim, rng);
        Wk = Tensor.randn(dim, dim, rng);
        Wv = Tensor.randn(dim, dim, rng);
        Wo = Tensor.randn(dim, dim, rng);

        W1 = Tensor.randn(dim, dim * 4, rng);
        b1 = Tensor.ones(1, dim * 4);  // bias
        W2 = Tensor.randn(dim * 4, dim, rng);
        b2 = Tensor.ones(1, dim);
    }

    /**
     * Forward pass.
     * @param x  input  [seqLen, dim]
     * @return    output [seqLen, dim]
     */
    public Tensor forward(Tensor x) {
        int seqLen = x.rows;

        // ---- Self‑attention ----
        Tensor Q = x.matmul(Wq);   // [seqLen, dim]
        Tensor K = x.matmul(Wk);
        Tensor V = x.matmul(Wv);

        // Split into heads and compute scaled dot‑product attention
        int headDim = dim / heads;
        Tensor attnOut = Tensor.matrix(seqLen, dim);

        for (int h = 0; h < heads; h++) {
            // Extract head h
            Tensor Qh = extractHead(Q, h, headDim);
            Tensor Kh = extractHead(K, h, headDim);
            Tensor Vh = extractHead(V, h, headDim);

            // Scores = Qh * Kh^T / sqrt(headDim)
            Tensor scores = Qh.matmul(Kh.transpose()).mul(1.0f / (float) Math.sqrt(headDim));
            Tensor attnWeights = scores.softmax();         // [seqLen, seqLen]
            Tensor headOut = attnWeights.matmul(Vh);       // [seqLen, headDim]

            // Write back to output columns
            for (int i = 0; i < seqLen; i++) {
                for (int j = 0; j < headDim; j++) {
                    attnOut.set(i, h * headDim + j, headOut.get(i, j));
                }
            }
        }

        // Project attention output
        Tensor attnProj = attnOut.matmul(Wo);   // [seqLen, dim]

        // Residual + layerNorm
        Tensor x1 = x.add(attnProj);
        Tensor x1Norm = x1.layerNorm(1e-5f);

        // ---- Feed‑forward ----
        Tensor ff1 = x1Norm.matmul(W1).add(b1).relu();   // [seqLen, 4*dim]
        Tensor ff2 = ff1.matmul(W2).add(b2);             // [seqLen, dim]

        // Residual + layerNorm
        Tensor x2 = x1Norm.add(ff2);
        return x2.layerNorm(1e-5f);
    }

    private Tensor extractHead(Tensor t, int h, int headDim) {
        int seqLen = t.rows;
        Tensor head = Tensor.matrix(seqLen, headDim);
        int offset = h * headDim;
        for (int i = 0; i < seqLen; i++) {
            for (int j = 0; j < headDim; j++) {
                head.set(i, j, t.get(i, offset + j));
            }
        }
        return head;
    }

    /** All learnable parameters (for optimizer). */
    public Tensor[] parameters() {
        return new Tensor[]{Wq, Wk, Wv, Wo, W1, b1, W2, b2};
    }
}
