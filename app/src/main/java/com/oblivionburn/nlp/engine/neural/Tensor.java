package com.oblivionburn.nlp.engine.neural;

import java.util.Random;

public class Tensor {

    public final float[] data;
    public final int rows, cols;
    public final boolean requiresGrad;

    public Tensor grad;

    private Tensor(float[] data, int rows, int cols, boolean requiresGrad) {
        this.data = data;
        this.rows = rows;
        this.cols = cols;
        this.requiresGrad = requiresGrad;
    }

    // ---- Factory methods ----
    public static Tensor vector(int size) {
        return new Tensor(new float[size], size, 1, false);
    }

    public static Tensor vector(float[] values) {
        return new Tensor(values.clone(), values.length, 1, true);
    }

    public static Tensor matrix(int rows, int cols) {
        return new Tensor(new float[rows * cols], rows, cols, false);
    }

    public static Tensor randn(int rows, int cols, Random rng) {
        float[] d = new float[rows * cols];
        for (int i = 0; i < d.length; i++) d[i] = (float) rng.nextGaussian() * 0.02f;
        return new Tensor(d, rows, cols, true);
    }

    public static Tensor zeros(int rows, int cols) {
        return new Tensor(new float[rows * cols], rows, cols, false);
    }

    public static Tensor ones(int rows, int cols) {
        float[] d = new float[rows * cols];
        java.util.Arrays.fill(d, 1.0f);
        return new Tensor(d, rows, cols, false);
    }

    // ---- Accessors ----
    public float get(int r, int c) { return data[r * cols + c]; }
    public void set(int r, int c, float v) { data[r * cols + c] = v; }
    public float get(int i) { return data[i]; }
    public void set(int i, float v) { data[i] = v; }

    // ---- In‑place operations ----
    public Tensor add(Tensor other) {
        if (this.rows != other.rows || this.cols != other.cols) {
            return this.copy();
        }
        Tensor out = new Tensor(new float[data.length], rows, cols, true);
        for (int i = 0; i < data.length; i++) out.data[i] = this.data[i] + other.data[i];
        out.backwardFn = () -> {
            if (this.requiresGrad) {
                if (this.grad == null) this.grad = zeros(rows, cols);
                for (int i = 0; i < data.length; i++) this.grad.data[i] += out.grad.data[i];
            }
            if (other.requiresGrad) {
                if (other.grad == null) other.grad = zeros(rows, cols);
                for (int i = 0; i < data.length; i++) other.grad.data[i] += out.grad.data[i];
            }
        };
        return out;
    }

    public Tensor matmul(Tensor other) {
        if (this.cols != other.rows) {
            return Tensor.zeros(this.rows, other.cols);
        }
        int m = this.rows, n = other.cols, k = this.cols;
        Tensor out = new Tensor(new float[m * n], m, n, true);
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                float sum = 0;
                for (int p = 0; p < k; p++) sum += this.get(i, p) * other.get(p, j);
                out.set(i, j, sum);
            }
        }
        out.backwardFn = () -> {
            if (this.requiresGrad) {
                if (this.grad == null) this.grad = zeros(m, k);
                for (int i = 0; i < m; i++)
                    for (int p = 0; p < k; p++)
                        for (int j = 0; j < n; j++)
                            this.grad.data[i * k + p] += out.grad.data[i * n + j] * other.data[p * n + j];
            }
            if (other.requiresGrad) {
                if (other.grad == null) other.grad = zeros(k, n);
                for (int p = 0; p < k; p++)
                    for (int j = 0; j < n; j++)
                        for (int i = 0; i < m; i++)
                            other.grad.data[p * n + j] += this.data[i * k + p] * out.grad.data[i * n + j];
            }
        };
        return out;
    }

    public Tensor mul(float scalar) {
        Tensor out = new Tensor(new float[data.length], rows, cols, true);
        for (int i = 0; i < data.length; i++) out.data[i] = this.data[i] * scalar;
        out.backwardFn = () -> {
            if (this.requiresGrad) {
                if (this.grad == null) this.grad = zeros(rows, cols);
                for (int i = 0; i < data.length; i++) this.grad.data[i] += out.grad.data[i] * scalar;
            }
        };
        return out;
    }

    public Tensor relu() {
        Tensor out = new Tensor(new float[data.length], rows, cols, true);
        for (int i = 0; i < data.length; i++) out.data[i] = Math.max(0, this.data[i]);
        out.backwardFn = () -> {
            if (this.requiresGrad) {
                if (this.grad == null) this.grad = zeros(rows, cols);
                for (int i = 0; i < data.length; i++)
                    if (this.data[i] > 0) this.grad.data[i] += out.grad.data[i];
            }
        };
        return out;
    }

    public Tensor softmax() {
        Tensor out = new Tensor(new float[data.length], rows, cols, true);
        for (int r = 0; r < rows; r++) {
            float max = Float.NEGATIVE_INFINITY;
            for (int c = 0; c < cols; c++) max = Math.max(max, get(r, c));
            float sum = 0;
            for (int c = 0; c < cols; c++) {
                float v = (float) Math.exp(get(r, c) - max);
                out.set(r, c, v);
                sum += v;
            }
            for (int c = 0; c < cols; c++) out.set(r, c, out.get(r, c) / sum);
        }
        out.backwardFn = () -> {
            if (this.requiresGrad) {
                if (this.grad == null) this.grad = zeros(rows, cols);
                for (int r = 0; r < rows; r++) {
                    for (int i = 0; i < cols; i++) {
                        float grad_i = 0;
                        for (int j = 0; j < cols; j++) {
                            float delta = (i == j) ? 1 : 0;
                            grad_i += out.grad.data[r * cols + j] * out.data[r * cols + i] * (delta - out.data[r * cols + j]);
                        }
                        this.grad.data[r * cols + i] += grad_i;
                    }
                }
            }
        };
        return out;
    }

    public Tensor layerNorm(float eps) {
        Tensor out = new Tensor(new float[data.length], rows, cols, true);
        for (int r = 0; r < rows; r++) {
            float mean = 0, var = 0;
            for (int c = 0; c < cols; c++) mean += get(r, c);
            mean /= cols;
            for (int c = 0; c < cols; c++) {
                float diff = get(r, c) - mean;
                var += diff * diff;
            }
            var = var / cols + eps;
            float invStd = (float) (1.0 / Math.sqrt(var));
            for (int c = 0; c < cols; c++) out.set(r, c, (get(r, c) - mean) * invStd);
        }
        out.backwardFn = () -> {
            if (this.requiresGrad) {
                if (this.grad == null) this.grad = zeros(rows, cols);
                for (int r = 0; r < rows; r++) {
                    float mean = 0, var = 0;
                    for (int c = 0; c < cols; c++) mean += get(r, c);
                    mean /= cols;
                    for (int c = 0; c < cols; c++) {
                        float diff = get(r, c) - mean;
                        var += diff * diff;
                    }
                    var = var / cols + eps;
                    float invStd = (float) (1.0 / Math.sqrt(var));
                    for (int c = 0; c < cols; c++) {
                        this.grad.data[r * cols + c] += out.grad.data[r * cols + c] * invStd;
                    }
                }
            }
        };
        return out;
    }

    public Tensor crossEntropyLoss(Tensor targets) {
        if (this.rows != targets.rows || this.cols != targets.cols) {
            return Tensor.zeros(1, 1);
        }
        float[] lossData = new float[1];
        float sum = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                sum -= targets.get(r, c) * (float) Math.log(Math.max(this.get(r, c), 1e-7));
            }
        }
        lossData[0] = sum / rows;
        Tensor loss = new Tensor(lossData, 1, 1, true);
        loss.backwardFn = () -> {
            if (this.requiresGrad) {
                if (this.grad == null) this.grad = zeros(rows, cols);
                for (int r = 0; r < rows; r++) {
                    for (int c = 0; c < cols; c++) {
                        this.grad.data[r * cols + c] += (this.data[r * cols + c] - targets.get(r, c)) / rows;
                    }
                }
            }
        };
        return loss;
    }

    public Tensor transpose() {
        Tensor out = new Tensor(new float[data.length], cols, rows, true);
        for (int i = 0; i < rows; i++)
            for (int j = 0; j < cols; j++)
                out.set(j, i, get(i, j));
        out.backwardFn = () -> {
            if (this.requiresGrad) {
                if (this.grad == null) this.grad = zeros(rows, cols);
                for (int i = 0; i < rows; i++)
                    for (int j = 0; j < cols; j++)
                        this.grad.data[i * cols + j] += out.grad.data[j * rows + i];
            }
        };
        return out;
    }

    // Backward function (set by operations)
    private Runnable backwardFn;

    public void backward() {
        if (grad == null) grad = ones(rows, cols);
        backwardFn.run();
    }

    // ---- Utility ----
    public Tensor copy() {
        return new Tensor(data.clone(), rows, cols, requiresGrad);
    }

    public void zeroGrad() {
        if (grad != null) java.util.Arrays.fill(grad.data, 0);
    }

    public void addScaledGrad(Tensor param, float learningRate) {
        for (int i = 0; i < data.length; i++) {
            data[i] -= learningRate * param.grad.data[i];
        }
    }
}
