/**
 * Line diff of two texts (longest common subsequence). Returns [{ type: 'same'|'added'|'removed', text }].
 * Prompts are short, so the quadratic table is fine.
 */
export function diffLines(before, after) {
  const a = (before ?? '').split('\n')
  const b = (after ?? '').split('\n')
  const lcs = Array.from({ length: a.length + 1 }, () => new Array(b.length + 1).fill(0))
  for (let i = a.length - 1; i >= 0; i--) {
    for (let j = b.length - 1; j >= 0; j--) {
      lcs[i][j] = a[i] === b[j] ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1])
    }
  }
  const out = []
  let i = 0
  let j = 0
  while (i < a.length && j < b.length) {
    if (a[i] === b[j]) {
      out.push({ type: 'same', text: a[i] })
      i++
      j++
    } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
      out.push({ type: 'removed', text: a[i++] })
    } else {
      out.push({ type: 'added', text: b[j++] })
    }
  }
  while (i < a.length) out.push({ type: 'removed', text: a[i++] })
  while (j < b.length) out.push({ type: 'added', text: b[j++] })
  return out
}
