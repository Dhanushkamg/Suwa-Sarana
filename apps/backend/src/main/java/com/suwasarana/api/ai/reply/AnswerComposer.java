package com.suwasarana.api.ai.reply;

import com.suwasarana.api.ai.kb.KbEntry;
import com.suwasarana.api.ai.kb.KbMessages;
import com.suwasarana.api.ai.kb.KbStatus;
import com.suwasarana.api.ai.kb.KnowledgeBase;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic composition of a reply from retrieved knowledge-base entries. No model is involved: the text is the
 * curated answer of each entry in the reply language, in question order, plus the "confirm with NBTS" footer when a
 * conflicting or unreviewed entry was used.
 */
@Component
public class AnswerComposer {

    private final KnowledgeBase kb;
    private final KbMessages messages;

    public AnswerComposer(KnowledgeBase kb, KbMessages messages) {
        this.kb = kb;
        this.messages = messages;
    }

    /** The entries to answer with: the retrieved ones followed by their companions, without duplicates. */
    public List<KbEntry> expand(List<KbEntry> retrieved) {
        Map<String, KbEntry> out = new LinkedHashMap<>();
        for (KbEntry e : retrieved) {
            out.putIfAbsent(e.id(), e);
            for (String c : e.companions()) {
                kb.byId(c).ifPresent(ce -> out.putIfAbsent(ce.id(), ce));
            }
        }
        return new ArrayList<>(out.values());
    }

    /** The body: one answer, or a bullet per entry for a multi-part question. */
    public String body(List<KbEntry> used, String lang) {
        if (used.size() == 1) return used.get(0).answer(lang);
        StringBuilder sb = new StringBuilder();
        for (KbEntry e : used) {
            if (sb.length() > 0) sb.append('\n');
            sb.append("• ").append(e.answer(lang));
        }
        return sb.toString();
    }

    /** Footer telling the user to confirm with NBTS; empty when no used entry needs it (GAP answers carry their own). */
    public String footer(List<KbEntry> used, String lang) {
        for (KbEntry e : used) {
            if (e.status() == KbStatus.CONFLICT || (e.status() == KbStatus.VERIFIED && e.needsHumanReview())) {
                return messages.get("confirmFooter", lang);
            }
        }
        return "";
    }

    public boolean needsConfirmation(List<KbEntry> used) {
        for (KbEntry e : used) if (e.requiresConfirmation()) return true;
        return false;
    }

    public boolean allVerified(List<KbEntry> used) {
        for (KbEntry e : used) if (e.status() != KbStatus.VERIFIED) return false;
        return true;
    }

    public String join(String body, String footer) {
        return footer == null || footer.isBlank() ? body : body + "\n\n" + footer;
    }
}
