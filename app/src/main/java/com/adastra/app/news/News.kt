package com.adastra.app.news

import android.text.Html
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** One article as it came from a feed. */
data class FeedItem(val title: String, val link: String, val source: String, val summary: String, val published: Long)

/** A ranked story: one or more articles about the same event. */
@Serializable
data class Story(
    val title: String,
    val link: String,
    val summary: String,
    val sources: List<String>,
    val published: Long,
    val tags: List<String>,
)

@Serializable
data class Briefing(
    val fetchedAt: Long,
    val stories: List<Story>,
    val sourcesOk: List<String> = emptyList(),
    val sourcesFailed: List<String> = emptyList(),
)

object NewsSources {
    data class Source(val name: String, val url: String)

    val all = listOf(
        Source("BleepingComputer", "https://www.bleepingcomputer.com/feed/"),
        Source("The Hacker News", "https://feeds.feedburner.com/TheHackersNews"),
        Source("SecurityWeek", "https://www.securityweek.com/feed/"),
        Source("Krebs on Security", "https://krebsonsecurity.com/feed/"),
        Source("Dark Reading", "https://www.darkreading.com/rss.xml"),
        Source("The Record", "https://therecord.media/feed"),
        Source("Help Net Security", "https://www.helpnetsecurity.com/feed/"),
    )
}

/** Downloads and parses RSS 2.0 and Atom feeds. Reads only public feeds; sends nothing about you. */
object FeedReader {
    private const val MAX_BYTES = 2_000_000

    fun read(source: NewsSources.Source): List<FeedItem> {
        val conn = (URL(source.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "AdAstra/1.0 (Android news reader)")
            setRequestProperty("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml")
        }
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            val bytes = conn.inputStream.use { it.readNBytesCompat(MAX_BYTES) }
            return parse(bytes, source.name)
        } finally {
            conn.disconnect()
        }
    }

    private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16_384)
        while (out.size() < max) {
            val n = read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    fun parse(bytes: ByteArray, sourceName: String): List<FeedItem> {
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(bytes.inputStream(), null)
        val items = mutableListOf<FeedItem>()
        var inItem = false
        var title = ""; var link = ""; var summary = ""; var date = ""
        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val name = p.name.lowercase()
                    if (name == "item" || name == "entry") {
                        inItem = true; title = ""; link = ""; summary = ""; date = ""
                    } else if (inItem) {
                        when (name) {
                            "title" -> title = p.nextTextSafe()
                            "link" -> {
                                val href = p.getAttributeValue(null, "href")
                                val rel = p.getAttributeValue(null, "rel")
                                if (href != null) { if (link.isEmpty() && (rel == null || rel == "alternate")) link = href }
                                else link = p.nextTextSafe().ifBlank { link }
                            }
                            "description", "summary" -> if (summary.isEmpty()) summary = p.nextTextSafe()
                            "content:encoded", "content" -> if (summary.isEmpty()) summary = p.nextTextSafe()
                            "pubdate", "published", "dc:date" -> date = p.nextTextSafe()
                            "updated" -> if (date.isEmpty()) date = p.nextTextSafe()
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    val name = p.name.lowercase()
                    if ((name == "item" || name == "entry") && inItem) {
                        inItem = false
                        val t = clean(title, 200)
                        if (t.isNotEmpty() && link.isNotBlank()) {
                            items += FeedItem(t, link.trim(), sourceName, clean(summary, 240), parseDate(date))
                        }
                    }
                }
            }
            event = p.next()
        }
        return items
    }

    private fun XmlPullParser.nextTextSafe(): String = try { nextText() } catch (e: Exception) { "" }

    private fun parseDate(s: String): Long {
        val t = s.trim()
        if (t.isEmpty()) return 0
        return runCatching { ZonedDateTime.parse(t, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
            .recoverCatching { OffsetDateTime.parse(t).toInstant().toEpochMilli() }
            .getOrDefault(0)
    }

    /** Strips HTML and feed boilerplate, then trims to a word boundary. */
    fun clean(raw: String, max: Int): String {
        var s = Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY).toString()
        s = s.replace(Regex("\\uFFFC"), " ")
            .replace(Regex("The post .+ appeared first on .+", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""\[(…|\.\.\.|&hellip;)]"""), "")
            .replace(Regex("(Continue|Read) reading.*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (s.length <= max) return s
        val cut = s.take(max)
        val sp = cut.lastIndexOf(' ')
        return (if (sp > max / 2) cut.take(sp) else cut).trimEnd(',', '.', ';', ':') + "…"
    }
}

/**
 * Picks the day's top stories without any AI: groups articles about the same event,
 * then ranks a group higher when several outlets cover it, when it's recent, and when it
 * mentions things that need action (actively exploited bugs, zero-days, breaches...).
 */
object NewsRanker {
    private val SIGNALS = listOf(
        "actively exploited" to 6, "exploited in the wild" to 6, "under active exploitation" to 6,
        "zero-day" to 5, "0-day" to 5, "zero day" to 5,
        "emergency" to 3, "ransomware" to 3, "data breach" to 3, "breach" to 2, "supply chain" to 3,
        "backdoor" to 3, "remote code execution" to 3, "rce" to 2, "critical" to 2, "kev" to 3,
        "nation-state" to 2, "apt" to 2, "botnet" to 2, "wiper" to 2, "patch" to 1, "vulnerability" to 1,
        "phishing" to 1, "malware" to 1,
    )
    private val TAGS = listOf(
        "actively exploited" to "Actively exploited", "exploited in the wild" to "Actively exploited",
        "under active exploitation" to "Actively exploited", "zero-day" to "Zero-day", "0-day" to "Zero-day",
        "zero day" to "Zero-day", "ransomware" to "Ransomware", "breach" to "Breach",
        "supply chain" to "Supply chain", "backdoor" to "Backdoor", "critical" to "Critical",
    )
    private val CVE = Regex("CVE-\\d{4}-\\d{4,7}", RegexOption.IGNORE_CASE)
    private val STOP = setOf(
        "with", "from", "that", "this", "into", "over", "after", "about", "their", "they", "have", "been",
        "will", "more", "than", "what", "when", "your", "says", "said", "new", "news", "week", "could",
        "attack", "attacks", "attackers", "hackers", "hacker", "security", "cyber", "cybersecurity",
        "flaw", "flaws", "vulnerability", "vulnerabilities", "users", "using", "update", "updates", "warns",
    )

    fun rank(items: List<FeedItem>, now: Long = System.currentTimeMillis(), count: Int = 5): List<Story> {
        val horizon = 36L * 3_600_000
        val recent = items.filter { it.published == 0L || now - it.published in 0..horizon }
            .distinctBy { it.link }
        if (recent.isEmpty()) return emptyList()

        // Group articles about the same event: shared CVE, or similar titles.
        val tokens = recent.map { words(it.title) }
        val cves = recent.map { CVE.findAll(it.title + " " + it.summary).map { m -> m.value.uppercase() }.toSet() }
        val group = IntArray(recent.size) { it }
        fun root(i: Int): Int { var x = i; while (group[x] != x) x = group[x]; return x }
        for (i in recent.indices) for (j in i + 1 until recent.size) {
            val sameCve = (cves[i] intersect cves[j]).isNotEmpty()
            val shared = (tokens[i] intersect tokens[j]).size
            val union = (tokens[i] union tokens[j]).size.coerceAtLeast(1)
            if (sameCve || (shared >= 3 && shared.toDouble() / union >= 0.25)) group[root(j)] = root(i)
        }

        return recent.indices.groupBy { root(it) }.values.map { idx ->
            val members = idx.map { recent[it] }
            val text = members.joinToString(" ") { (it.title + " " + it.summary).lowercase() }
            val outlets = members.map { it.source }.distinct()
            val signal = SIGNALS.filter { text.contains(it.first) }.sumOf { it.second }.coerceAtMost(12)
            val newest = members.maxOf { it.published }
            val ageH = if (newest == 0L) 24.0 else (now - newest) / 3_600_000.0
            val freshness = when { ageH < 6 -> 3.0; ageH < 12 -> 2.0; ageH < 24 -> 1.0; else -> 0.0 }
            val score = (outlets.size - 1) * 5.0 + signal + freshness
            val lead = members.maxBy { it.summary.length.coerceAtMost(240) + if (it.published == newest) 20 else 0 }
            val tags = (TAGS.filter { text.contains(it.first) }.map { it.second } +
                members.flatMap { m -> CVE.findAll(m.title).map { it.value.uppercase() }.toList() })
                .distinct().take(3)
            score to Story(lead.title, lead.link, lead.summary, outlets, newest, tags)
        }.sortedByDescending { it.first }.take(count).map { it.second }
    }

    private fun words(s: String): Set<String> =
        s.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length >= 4 && it !in STOP }.toSet()
}

/** Holds the latest briefing, saved to a small file so it shows instantly and offline. */
class NewsRepository(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val lock = Mutex()

    private val _briefing = MutableStateFlow(load())
    val briefing: StateFlow<Briefing?> = _briefing
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private fun load(): Briefing? = runCatching { json.decodeFromString<Briefing>(file.readText()) }.getOrNull()

    /** Fetches every source and re-ranks. Returns true if at least one source answered. */
    suspend fun refresh(): Boolean = lock.withLock {
        _loading.value = true
        try {
            val results = coroutineScope {
                NewsSources.all.map { src -> async(Dispatchers.IO) { src to runCatching { FeedReader.read(src) } } }.awaitAll()
            }
            val ok = results.filter { it.second.isSuccess }
            if (ok.isEmpty()) return@withLock false
            val items = ok.flatMap { it.second.getOrDefault(emptyList()) }
            val b = Briefing(
                fetchedAt = System.currentTimeMillis(),
                stories = NewsRanker.rank(items),
                sourcesOk = ok.map { it.first.name },
                sourcesFailed = results.filter { it.second.isFailure }.map { it.first.name },
            )
            _briefing.value = b
            withContext(Dispatchers.IO) { file.writeText(json.encodeToString(Briefing.serializer(), b)) }
            true
        } finally {
            _loading.value = false
        }
    }
}
