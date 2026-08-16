// Usage: node tools/lattice_verify.mjs <scenario.json> <lattice_vault.json>
import { readFileSync } from 'node:fs';
import crypto from 'node:crypto';
import { canonicalCarriers, canonicalTopology, evaluate, allEffectiveRotationCombos } from './lattice_board.mjs';

const [scenarioPath, vaultPath] = process.argv.slice(2);
const scenario = JSON.parse(readFileSync(scenarioPath, 'utf8'));
const vault = JSON.parse(readFileSync(vaultPath, 'utf8'));
const pepper = String(scenario.pepper || '').trim();

function open(canonical, s) {
  const salt = Buffer.from(s.salt, 'base64');
  const keyBuf = crypto.pbkdf2Sync(Buffer.from(canonical, 'utf8'), salt, 180000, 32, 'sha256');
  if (crypto.createHash('sha256').update(keyBuf).digest('base64') !== s.check) return null;
  const raw = Buffer.from(s.data, 'base64');
  const d = crypto.createDecipheriv('aes-256-gcm', keyBuf, Buffer.from(s.iv, 'base64'));
  d.setAuthTag(raw.subarray(raw.length - 16));
  return Buffer.concat([d.update(raw.subarray(0, raw.length - 16)), d.final()]).toString('utf8');
}

const board = scenario.board;
const a = open(
  canonicalCarriers(scenario.carriers.a, scenario.carriers.m, scenario.carriers.n, pepper),
  vault.stageA,
);
if (!a) throw new Error('stage A did not open');
const opened = JSON.parse(a);
if (opened.cells.length !== board.cells.length) throw new Error('board cell count mismatch');
console.log(`stage A OK: board with ${opened.cells.length} cells`);

const startRot = new Map(board.cells.map((c) => [`${c.q},${c.r}`, c.start]));
if (evaluate(board, startRot).closed) throw new Error('start position is already closed');

const solutions = [];
for (const rot of allEffectiveRotationCombos(board)) {
  if (evaluate(board, rot).closed) solutions.push(canonicalTopology(board, rot));
}
if (solutions.length !== 1) throw new Error('expected exactly 1 solution, found ' + solutions.length);
const solRot = new Map(board.cells.map((c) => [`${c.q},${c.r}`, c.solution]));
const solCanon = canonicalTopology(board, solRot);
if (solutions[0] !== solCanon) throw new Error('brute-force solution differs from scenario');
console.log('unique solution: ' + solCanon);

const b = open(solCanon, vault.stageB);
if (!b) throw new Error('stage B did not open');
const payload = JSON.parse(b);
if (payload.achievementId !== scenario.payload.achievementId) throw new Error('payload mismatch');
console.log(
  `stage B OK: ${payload.achievementId}, +${payload.bonusPoints} pts, theme ${payload.themeId}, unlockables ${payload.unlockables.join(', ')}`,
);
console.log('VERIFY PASSED');
