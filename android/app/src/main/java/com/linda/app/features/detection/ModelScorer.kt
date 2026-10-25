package com.linda.app.features.detection

import org.json.JSONObject
import java.util.Base64
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Scores a message from the exported model file (assets/model.json), with no ML library.
 * MUST match ml/src/features/scoring/scorer.py to within 0.001 (shared/test-vectors.json checks it).
 *
 * In plain terms: chop the normalised message into overlapping 2 to 5 letter pieces, put each piece in
 * one of 32,768 slots with the same FNV-1a hash the fingerprints use, weight each slot (rare pieces count
 * more), add up each slot's learned "scaminess", add the sender facts, and squash the total into a
 * number between 0 and 1.
 *
 * Lightweight (root CLAUDE.md 5.8, 7.1): the file stores one byte per slot for the idf (value = byte /
 * idf_scale) and one signed byte per slot for the weight (value = byte * coef_scale), as base64 text.
 * That is about 87 KB instead of the old 1 MB vocabulary, with the same results on real messages.
 */
class ModelScorer(modelJson: String) {
    private val root = JSONObject(modelJson)
    private val vectorizer = root.getJSONObject("vectorizer")
    private val buckets: Int = vectorizer.getInt("buckets")
    private val idf: DoubleArray
    private val coef: DoubleArray
    private val minN: Int
    private val maxN: Int
    private val intercept = root.getDouble("intercept")
    private val metaCoef: Map<String, Double>

    val version: String = root.getString("version")
    val warnThreshold: Double = root.getJSONObject("thresholds").getDouble("warn")
    val scamThreshold: Double = root.getJSONObject("thresholds").getDouble("scam")
    val verifiedSenders: Set<String>

    init {
        require(vectorizer.getString("hash") == "fnv1a_64") { "unknown hash in model file" }
        val idfScale = vectorizer.getDouble("idf_scale")
        val idfBytes = Base64.getDecoder().decode(vectorizer.getString("idf_uint8_b64"))
        idf = DoubleArray(idfBytes.size) { (idfBytes[it].toInt() and 0xff) / idfScale } // unsigned byte
        val coefScale = root.getDouble("coef_scale")
        val coefBytes = Base64.getDecoder().decode(root.getString("coef_int8_b64"))
        coef = DoubleArray(coefBytes.size) { coefBytes[it].toInt() * coefScale } // Kotlin bytes are already signed
        require(idf.size == buckets && coef.size == buckets) { "model file has the wrong number of slots" }
        val range = vectorizer.getJSONArray("ngram_range")
        minN = range.getInt(0)
        maxN = range.getInt(1)
        val meta = root.getJSONArray("metadata_features")
        metaCoef = (0 until meta.length()).associate { meta.getJSONObject(it).getString("name") to meta.getJSONObject(it).getDouble("coef") }
        val verified = root.getJSONArray("verified_senders")
        verifiedSenders = (0 until verified.length()).map { verified.getString(it).uppercase() }.toSet()
    }

    /** Same n-grams as scikit-learn's analyzer="char_wb": each word padded with one space each side. */
    internal fun charWbNgrams(text: String): List<String> {
        val grams = ArrayList<String>()
        for (word in text.split(' ').filter { it.isNotEmpty() }) {
            val w = " $word "
            for (n in minN..maxN) {
                var offset = 0
                grams.add(w.substring(offset, minOf(offset + n, w.length)))
                while (offset + n < w.length) {
                    offset++
                    grams.add(w.substring(offset, minOf(offset + n, w.length)))
                }
                if (offset == 0) break // the word is no longer than n: longer n-grams would repeat it
            }
        }
        return grams
    }

    /** Which slot a letter piece goes in: FNV-1a 64 of its UTF-8 bytes, read as unsigned, modulo the slot count. */
    internal fun bucketOf(gram: String): Int =
        java.lang.Long.remainderUnsigned(SimHash.fnv1a64(gram.toByteArray(Charsets.UTF_8)), buckets.toLong()).toInt()

    /** slot to its normalised weight (the part of the text that the model looks at). */
    internal fun tfidf(grams: List<String>): Map<Int, Double> {
        val counts = HashMap<Int, Int>()
        for (g in grams) bucketOf(g).let { counts[it] = (counts[it] ?: 0) + 1 }
        val weights = HashMap<Int, Double>()
        for ((b, c) in counts) weights[b] = (1.0 + ln(c.toDouble())) * idf[b]
        val norm = sqrt(weights.values.sumOf { it * it })
        if (norm <= 0.0) return emptyMap()
        return weights.mapValues { it.value / norm }
    }

    /**
     * The score, plus the parts that explain it. The sorted list of n-grams is only built if someone asks for it
     * (only warnings need it), because sorting for every harmless message would waste time when scanning a whole
     * inbox on a cheap phone. Each n-gram of the message gets the contribution of the slot it fell in, so the
     * explanation still names the message's own letters, not slot numbers.
     */
    class Detail(
        val probability: Double,
        private val grams: List<String>,
        private val slotContribution: Map<Int, Double>,
        private val slotOf: (String) -> Int,
        val meta: Map<String, Double>,
        /** The message as the model sees it: slot to normalised weight. Used by the personal layer (features/learning). */
        val slots: Map<Int, Double> = emptyMap(),
        /** The total before it is squashed into 0..1 (probability = 1 / (1 + e^-logit)). */
        val logit: Double = 0.0,
    ) {
        val ngramContributions: List<Pair<String, Double>> by lazy {
            grams.distinct().map { it to (slotContribution[slotOf(it)] ?: 0.0) }.sortedByDescending { it.second }
        }
    }

    fun explain(text: String, sender: String?): Detail {
        val grams = charWbNgrams(Normalizer.normalize(text))
        val weights = tfidf(grams)
        val meta = MetadataFeatures.compute(text, sender, verifiedSenders)
        val contributions = weights.mapValues { (b, w) -> coef[b] * w }
        var z = intercept + contributions.values.sum()
        for ((name, value) in meta) z += metaCoef.getValue(name) * value
        val p = if (z >= 0) 1.0 / (1.0 + exp(-z)) else exp(z) / (1.0 + exp(z))
        return Detail(p, grams, contributions, ::bucketOf, meta, weights, z)
    }

    fun score(text: String, sender: String?): Double = explain(text, sender).probability
}
