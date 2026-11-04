package org.koocheh.psi

import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.koocheh.model.InterestTag

/**
 * Round-time benchmark for low-end devices: 10 tags per side, 5 shared.
 *
 * The engine under test is chosen by instrumentation argument `psiEngineClass`
 * (fully qualified name of a PsiEngine with a public no-arg constructor), e.g.
 *   -e psiEngineClass com.example.RealPsiEngine
 * Default is FakePsiEngine, whose timings are MEANINGLESS (it only proves the harness runs).
 *
 * Both parties run in one process on one thread, so the "total" figure is the sum of both
 * sides' compute plus in-memory hand-off, with no radio time. The per-side figures time
 * each side's calls separately. Peak memory is not measured here. Tags are never logged.
 */
@RunWith(AndroidJUnit4::class)
class PsiBenchmark {
    private val engineClassName: String =
        InstrumentationRegistry.getArguments().getString("psiEngineClass")
            ?: "org.koocheh.psi.FakePsiEngine"

    private fun newEngine(): PsiEngine =
        Class.forName(engineClassName).getDeclaredConstructor().newInstance() as PsiEngine

    @Test fun tenTagsCountOnly() = bench(RevealLevel.COUNT_ONLY)

    @Test fun tenTagsReveal() = bench(RevealLevel.TAGS)

    private fun bench(level: RevealLevel) {
        val a = (0 until TAGS).map { InterestTag("benchtag-%02d".format(it)) }.toSet()
        val b = (TAGS - SHARED until 2 * TAGS - SHARED)
            .map { InterestTag("benchtag-%02d".format(it)) }.toSet()
        val total = ArrayList<Long>()
        val sideA = ArrayList<Long>()
        val sideB = ArrayList<Long>()
        var bytes = 0

        repeat(WARMUP + ITERATIONS) { i ->
            val ea = newEngine()
            val eb = newEngine()
            var ta = 0L
            var tb = 0L
            val t0 = System.nanoTime()

            var s = System.nanoTime()
            val sa = ea.newSession(a, level)
            ta += System.nanoTime() - s
            s = System.nanoTime()
            val sb = eb.newSession(b, level)
            tb += System.nanoTime() - s

            var msgBytes = 0
            for (round in 0 until MAX_ROUNDS) {
                if (sa.result != null && sb.result != null) break
                s = System.nanoTime(); val ma = sa.outgoing(); ta += System.nanoTime() - s
                s = System.nanoTime(); val mb = sb.outgoing(); tb += System.nanoTime() - s
                msgBytes += ma.size + mb.size
                if (ma.isNotEmpty()) { s = System.nanoTime(); sb.incoming(ma); tb += System.nanoTime() - s }
                if (mb.isNotEmpty()) { s = System.nanoTime(); sa.incoming(mb); ta += System.nanoTime() - s }
            }
            val elapsed = System.nanoTime() - t0

            // A broken engine makes timings meaningless, so check correctness first.
            assertEquals(SHARED, sa.result?.count)
            assertEquals(SHARED, sb.result?.count)

            if (i >= WARMUP) { total += elapsed; sideA += ta; sideB += tb; bytes = msgBytes }
        }

        val fake = engineClassName.endsWith("FakePsiEngine")
        Log.i(
            TAG,
            "engine=$engineClassName${if (fake) " (FAKE: timings meaningless)" else ""} " +
                "reveal=$level tags=$TAGS shared=$SHARED iters=$ITERATIONS " +
                "transcript_bytes=$bytes total_ms[median=${ms(med(total))} p90=${ms(p90(total))} " +
                "max=${ms(total.max())}] sideA_median_ms=${ms(med(sideA))} " +
                "sideB_median_ms=${ms(med(sideB))} device=${Build.MODEL} sdk=${Build.VERSION.SDK_INT}",
        )
    }

    private fun med(v: List<Long>): Long = v.sorted()[v.size / 2]

    private fun p90(v: List<Long>): Long = v.sorted()[minOf(v.size - 1, v.size * 9 / 10)]

    private fun ms(nanos: Long): String = "%.3f".format(nanos / 1_000_000.0)

    private companion object {
        const val TAG = "PsiBenchmark"
        const val TAGS = 10
        const val SHARED = 5
        const val WARMUP = 5
        const val ITERATIONS = 30
        const val MAX_ROUNDS = 8
    }
}
