package com.oblivionburn.nlp.engine.neural;

import java.util.Random;

/**
 * Learnable word embedding table.
 * Maps token IDs → dense vectors of size `dim`.
 */
public class Embedding {
    public final Tensor weight;   // shape: [vocabSize, dim]

    public Embedding(int vocabSize, int dim, Random rng) {
        this.weight = Tensor.randn(vocabSize, dim, rng);
    }

    /** Returns the embedding matrix for a sequence of token IDs (shape: [seqLen, dim]). */
    public Tensor forward(int[] tokenIds) {
        int seqLen = tokenIds.length;
        Tensor out = Tensor.matrix(seqLen, weight.cols);
        for (int i = 0; i < seqLen; i++) {
            int id = Math.min(tokenIds[i], weight.rows - 1);
            for (int j = 0; j < weight.cols; j++) {
                out.set(i, j, weight.get(id, j));
            }
        }
        // This operation conceptually "selects" rows; gradients will flow back to those rows.
        // We'll set up gradient tracking manually in the backward pass of the calling model.
        return out;
    }
}
