package com.linda.app.features.detection

import org.json.JSONObject
import java.io.File

/** Loads the shared test vectors and the shipped model. Gradle runs unit tests from the android/app folder. */
object TestData {
    data class Vector(
        val id: String, val sender: String, val text: String, val label: String,
        val normalized: String, val scoreMin: Double, val scoreMax: Double, val fingerprint: String,
        val campaign: String, val inContacts: Boolean?,
    )

    private val vectorsFile = File("../../shared/test-vectors.json")
    val modelJson: String = File("src/main/assets/model.json").readText()
    val scorer: ModelScorer by lazy { ModelScorer(modelJson) }
    val detector: LindaDetector by lazy { LindaDetector(scorer) }

    val vectors: List<Vector> by lazy {
        val arr = JSONObject(vectorsFile.readText()).getJSONArray("vectors")
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Vector(
                o.getString("id"), o.getString("sender"), o.getString("text"), o.getString("expected_label"),
                o.getString("expected_normalized"), o.getDouble("expected_score_min"), o.getDouble("expected_score_max"),
                o.getString("expected_fingerprint"), o.getString("campaign"),
                if (o.has("sender_in_contacts")) o.getBoolean("sender_in_contacts") else null,
            )
        }
    }
}
