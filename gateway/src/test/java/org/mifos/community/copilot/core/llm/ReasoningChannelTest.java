/**
 * Copyright since 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package org.mifos.community.copilot.core.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Keeping the two channels apart when a provider uses both at once.
 *
 * <p>Written after review pointed out that the client stopped inspecting content once a native
 * reasoning field had arrived. The reasoning was that a provider separating the two itself
 * would not also inline them, which is an assumption about every proxy and adapter sitting in
 * front of every engine. The cost of it being wrong is a model's private deliberation reaching
 * a loan officer as though it were advice, which is the worst thing this feature can do.
 *
 * <p>These drive {@link ReasoningSplitter} directly, since that is where the decision now lives
 * and it needs no provider to exercise.
 */
class ReasoningChannelTest {

    private final List<String> answer = new ArrayList<>();
    private final List<String> reasoning = new ArrayList<>();
    private final ReasoningSplitter splitter = new ReasoningSplitter(answer::add, reasoning::add);

    /**
     * The finding itself. A turn where the provider sends reasoning in its own field and also
     * leaves markers in the content must still keep the marked text out of the answer.
     */
    @Test
    void inlineMarkersAreStillCaughtAlongsideANativeReasoningField() {
        // What the native field carried, delivered straight to the reasoning channel.
        reasoning.add("Deciding which tool to use.");

        // What arrived on the content channel in the same turn.
        splitter.accept("Her balance is USD 500.");
        splitter.accept("<think>she is two weeks in arrears, do not mention it</think>");
        splitter.accept(" Anything else?");
        splitter.finish();

        assertThat(String.join("", answer)).isEqualTo("Her balance is USD 500. Anything else?");
        assertThat(String.join("", answer)).doesNotContain("arrears");
        assertThat(String.join("", reasoning)).contains("Deciding which tool to use.").contains("arrears");
    }

    /** The same thing arriving in fragments, which is how it actually arrives. */
    @Test
    void theSameHoldsWhenTheMarkerIsSplitAcrossDeltas() {
        for (String delta : new String[] { "Balance is ", "USD 500.", "<th", "ink>", "arrears", "</th", "ink>", " Done." }) {
            splitter.accept(delta);
        }
        splitter.finish();

        assertThat(String.join("", answer)).isEqualTo("Balance is USD 500. Done.");
        assertThat(String.join("", answer)).doesNotContain("arrears").doesNotContain("<");
    }

    /** Nothing legitimate is lost by always looking: only the two literal markers are known. */
    @Test
    void ordinaryBankingProseIsUntouched() {
        splitter.accept("The instalment is < 500 and the client is > 30 days overdue.");
        splitter.finish();

        assertThat(String.join("", answer)).isEqualTo("The instalment is < 500 and the client is > 30 days overdue.");
        assertThat(reasoning).isEmpty();
    }
}
