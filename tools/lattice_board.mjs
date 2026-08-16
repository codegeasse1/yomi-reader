// Shared hex-board logic. Mirrors LatticeBoardModel.kt / LatticeCanonical.kt.
export const DIRS = [[1, 0], [1, -1], [0, -1], [-1, 0], [-1, 1], [0, 1]];
export const SEGMENTS = {
  LINE: { connectors: [0, 3], symmetry: 3 },
  CURVE: { connectors: [0, 2], symmetry: 6 },
  ELBOW: { connectors: [0, 1], symmetry: 6 },
  TEE: { connectors: [0, 2, 4], symmetry: 2 },
  FORK: { connectors: [0, 1, 2], symmetry: 6 },
};
const key = (q, r) => `${q},${r}`;
const mod6 = (x) => ((x % 6) + 6) % 6;

export function connectorDirs(cell, rotation) {
  return new Set(SEGMENTS[cell.segment].connectors.map((c) => mod6(c + rotation)));
}

export function evaluate(board, rotations) {
  const cells = new Map(board.cells.map((c) => [key(c.q, c.r), c]));
  const pk = key(board.port.q, board.port.r);
  const dirsOf = (k) => connectorDirs(cells.get(k), rotations.get(k));
  if (!cells.has(pk) || !dirsOf(pk).has(board.port.dir)) {
    return { closed: false, coreReached: false, reached: new Set(), stubs: new Set() };
  }
  const reached = new Set([pk]);
  const stubs = new Set();
  let core = false;
  const queue = [pk];
  while (queue.length) {
    const k = queue.shift();
    const [q, r] = k.split(',').map(Number);
    for (const d of dirsOf(k)) {
      if (k === pk && d === board.port.dir) continue;
      const nq = q + DIRS[d][0];
      const nr = r + DIRS[d][1];
      if (nq === 0 && nr === 0) { core = true; continue; }
      const nk = key(nq, nr);
      if (cells.has(nk) && dirsOf(nk).has(mod6(d + 3))) {
        if (!reached.has(nk)) { reached.add(nk); queue.push(nk); }
      } else {
        stubs.add(k);
      }
    }
  }
  const closed = core && stubs.size === 0 && reached.size === cells.size;
  return { closed, coreReached: core, reached, stubs };
}

/**
 * Stage A key. v2 includes forge pepper so the open-string is not
 * reconstructible from carrier thresholds alone in plain enum form.
 * pepper lives only in scenario + generated LatticeVaultData.FRAME.
 */
export function canonicalCarriers(a, m, n, pepper = '') {
  if (pepper) {
    return `carriers/v2|p:${pepper}|a:${a}|m:${m}|n:${n}`;
  }
  return `carriers/v1|a:${a}|m:${m}|n:${n}`;
}

export function canonicalTopology(board, rotations) {
  const parts = board.cells
    .map((c) => {
      const sym = SEGMENTS[c.segment].symmetry;
      const rot = ((rotations.get(key(c.q, c.r)) % sym) + sym) % sym;
      return { q: c.q, r: c.r, rot };
    })
    .sort((a, b) => a.q - b.q || a.r - b.r)
    .map((c) => `${c.q},${c.r}:${c.rot}`);
  return 'topology/v1|' + parts.join(';');
}

export function* allEffectiveRotationCombos(board) {
  const syms = board.cells.map((c) => SEGMENTS[c.segment].symmetry);
  const idx = new Array(board.cells.length).fill(0);
  while (true) {
    yield new Map(board.cells.map((c, i) => [key(c.q, c.r), idx[i]]));
    let i = 0;
    while (i < idx.length) {
      idx[i]++;
      if (idx[i] < syms[i]) break;
      idx[i] = 0;
      i++;
    }
    if (i === idx.length) return;
  }
}
