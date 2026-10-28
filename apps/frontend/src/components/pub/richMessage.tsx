import { Fragment, type ReactNode } from 'react';

const MARK = '⁣';

/**
 * Lets a translated sentence contain links/emphasis without splitting it into fragments
 * (word order differs in Sinhala and Tamil). Message: "I accept the {terms} and the {privacy}".
 *
 *   renderRich(t('authFlow.consent', richValues(['terms', 'privacy'])), { terms: <Link>…</Link>, privacy: <Link>…</Link> })
 */
export function richValues(names: string[]): Record<string, string> {
  return Object.fromEntries(names.map((name) => [name, `${MARK}${name}${MARK}`]));
}

export function renderRich(text: string, parts: Record<string, ReactNode>): ReactNode {
  const pieces = text.split(new RegExp(`${MARK}(\\w+)${MARK}`));
  return pieces.map((piece, i) => (i % 2 === 1 ? <Fragment key={i}>{parts[piece] ?? piece}</Fragment> : <Fragment key={i}>{piece}</Fragment>));
}
