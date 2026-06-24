package com.oblivionburn.nlp.engine.neural;

import java.io.*;
import java.util.*;

public class AlienMind {
    private final Random rng = new Random();

    private final int dim = 128;
    private final int heads = 4;
    private final int blocks = 4;
    private final int maxSeqLen = 64;

    private final Embedding embedding;
    private final Tensor posEncoding;
    private final TransformerBlock[] transformerBlocks;
    private final Tensor outputW;
    private final Tensor outputB;

    private final List<Tensor> allParams = new ArrayList<>();
    private final List<Tensor> m = new ArrayList<>();
    private final List<Tensor> v = new ArrayList<>();
    private int t = 0;
    private final float lr = 0.001f;
    private final float beta1 = 0.9f, beta2 = 0.999f, eps = 1e-8f;

    private final Tokenizer tokenizer;

    public AlienMind(Tokenizer tokenizer) {
        this.tokenizer = tokenizer;
        int vocabSize = tokenizer.getVocabSize();

        embedding = new Embedding(vocabSize, dim, rng);

        posEncoding = Tensor.matrix(maxSeqLen, dim);
        for (int pos = 0; pos < maxSeqLen; pos++) {
            for (int i = 0; i < dim; i++) {
                float angle = (float) (pos / Math.pow(10000, (2.0 * i) / dim));
                float val = (i % 2 == 0) ? (float) Math.sin(angle) : (float) Math.cos(angle);
                posEncoding.set(pos, i, val);
            }
        }

        transformerBlocks = new TransformerBlock[blocks];
        for (int i = 0; i < blocks; i++) {
            transformerBlocks[i] = new TransformerBlock(dim, heads, rng);
        }

        outputW = Tensor.randn(dim, vocabSize, rng);
        outputB = Tensor.ones(1, vocabSize);

        allParams.add(embedding.weight);
        for (TransformerBlock block : transformerBlocks) {
            for (Tensor p : block.parameters()) allParams.add(p);
        }
        allParams.add(outputW);
        allParams.add(outputB);

        for (Tensor p : allParams) {
            m.add(Tensor.zeros(p.rows, p.cols));
            v.add(Tensor.zeros(p.rows, p.cols));
        }
    }

    private Tensor forward(int[] tokenIds) {
        int seqLen = Math.min(tokenIds.length, maxSeqLen);
        if (seqLen == 0) seqLen = 1;

        Tensor x = embedding.forward(tokenIds);
        for (int i = 0; i < seqLen; i++) {
            for (int j = 0; j < dim; j++) {
                x.set(i, j, x.get(i, j) + posEncoding.get(i, j));
            }
        }

        for (TransformerBlock block : transformerBlocks) {
            x = block.forward(x);
        }

        return x.matmul(outputW).add(outputB);
    }

    public float trainOnSentence(String sentence) {
        if (sentence == null || sentence.trim().isEmpty()) return 0f;

        int[] ids = tokenizer.encode(sentence, true);
        if (ids.length < 2) return 0f;

        int[] inputIds = new int[ids.length - 1];
        int[] targetIds = new int[ids.length - 1];
        System.arraycopy(ids, 0, inputIds, 0, ids.length - 1);
        System.arraycopy(ids, 1, targetIds, 0, ids.length - 1);

        Tensor logits = forward(inputIds);

        int vocabSize = tokenizer.getVocabSize();
        Tensor target = Tensor.zeros(logits.rows, vocabSize);
        for (int i = 0; i < targetIds.length; i++) {
            int tid = Math.min(targetIds[i], vocabSize - 1);
            target.set(i, tid, 1.0f);
        }

        Tensor probs = logits.softmax();
        Tensor loss = probs.crossEntropyLoss(target);
        float lossVal = loss.get(0);

        loss.backward();

        t++;
        float lr_t = lr * (float) (Math.sqrt(1 - Math.pow(beta2, t)) / (1 - Math.pow(beta1, t)));
        for (int i = 0; i < allParams.size(); i++) {
            Tensor param = allParams.get(i);
            if (param.grad == null) continue;
            Tensor mt = m.get(i);
            Tensor vt = v.get(i);
            for (int j = 0; j < param.data.length; j++) {
                float g = param.grad.data[j];
                mt.data[j] = beta1 * mt.data[j] + (1 - beta1) * g;
                vt.data[j] = beta2 * vt.data[j] + (1 - beta2) * g * g;
                param.data[j] -= lr_t * mt.data[j] / ((float) Math.sqrt(vt.data[j]) + eps);
            }
            param.zeroGrad();
        }

        return lossVal;
    }

    public void reinforce(String sentence, int times) {
        for (int i = 0; i < times; i++) trainOnSentence(sentence);
    }

    public void penalize(String sentence) {
        if (sentence == null || sentence.trim().isEmpty()) return;

        int[] ids = tokenizer.encode(sentence, true);
        if (ids.length < 2) return;

        int[] inputIds = new int[ids.length - 1];
        int[] targetIds = new int[ids.length - 1];
        System.arraycopy(ids, 0, inputIds, 0, ids.length - 1);
        System.arraycopy(ids, 1, targetIds, 0, ids.length - 1);

        Tensor logits = forward(inputIds);

        int vocabSize = tokenizer.getVocabSize();
        Tensor target = Tensor.zeros(logits.rows, vocabSize);
        for (int i = 0; i < targetIds.length; i++) {
            int tid = Math.min(targetIds[i], vocabSize - 1);
            target.set(i, tid, 1.0f);
        }

        Tensor probs = logits.softmax();
        Tensor loss = probs.crossEntropyLoss(target);
        loss.backward();

        t++;
        float lr_t = lr * (float) (Math.sqrt(1 - Math.pow(beta2, t)) / (1 - Math.pow(beta1, t)));
        for (int i = 0; i < allParams.size(); i++) {
            Tensor param = allParams.get(i);
            if (param.grad == null) continue;
            Tensor mt = m.get(i);
            Tensor vt = v.get(i);
            for (int j = 0; j < param.data.length; j++) {
                float g = param.grad.data[j];
                mt.data[j] = beta1 * mt.data[j] + (1 - beta1) * g;
                vt.data[j] = beta2 * vt.data[j] + (1 - beta2) * g * g;
                param.data[j] += lr_t * mt.data[j] / ((float) Math.sqrt(vt.data[j]) + eps);
            }
            param.zeroGrad();
        }
    }

    public String generate(String context, int maxLen) {
        if (context == null) context = "";
        String trimmed = context.trim();

        int[] ids;
        if (trimmed.isEmpty()) {
            ids = new int[]{Tokenizer.START_ID};
        } else {
            ids = tokenizer.encode(trimmed, false);
            if (ids.length == 0) ids = new int[]{Tokenizer.START_ID};
        }

        if (ids.length > maxSeqLen) {
            int[] trunc = new int[maxSeqLen];
            System.arraycopy(ids, ids.length - maxSeqLen, trunc, 0, maxSeqLen);
            ids = trunc;
        }

        List<Integer> genIds = new ArrayList<>();
        for (int id : ids) genIds.add(id);

        for (int step = 0; step < maxLen; step++) {
            int[] input = new int[genIds.size()];
            for (int i = 0; i < genIds.size(); i++) input[i] = genIds.get(i);

            Tensor logits = forward(input);
            int lastIdx = logits.rows - 1;
            float[] probs = new float[logits.cols];
            float maxLogit = Float.NEGATIVE_INFINITY;
            for (int c = 0; c < logits.cols; c++) {
                probs[c] = logits.get(lastIdx, c);
                if (probs[c] > maxLogit) maxLogit = probs[c];
            }
            float sum = 0;
            for (int c = 0; c < probs.length; c++) {
                probs[c] = (float) Math.exp(probs[c] - maxLogit);
                sum += probs[c];
            }
            for (int c = 0; c < probs.length; c++) probs[c] /= sum;

            float rand = rng.nextFloat();
            float cum = 0;
            int nextId = Tokenizer.END_ID;
            for (int c = 0; c < probs.length; c++) {
                cum += probs[c];
                if (rand <= cum) {
                    nextId = c;
                    break;
                }
            }

            if (nextId == Tokenizer.END_ID) break;
            genIds.add(nextId);
        }

        int[] outputIds = new int[genIds.size() - ids.length];
        for (int i = ids.length; i < genIds.size(); i++) outputIds[i - ids.length] = genIds.get(i);
        return tokenizer.decode(outputIds);
    }

    public void save(File file) throws IOException {
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
            for (Tensor p : allParams) {
                out.writeInt(p.rows);
                out.writeInt(p.cols);
                for (float v : p.data) out.writeFloat(v);
            }
        }
    }

    public void load(File file) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            for (Tensor p : allParams) {
                int rows = in.readInt();
                int cols = in.readInt();
                if (rows != p.rows || cols != p.cols) throw new IOException("Weight shape mismatch");
                for (int i = 0; i < p.data.length; i++) p.data[i] = in.readFloat();
            }
        }
    }

    public Tokenizer getTokenizer() {
        return tokenizer;
    }
}
