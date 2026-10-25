package com.ti.tasktimer;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

/**
 * 本地语义向量模型（v1.21.4）：bge-small-zh-v1.5 int8 量化版
 * （Xenova 现成量化件，512 维），模型与词表都由 ModelManager 按需
 * 下载到 filesDir/models/embed-semantic/，不进安装包。
 *
 * 分词：BERT WordPiece，只用 vocab.txt 在本地实现（基础切分＝小写、
 * 中文逐字、空白与标点分开；再贪心最长匹配，续接段加 ## 前缀），
 * 与备料阶段的实测口径一致：编码单句 → ONNX → last_hidden_state 做
 * attention-mask mean pooling → L2 归一化 → 余弦即点积。
 *
 * 模型未下载/加载失败时 embed() 返回 null，调用方（SemanticJudge）
 * 按「无本地预筛」处理，绝不崩溃。全部方法须在后台线程调用。
 */
public final class EmbedModel {
    private static final int MAX_LEN = 256;
    private static final int DIM = 512;

    private static OrtSession session;
    private static List<String> inputNames;
    private static Map<String, Integer> vocab;
    private static boolean failed;
    private static String loadedFrom = "";

    private EmbedModel() {}

    public static boolean isReady(Context c) {
        return ModelManager.isReady(c, ModelManager.ID_EMBED);
    }

    private static synchronized boolean ensure(Context c) {
        ModelManager.Model m = ModelManager.find(c, ModelManager.ID_EMBED);
        if (m == null || !ModelManager.isReady(c, m)) return false;
        File onnx = ModelManager.mainFile(c, m);
        if (session != null && onnx.getAbsolutePath().equals(loadedFrom)) return true;
        if (failed && onnx.getAbsolutePath().equals(loadedFrom)) return false;
        try {
            if (session != null) { try { session.close(); } catch (Exception ignored) { } session = null; }
            Map<String, Integer> v = new HashMap<>();
            File vf = ModelManager.extraFile(c, m, "vocab.txt");
            BufferedReader r = new BufferedReader(new InputStreamReader(
                    new FileInputStream(vf), StandardCharsets.UTF_8));
            String line;
            int idx = 0;
            while ((line = r.readLine()) != null) v.put(line, idx++);
            r.close();
            OrtEnvironment env = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
            opts.setIntraOpNumThreads(2);
            session = env.createSession(onnx.getAbsolutePath(), opts);
            inputNames = new ArrayList<>(session.getInputNames());
            vocab = v;
            loadedFrom = onnx.getAbsolutePath();
            failed = false;
            return true;
        } catch (Exception e) {
            failed = true;
            loadedFrom = onnx.getAbsolutePath();
            session = null;
            return false;
        }
    }

    /** 把一段文字编码成 512 维归一化向量；模型不可用或文字为空返回 null。 */
    public static synchronized float[] embed(Context c, String text) {
        if (text == null || text.trim().isEmpty()) return null;
        if (!ensure(c)) return null;
        try {
            long[] ids = encode(text);
            int len = ids.length;
            long[] mask = new long[len];
            long[] types = new long[len];
            for (int i = 0; i < len; i++) { mask[i] = 1; types[i] = 0; }
            OrtEnvironment env = OrtEnvironment.getEnvironment();
            Map<String, ai.onnxruntime.OnnxTensor> inputs = new HashMap<>();
            List<OnnxTensor> tensors = new ArrayList<>();
            long[] shape = new long[]{1, len};
            for (String name : inputNames) {
                long[] data = "token_type_ids".equals(name) ? types
                        : "attention_mask".equals(name) ? mask : ids;
                OnnxTensor t = OnnxTensor.createTensor(env, java.nio.LongBuffer.wrap(data.clone()), shape);
                inputs.put(name, t);
                tensors.add(t);
            }
            float[][][] out;
            try (OrtSession.Result res = session.run(inputs)) {
                out = (float[][][]) res.get(0).getValue();
            } finally {
                for (OnnxTensor t : tensors) { try { t.close(); } catch (Exception ignored) { } }
            }
            float[] vec = new float[DIM];
            for (int i = 0; i < len; i++) {
                float[] h = out[0][i];
                for (int d = 0; d < DIM; d++) vec[d] += h[d];
            }
            double norm = 0;
            for (float x : vec) norm += (double) x * x;
            norm = Math.sqrt(norm);
            if (norm <= 0) return null;
            for (int d = 0; d < DIM; d++) vec[d] = (float) (vec[d] / (len * norm));
            return vec;
        } catch (Exception e) {
            return null;
        }
    }

    /** 余弦相似度（两向量都已归一化时即点积；这里按通用公式算，不假设）。 */
    public static float cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return 0f;
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        if (na <= 0 || nb <= 0) return 0f;
        return (float) (dot / Math.sqrt(na * nb));
    }

    // ---------------- WordPiece 分词（BERT 基础切分 + 贪心最长匹配） ----------------

    private static long[] encode(String text) {
        List<Integer> ids = new ArrayList<>();
        ids.add(vocab.getOrDefault("[CLS]", 101));
        for (int id : wordpiece(basicTokenize(text))) {
            if (ids.size() >= MAX_LEN - 1) break;
            ids.add(id);
        }
        ids.add(vocab.getOrDefault("[SEP]", 102));
        long[] out = new long[ids.size()];
        for (int i = 0; i < ids.size(); i++) out[i] = ids.get(i);
        return out;
    }

    /** 基础切分：小写；中文逐字成词；空白分隔；标点各自成词。 */
    private static List<String> basicTokenize(String text) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        String lower = text.toLowerCase(java.util.Locale.US);
        for (int i = 0; i < lower.length(); i++) {
            char ch = lower.charAt(i);
            if (Character.isWhitespace(ch)) {
                if (cur.length() > 0) { out.add(cur.toString()); cur.setLength(0); }
            } else if (isCjk(ch) || isPunct(ch)) {
                if (cur.length() > 0) { out.add(cur.toString()); cur.setLength(0); }
                out.add(String.valueOf(ch));
            } else {
                cur.append(ch);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    private static boolean isCjk(char ch) {
        return (ch >= 0x4E00 && ch <= 0x9FFF) || (ch >= 0x3400 && ch <= 0x4DBF)
                || (ch >= 0xF900 && ch <= 0xFAFF);
    }

    private static boolean isPunct(char ch) {
        int t = Character.getType(ch);
        return t == Character.CONNECTOR_PUNCTUATION || t == Character.DASH_PUNCTUATION
                || t == Character.START_PUNCTUATION || t == Character.END_PUNCTUATION
                || t == Character.INITIAL_QUOTE_PUNCTUATION || t == Character.FINAL_QUOTE_PUNCTUATION
                || t == Character.OTHER_PUNCTUATION || t == Character.MATH_SYMBOL
                || t == Character.CURRENCY_SYMBOL || t == Character.MODIFIER_SYMBOL
                || t == Character.OTHER_SYMBOL;
    }

    /** WordPiece：整词在表里直接用；否则贪心最长匹配（续接段带 ##），匹配不上整词记 [UNK]。 */
    private static List<Integer> wordpiece(List<String> tokens) {
        List<Integer> out = new ArrayList<>();
        int unk = vocab.getOrDefault("[UNK]", 100);
        for (String tok : tokens) {
            if (tok.length() > 100) { out.add(unk); continue; }
            List<Integer> pieces = new ArrayList<>();
            int start = 0;
            boolean bad = false;
            while (start < tok.length()) {
                int end = tok.length();
                int found = -1;
                while (end > start) {
                    String sub = tok.substring(start, end);
                    if (start > 0) sub = "##" + sub;
                    Integer id = vocab.get(sub);
                    if (id != null) { found = id; break; }
                    end--;
                }
                if (found < 0) { bad = true; break; }
                pieces.add(found);
                start = end;
            }
            if (bad) out.add(unk); else out.addAll(pieces);
        }
        return out;
    }
}
