package com.oblivionburn.nlp.engine.neural;

import java.util.Random;

public class TransformerBlock {
    private final int dim;
    private final int heads;

    private final Tensor Wq, Wk, Wv, Wo;
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
        b1 = Tensor.ones(1, dim * 4);
        W2 = Tensor.randn(dim * 4, dim, rng);
        b2 = Tensor.ones(1, dim);
    }

    public Tensor forward(Tensor x) {
        int seqLen = x.rows;

        Tensor Q = x.matmul(Wq);
        Tensor K = x.matmul(Wk);
        Tensor V = x.matmul(Wv);

        int headDim = dim / heads;
        Tensor attnOut = Tensor.matrix(seqLen, dim);

        for (int h = 0; h < heads; h++) {
            Tensor Qh = extractHead(Q, h, headDim);
            Tensor Kh = extractHead(K, h, headDim);
            Tensor Vh = extractHead(V, h, headDim);

            Tensor scores = Qh.matmul(Kh.transpose()).mul(1.0f / (float) Math.sqrt(headDim));
            Tensor attnWeights = scores.softmax();
            Tensor headOut = attnWeights.matmul(Vh);

            for (int i = 0; i < seqLen; i++) {
                for (int j = 0; j < headDim; j++) {
                    attnOut.set(i, h * headDim + j, headOut.get(i, j));
                }
            }
        }

        Tensor attnProj = attnOut.matmul(Wo);
        // Residual connection – safe add (will just copy if shapes differ)
        Tensor x1 = x.add(attnProj);
        Tensor x1Norm = x1.layerNorm(1e-5f);

        Tensor ff1 = x1Norm.matmul(W1);
        // Bias add: broadcast if needed
        Tensor ff1a = ff1.rows == b1.rows ? ff1.add(b1) : ff1;
        Tensor ff1r = ff1a.relu();
        Tensor ff2 = ff1r.matmul(W2);
        Tensor ff2a = ff2.rows == b2.rows ? ff2.add(b2) : ff2;

        Tensor x2 = x1Norm.add(ff2a);
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

    public Tensor[] parameters() {
        return new Tensor[]{Wq, Wk, Wv, Wo, W1, b1, W2, b2};
    }
}
