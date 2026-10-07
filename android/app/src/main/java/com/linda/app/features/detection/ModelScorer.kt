package com.linda.app.features.detection

import org.json.JSONObject
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Scores a message from the exported model file (assets/model.json), with no ML library.
 * MUST match ml/src/features/scoring/scorer.py to within 0.001 (shared/test-vectors.json checks it).
 *
 * In plain terms: chop the normalised message into overlapping 2 to 5 letter pieces, weight each
 * piece (rare pieces count more), add up each piece's learned "scaminess", add the sender facts,
 * and squash the total into a number between 0 and 1.
 */
class ModelScorer(modelJson: String) {
    private val root = JSONObject(modelJson)
    private val vectorizer = root.getJSONObject("vectorizer")
    private val vocabulary: Map<String, Int>
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
        val vocab = vectorizer.getJSONObject("vocabulary")
        val map = HashMap<String, Int>(vocab.length() * 2)
        for (key in vocab.keys()) map[key] = vocab.getInt(key)
        vocabulary = map
        idf = vectorizer.getJSONArray("idf").toDoubleArray()
        coef = root.getJSONArray("coef").toDoubleArray()
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

    /** n-gram to its normalised weight (the part of the text that the model looks at). */
    internal fun tfidf(normalized: String): Map<String, Double> {
        val counts = HashMap<String, Int>()
        for (g in charWbNgrams(normalized)) if (g in vocabulary) counts[g] = (counts[g] ?: 0) + 1
        val weights = HashMap<String, Double>()
        for ((g, c) in counts) weights[g] = (1.0 + ln(c.toDouble())) * idf[vocabulary.getValue(g)]
        val norm = sqrt(weights.values.sumOf { it * it })
        if (norm <= 0.0) return emptyMap()
        return weights.mapValues { it.value / norm }
    }

    /** The raw score before squashing, plus the parts, so the detector can explain it. */
    data class Detail(val probability: Double, val ngramContributions: List<Pair<String, Double>>, val meta: Map<String, Double>)

    fun explain(text: String, sender: String?): Detail {
        val normalized = Normalizer.normalize(text)
        val weights = tfidf(normalized)
        val meta = MetadataFeatures.compute(text, sender, verifiedSenders)
        val contributions = weights.map { (g, w) -> g to coef[vocabulary.getValue(g)] * w }
        var z = intercept + contributions.sumOf { it.second }
        for ((name, value) in meta) z += metaCoef.getValue(name) * value
        val p = if (z >= 0) 1.0 / (1.0 + exp(-z)) else exp(z) / (1.0 + exp(z))
        return Detail(p, contributions.sortedByDescending { it.second }, meta)
    }

    fun score(text: String, sender: String?): Double = explain(text, sender).probability

    private fun org.json.JSONArray.toDoubleArray() = DoubleArray(length()) { getDouble(it) }
}
