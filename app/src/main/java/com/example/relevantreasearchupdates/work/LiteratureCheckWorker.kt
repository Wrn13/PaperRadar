package com.example.relevantreasearchupdates.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.relevantreasearchupdates.data.AppDatabase
import com.example.relevantreasearchupdates.data.Paper
import com.example.relevantreasearchupdates.data.PaperRetention
import com.example.relevantreasearchupdates.data.PaperSource
import com.example.relevantreasearchupdates.data.SettingsRepository
import com.example.relevantreasearchupdates.data.Watch
import com.example.relevantreasearchupdates.data.WatchType
import com.example.relevantreasearchupdates.network.ArxivClient
import com.example.relevantreasearchupdates.network.CrossrefClient
import com.example.relevantreasearchupdates.notifications.NotificationHelper
import com.example.relevantreasearchupdates.util.AuthorMatcher
import com.example.relevantreasearchupdates.util.KeywordMatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

/**
 * Periodically searches arXiv and Crossref for each configured author/keyword watch, stores any
 * papers not seen before, and raises a single notification summarizing what's new.
 */
class LiteratureCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val db = AppDatabase.getInstance(applicationContext)
    private val arxivClient = ArxivClient()
    private val crossrefClient = CrossrefClient()

    /** See [PaperRetention.insertFloor]; papers published before this are not stored. */
    private var insertFloor: String? = null

    /** Inserts [paper] unless it's already stored or older than the retention floor. */
    private suspend fun store(paper: Paper, inserted: MutableList<Paper>) {
        val floor = insertFloor
        if (floor != null && paper.publishedDate.isNotEmpty() && paper.publishedDate < floor) return
        val rowId = db.paperDao().insertIgnoring(paper)
        if (rowId != -1L) inserted.add(paper.copy(id = rowId))
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val watches = db.watchDao().getAllOnce()
        if (watches.isEmpty()) return@withContext Result.success()

        val newlyInserted = Collections.synchronizedList(mutableListOf<Paper>())
        insertFloor = PaperRetention.insertFloor(db)
        val arxivBatches = buildArxivBatches(watches)
        val totalSteps = watches.size + arxivBatches.size
        val completedSteps = AtomicInteger(0)
        val progressMutex = Mutex()
        suspend fun stepDone() = progressMutex.withLock {
            setProgress(workDataOf(KEY_CURRENT to completedSteps.incrementAndGet(), KEY_TOTAL to totalSteps))
        }

        var outcomes: List<Boolean> = emptyList()

        try {
            setProgress(workDataOf(KEY_CURRENT to 0, KEY_TOTAL to totalSteps))

            // arXiv and Crossref run as two independent lanes. Crossref is fast and has no
            // spacing requirement, so its results land within seconds; arXiv is slow and
            // rate-limited, so it no longer holds up everything else.
            outcomes = coroutineScope {
                val crossrefLane = async {
                    val limiter = Semaphore(CROSSREF_CONCURRENCY)
                    watches.map { watch ->
                        async {
                            limiter.withPermit {
                                runSource { checkCrossref(watch, newlyInserted) }.also { stepDone() }
                            }
                        }
                    }.awaitAll()
                }
                val arxivLane = async { runArxivLane(arxivBatches, newlyInserted) { stepDone() } }
                crossrefLane.await() + arxivLane.await()
            }

            try {
                PaperRetention.enforce(applicationContext, db)
            } catch (e: Exception) {
                Log.w(TAG, "Retention cleanup failed", e)
            }
        } finally {
            // Papers are written to the database as soon as they're found, so they'll never
            // count as "new" again on a later run. Notify for them even if the system stopped
            // this worker partway through (constraints lost, time limit hit), otherwise they'd
            // silently land in the feed without ever producing an alert.
            withContext(NonCancellable) {
                notifyIfEnabled(newlyInserted.toList())
            }
        }

        // Every single request failed — almost certainly no usable connection, so a scheduled
        // run tries again soon rather than waiting a full interval. A manual check never
        // retries on its own: it would sit in backoff showing a spinner, and pressing refresh
        // again would do nothing until the backoff expired.
        val allFailed = outcomes.isNotEmpty() && outcomes.none { it }
        val isManual = inputData.getBoolean(KEY_MANUAL, false)
        if (allFailed && !isManual) Result.retry() else Result.success()
    }

    /**
     * Queries arXiv one batch at a time, spaced as arXiv requests. If a batch still fails after
     * a pause and one retry, arXiv is either rate-limiting us or struggling, and hammering it
     * with the remaining batches only prolongs that — so the rest are skipped until next run.
     */
    private suspend fun runArxivLane(
        batches: List<List<Watch>>,
        inserted: MutableList<Paper>,
        onStep: suspend () -> Unit
    ): List<Boolean> {
        val outcomes = mutableListOf<Boolean>()
        var givenUp = false
        batches.forEachIndexed { index, batch ->
            if (givenUp) {
                outcomes += false
            } else {
                val ok = runSource(retryDelayMs = ARXIV_RETRY_DELAY_MS) { checkArxivBatch(batch, inserted) }
                outcomes += ok
                if (!ok) {
                    givenUp = true
                    Log.w(TAG, "arXiv unavailable; skipping ${batches.size - index - 1} remaining batch(es)")
                }
            }
            onStep()
            if (!givenUp && index != batches.lastIndex) delay(ARXIV_SPACING_MS)
        }
        return outcomes
    }

    /**
     * Each arXiv request can take tens of seconds, so author watches are OR-ed together into a
     * few combined queries, each sized to give every author in it as many results as a solo
     * query would. Keywords stay one per query: a broad topic can return far more papers than
     * any author, and sharing a result window would crowd the other watches out entirely.
     */
    private fun buildArxivBatches(watches: List<Watch>): List<List<Watch>> {
        val authors = watches.filter { it.type == WatchType.AUTHOR }.chunked(ARXIV_AUTHOR_BATCH)
        val keywords = watches.filter { it.type == WatchType.KEYWORD }.chunked(ARXIV_KEYWORD_BATCH)
        return authors + keywords
    }

    /** Runs one source check with a single retry; returns false if it still failed. */
    private suspend fun runSource(
        retryDelayMs: Long = RETRY_DELAY_MS,
        block: suspend () -> Unit
    ): Boolean {
        repeat(2) { attempt ->
            try {
                block()
                return true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Source check failed (attempt ${attempt + 1}): $e")
                if (attempt == 0) delay(retryDelayMs)
            }
        }
        return false
    }

    private suspend fun notifyIfEnabled(papers: List<Paper>) {
        if (papers.isEmpty()) return
        try {
            val settings = SettingsRepository(applicationContext).settings.first()
            if (settings.notificationsEnabled) {
                NotificationHelper.showNewPapers(applicationContext, papers)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to post new-paper notification", e)
        }
    }

    private suspend fun checkArxivBatch(batch: List<Watch>, inserted: MutableList<Paper>) {
        val clauses = batch.map { watch ->
            when (watch.type) {
                WatchType.AUTHOR -> arxivClient.authorClause(watch.value)
                WatchType.KEYWORD -> arxivClient.keywordClause(watch.value)
            }
        }
        val entries = arxivClient.searchAny(clauses, maxResults = ARXIV_RESULTS_PER_WATCH * batch.size)
        for (entry in entries) {
            // A combined query returns hits for any watch in the batch, so attribute each
            // result to the first watch it actually matches (and drop it if it matches none).
            val watch = batch.firstOrNull { watch ->
                when (watch.type) {
                    WatchType.AUTHOR -> AuthorMatcher.anyMatches(watch.value, entry.authors)
                    WatchType.KEYWORD -> KeywordMatcher.matches(watch.value, entry.title, entry.summary)
                }
            } ?: continue
            val strippedId = entry.arxivId.replace(Regex("v\\d+$"), "")
            val paper = Paper(
                externalId = "arxiv:$strippedId",
                title = entry.title,
                authors = entry.authors.joinToString(", "),
                summary = entry.summary,
                source = PaperSource.ARXIV,
                sourceUrl = entry.absUrl,
                pdfUrl = entry.pdfUrl,
                doi = null,
                journal = "arXiv",
                publishedDate = entry.published.take(10),
                matchedQuery = describeWatch(watch)
            )
            store(paper, inserted)
        }
    }

    private suspend fun checkCrossref(watch: Watch, inserted: MutableList<Paper>) {
        val matchedQuery = describeWatch(watch)
        val entries = when (watch.type) {
            WatchType.AUTHOR -> crossrefClient.searchByAuthor(watch.value, rows = 40)
            WatchType.KEYWORD -> crossrefClient.searchByKeyword(watch.value, rows = 40)
        }
        for (entry in entries) {
            if (watch.type == WatchType.AUTHOR && !AuthorMatcher.anyMatches(watch.value, entry.authors)) {
                continue
            }
            if (watch.type == WatchType.KEYWORD && !KeywordMatcher.matches(watch.value, entry.title)) {
                continue
            }
            val paper = Paper(
                externalId = "doi:${entry.doi.lowercase()}",
                title = entry.title,
                authors = entry.authors.joinToString(", "),
                summary = "",
                source = PaperSource.CROSSREF,
                sourceUrl = entry.url,
                pdfUrl = entry.openAccessPdfUrl,
                doi = entry.doi,
                journal = entry.journal,
                publishedDate = entry.publishedDate,
                matchedQuery = matchedQuery
            )
            store(paper, inserted)
        }
    }

    private fun describeWatch(watch: Watch): String {
        val label = if (watch.type == WatchType.AUTHOR) "Author" else "Keyword"
        return "$label: ${watch.value}"
    }

    companion object {
        const val UNIQUE_PERIODIC_NAME = "literature_check_periodic"
        const val UNIQUE_ONE_TIME_NAME = "literature_check_once"
        const val KEY_CURRENT = "current"
        const val KEY_TOTAL = "total"
        private const val TAG = "LiteratureCheckWorker"
        const val KEY_MANUAL = "manual"
        private const val ARXIV_SPACING_MS = 3000L
        private const val ARXIV_RETRY_DELAY_MS = 15_000L
        private const val RETRY_DELAY_MS = 5000L
        private const val ARXIV_AUTHOR_BATCH = 5
        private const val ARXIV_KEYWORD_BATCH = 1
        private const val ARXIV_RESULTS_PER_WATCH = 40
        private const val CROSSREF_CONCURRENCY = 4
    }
}
