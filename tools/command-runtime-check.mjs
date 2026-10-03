import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import vm from 'node:vm';

const archive = fileURLToPath(new URL('../app/src/main/assets/runtime/rootfs.tgz', import.meta.url));
const prefix = './opt/tianshu/node_modules/tianshu-harness/';
const bundled = name => execFileSync('tar', ['-xOf', archive, prefix + name], {encoding: 'utf8', maxBuffer: 32 * 1024 * 1024});
assert.equal(JSON.parse(bundled('package.json')).version, '3.27.0');
const routes = bundled('dist/chunk-DXY5P57D.js');
for (const route of ['GET /sessions/:id/rewind-points', 'POST /sessions/:id/goal', 'GET /sessions/:id/goal',
  'POST /sessions/:id/goal/pause', 'POST /sessions/:id/goal/resume', 'POST /sessions/:id/goal/cancel',
  'GET /sessions/:id/plans', 'GET /sessions/:id/plans/:slug', 'PUT /sessions/:id/plans/:slug',
  'POST /sessions/:id/plans/:slug/approve', 'POST /sessions/:id/plans/:slug/reject',
  'POST /sessions/:id/plans/:slug/auto-approve/cancel']) {
  assert.ok(routes.includes(JSON.stringify(route) + ': withAuth2('), `missing bundled route: ${route}`);
}

// Execute the actual bundled read method to check the lazy-agent initialization path.
const start = routes.indexOf('  async listRewindPoints(id) {');
const end = routes.indexOf('\n  /**', start);
assert.ok(start > 0 && end > start);
const reader = vm.runInNewContext('({' + routes.slice(start, end) + '})', {buildUserAnchors: () => new Map()}).listRewindPoints;
const session = {};
let initialized = 0;
const points = await reader.call({sessions: new Map([['s', session]]),
  ensureAgentAsync: async s => { initialized++; s.agent = {getMessages: () => []}; },
  getAllEventsAsync: async () => ({events: []})}, 's');
assert.equal(initialized, 1);
assert.equal(points.length, 0);

const tracker = bundled('dist/chunk-OXMIV4WE.js');
const driverStart = tracker.indexOf('function buildGoalModePrompt(goal) {');
const driverEnd = tracker.indexOf('\nvar GoalTracker', driverStart);
assert.ok(driverStart > 0 && driverEnd > driverStart);
const officialDriver = vm.runInNewContext(tracker.slice(driverStart, driverEnd) + '\nbuildGoalModePrompt');
const kotlin = readFileSync(new URL('../app/src/main/java/com/xuanji/android/TianshuGoals.kt', import.meta.url), 'utf8');
const literal = kotlin.match(/internal fun goalKickoff\(goal: String\) = ("[^\n]+")/);
assert.ok(literal);
const template = JSON.parse(literal[1]);
for (const goal of ['完成 Android 验收', 'check "quotes"\nnext line']) {
  assert.equal(template.replace('$goal', goal), officialDriver(goal));
}
console.log('PASS: bundled Tianshu 3.27.0 has all 12 goal/plan routes; actual rewind-points method initializes the empty-session agent; Android kickoff matches the official driver byte for byte.');
