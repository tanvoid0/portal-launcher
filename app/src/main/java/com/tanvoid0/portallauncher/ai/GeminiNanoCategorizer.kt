package com.tanvoid0.portallauncher.ai

import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.tanvoid0.portallauncher.data.AppCategory
import com.tanvoid0.portallauncher.data.LaunchableApp

/**
 * What the on-device model can do for us right now.
 *
 * Every value except [Ready] is a normal, expected state — an unsupported chipset,
 * an unlocked bootloader, or simply a user who never opted in. Nothing in the
 * launcher branches on this beyond the settings screen; categorisation degrades to
 * [com.tanvoid0.portallauncher.data.AppCategoryRules] and the launcher is unchanged.
 */
enum class AiStatus { Unavailable, Downloadable, Downloading, Ready }

/**
 * Categorises apps with Gemini Nano through ML Kit's Prompt API.
 *
 * The model lives in AICore, a system service, not in this process: we never hold
 * the weights, never grow the launcher's resident set, and never pay a cold-start
 * cost for it. That is the whole reason this path was chosen over bundling a
 * LiteRT model — the home process getting killed by lmkd leaves the user with no
 * home screen.
 *
 * Every entry point is failure-tolerant by construction. There is no error surface
 * to handle upstream: a device without AICore, a revoked download, an inference
 * quota rejection and a garbled response all produce "no answer", which the caller
 * already handles because it is the same thing as the user never enabling this.
 */
class GeminiNanoCategorizer {

    // Lazy so that a device where the client cannot even be constructed costs us
    // nothing until something actually asks.
    private val model by lazy { runCatching { Generation.getClient() }.getOrNull() }

    suspend fun status(): AiStatus {
        val model = model ?: return AiStatus.Unavailable
        return when (runCatching { model.checkStatus() }.getOrNull()) {
            FeatureStatus.AVAILABLE -> AiStatus.Ready
            FeatureStatus.DOWNLOADABLE -> AiStatus.Downloadable
            FeatureStatus.DOWNLOADING -> AiStatus.Downloading
            else -> AiStatus.Unavailable
        }
    }

    /**
     * Fetches the model. Only ever called from an explicit user opt-in — this is a
     * large download on the user's connection and must not happen behind their back.
     */
    suspend fun download(): AiStatus {
        val model = model ?: return AiStatus.Unavailable
        // The flow completes when the download does, succeeded or not; re-checking
        // the status afterwards is both shorter than matching on every DownloadStatus
        // subtype and the only answer that is actually authoritative.
        runCatching { model.download().collect { } }
        return status()
    }

    /**
     * Returns a category for as many of [apps] as the model managed to place.
     * Absent packages are not failures — the caller keeps whatever it had.
     */
    suspend fun classify(apps: List<LaunchableApp>): Map<String, AppCategory> {
        val model = model ?: return emptyMap()
        if (apps.isEmpty() || status() != AiStatus.Ready) return emptyMap()

        val batches = apps.distinctBy { it.packageName }.chunked(BATCH_SIZE)
        val result = mutableMapOf<String, AppCategory>()
        for (batch in batches) {
            val text = runCatching { model.generateContent(classificationPrompt(batch)) }
                .getOrNull()
                ?.candidates
                ?.firstOrNull()
                ?.text
                ?: continue
            result += parseClassification(text, batch.map { it.packageName })
        }
        return result
    }

    /**
     * Runs [prompt] through the model and returns its raw reply text, or null on any
     * failure -- no model, not ready, a quota rejection, a garbled response. Unlike
     * [classify], the caller supplies the whole prompt; used by
     * [com.tanvoid0.portallauncher.ai.agent.LauncherAgent] for open-ended tool-calling
     * turns rather than the fixed categorisation prompt above.
     */
    suspend fun generate(prompt: String): String? {
        val model = model ?: return null
        if (status() != AiStatus.Ready) return null
        return runCatching { model.generateContent(prompt) }.getOrNull()
            ?.candidates?.firstOrNull()?.text
    }
}

/**
 * 15 apps per request keeps us an order of magnitude inside the Prompt API's ~4000
 * input token budget while still amortising the per-call overhead.
 */
private const val BATCH_SIZE = 15

/**
 * The categories are the profile axis — Study / Social / Productivity / Gaming are
 * exactly the profiles the launcher ships — so classifying an app *is* deciding
 * which profiles should surface it.
 */
internal fun classificationPrompt(apps: List<LaunchableApp>): String = buildString {
    appendLine(
        "Classify each Android app into exactly one category: " +
            AppCategory.entries.joinToString(", ") { it.id } + "."
    )
    appendLine("Use \"other\" when none of the rest clearly fit.")
    appendLine("Reply with one line per app, formatted \"<number>. <category>\", and nothing else.")
    appendLine()
    apps.forEachIndexed { index, app ->
        appendLine("${index + 1}. ${app.label} (${app.packageName})")
    }
}

private val RESPONSE_LINE = Regex("""^\s*(\d+)\s*[.):\-]?\s*([A-Za-z]+)""")

/**
 * Tolerant on purpose: a small model will sometimes number oddly, add a preamble,
 * or invent a category. Anything that does not parse into a known category for a
 * known index is dropped, so a bad response degrades to a partial one rather than
 * writing nonsense into the cache.
 */
internal fun parseClassification(
    text: String,
    packageNames: List<String>
): Map<String, AppCategory> = text.lineSequence()
    .mapNotNull { line ->
        val match = RESPONSE_LINE.find(line) ?: return@mapNotNull null
        val packageName = match.groupValues[1].toIntOrNull()
            ?.let { packageNames.getOrNull(it - 1) }
            ?: return@mapNotNull null
        val category = AppCategory.entries
            .find { it.id == match.groupValues[2].lowercase() }
            ?: return@mapNotNull null
        packageName to category
    }
    .toMap()
