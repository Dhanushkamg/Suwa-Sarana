package com.suwasarana.api.ai.kb;

/** Verification status of a knowledge-base entry. */
public enum KbStatus {
    /** Stated on an official NBTS page. */
    VERIFIED,
    /** The official page and another source disagree; the reply shows the official value and says it must be confirmed. */
    CONFLICT,
    /** No official source found; the reply is a safe "no confirmed figure, ask the Medical Officer / NBTS" message. */
    GAP
}
