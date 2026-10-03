import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {createHash, timingSafeEqual} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {join} from 'node:path';
import vm from 'node:vm';
import {injectAndroidCommands} from '../app/src/main/assets/android-runtime/command-transform.mjs';
import {buildAndroidCommandRoutes} from '../app/src/main/assets/android-runtime/command-routes.mjs';
import * as commandRoutes from '../app/src/main/assets/android-runtime/command-routes.mjs';

const archive = fileURLToPath(new URL('../app/src/main/assets/runtime/rootfs.tgz', import.meta.url));
const original = execFileSync('tar', ['-xOf', archive,
  './opt/tianshu/node_modules/tianshu-harness/dist/chunk-DXY5P57D.js'], {encoding: 'utf8', maxBuffer: 32 * 1024 * 1024});
const source = injectAndroidCommands(original, 'file:///test/command-routes.mjs');
execFileSync(process.execPath, ['--check', '--input-type=module'], {input: source});
assert.throws(() => injectAndroidCommands(source, 'file:///test/command-routes.mjs'), /incompatible/);
const method = (anchor, end) => {
  const start = source.indexOf(anchor);
  const finish = source.indexOf(end, start + anchor.length);
  assert.ok(start > 0 && finish > start, `missing real bundled method: ${anchor}`);
  return source.slice(start, finish);
};
const handoffText = method('  requestHandoff(id, note) {', '\n  /**');
const requestHandoff = vm.runInNewContext('({' + handoffText + '})', {
  join4: join, getSessionDir: cwd => join(cwd, 'sessions'), buildHandoffPrompt: (_path, note) => `official driver: ${note ?? ''}`,
}).requestHandoff;
const a = {record: {id: 'a', cwd: '/a'}, running: false};
const b = {record: {id: 'b', cwd: '/b'}, running: false};
const sessions = new Map([['a', a], ['b', b]]);
let handoffRun;
const handoffManager = {sessions, run: (...args) => { handoffRun = args; return true; }};
assert.equal(requestHandoff.call(handoffManager, 'a', 'retain evidence').ok, true);
assert.equal(handoffRun[1], 'official driver: retain evidence', 'model must still receive the official handoff driver');
assert.equal(handoffRun[5]?.promptText, '/handoff retain evidence', 'handoff history must retain the short user command');
assert.equal(b.pendingHandoff, undefined, 'handoff must stay bound to its source session');
for (const flag of ['running', 'activeRunSettlement', 'xuanjiTodoBusy']) {
  a[flag] = true;
  assert.equal(requestHandoff.call(handoffManager, 'a').ok, false, `handoff must reject ${flag}`);
  delete a[flag];
}

const compactContext = vm.runInNewContext('({' + method('  async compactContext(id) {', '\n  async waitForRunSettled(') + '})').compactContext;
let compacted = 0;
const compactEvents = [];
const compactManager = {sessions, ensureAgentAsync: async () => ({compactContext: async () => { compacted++; return true; }, getContextBudget: () => ({usedTokens: 123})}),
  append: (_session, type, data) => compactEvents.push({type, data}), persistRecord: () => {}};
a.xuanjiTodoBusy = true;
await assert.rejects(compactContext.call(compactManager, 'a'), error => error.status === 409);
assert.equal(compacted, 0, 'compaction cannot race a native todo durability barrier');
delete a.xuanjiTodoBusy;
assert.equal((await compactContext.call(compactManager, 'a')).changed, true);
assert.equal(a.running, false);
assert.equal(compactEvents.at(-1).data.phase, 'idle');
compactManager.ensureAgentAsync = async () => ({compactContext: async () => { throw new Error('provider compact failed'); }});
await assert.rejects(compactContext.call(compactManager, 'a'), /provider compact failed/);
assert.equal(a.running, false, 'failed compaction must release the session');

const authStart = source.indexOf('function extractBearerToken(');
const authEnd = source.indexOf('// src/server/cors.ts', authStart);
const wrapperStart = source.indexOf('function withAuth2(');
const wrapperEnd = source.indexOf('// src/server/storage-cleanup-route.ts', wrapperStart);
const withAuth = vm.runInNewContext(source.slice(authStart, authEnd) + source.slice(wrapperStart, wrapperEnd) + '\nwithAuth2',
  {createHash, timingSafeEqual, PROTOCOL_HEADERS: {'x-tianshu-protocol': '1'}});
const route = (name, context) => {
  const start = source.indexOf(JSON.stringify(name) + ': withAuth2(');
  const end = source.indexOf('    }, apiToken)', start);
  assert.ok(start > 0 && end > start, `missing real bundled route: ${name}`);
  return vm.runInNewContext('(' + source.slice(start + JSON.stringify(name).length + 2, end + '    }, apiToken)'.length) + ')',
    {withAuth2: withAuth, apiToken: 'test-session-command', ...context});
};
const auth = {authorization: 'Bearer test-session-command'};
const teamTrace = [];
const teamManager = {getSession: id => sessions.get(id)?.record, sessions,
  run: (...args) => { teamTrace.push({run: args}); return true; }};
const teamResume = route('POST /sessions/:id/team-resume', {manager: teamManager,
  isSafeFileName: value => /^[a-z0-9-]+$/.test(value),
  loadCheckpoint: () => ({groupId: 'team-a'}), buildResumeFromCheckpoint: () => ({planJson: '{"plan":true}', prompt: 'official resume driver'}),
  storePlan: (...args) => teamTrace.push({plan: args})});
assert.equal((await teamResume({groupId: 'team-a'}, {id: 'a'}, {})).status, 401);
for (const flag of ['running', 'activeRunSettlement', 'xuanjiTodoBusy']) {
  a[flag] = true;
  assert.equal((await teamResume({groupId: 'team-a'}, {id: 'a'}, auth)).status, 409);
  assert.equal(teamTrace.length, 0, `team resume must reject ${flag} before altering its plan`);
  delete a[flag];
}
const resumed = await teamResume({groupId: 'team-a'}, {id: 'a'}, auth);
assert.equal(resumed.body.resumed, true);
assert.equal(resumed.body.message, 'Team resumed', 'response must not expose the expanded driver as UI feedback');
assert.equal(teamTrace[0].plan[1], 'a');
assert.equal(teamTrace[1].run[0], 'a');
assert.equal(teamTrace[1].run[1], 'official resume driver');
assert.equal(teamTrace[1].run[5].promptText, '/team-resume team-a');

const forkText = method('  async forkSession(id, opts = {}) {', '\n  /**');
const forkTranscripts = new Map([['a', [{role: 'user', content: 'retain files'}, {role: 'assistant', content: 'acknowledged'}]]]);
class SessionPersist {
  constructor(id) { this.id = id; }
  loadOai() { return forkTranscripts.get(this.id) ?? []; }
  async appendOaiWithChecksum(message) { forkTranscripts.set(this.id, [...forkTranscripts.get(this.id) ?? [], message]); }
  async flushSessionBuffer() {}
}
const forkSession = vm.runInNewContext('({' + forkText + '})', {
  SessionPersist, copyAndroidForkAttachments: commandRoutes.copyAndroidForkAttachments,
}).forkSession;
for (const flag of ['running', 'activeRunSettlement', 'xuanjiTodoBusy']) {
  a[flag] = true;
  assert.equal((await forkSession.call({sessions}, 'a')).reason, 'running', `fork must reject ${flag} before any copy`);
  delete a[flag];
}
const attachments = new Map();
const writes = [];
const forkEvents = [
  {seq: 1, type: 'user', ts: 1, data: {text: 'retain files', imageIds: ['img-1'], documents: [{id: 'doc-1', name: '../../private/notes.pdf'}]}},
  {seq: 2, type: 'assistant', ts: 2, data: {text: 'acknowledged'}},
];
attachments.set('a/image/img-1', {bytes: Buffer.from('image-original'), mime: 'image/png'});
attachments.set('a/document/doc-1', {bytes: Buffer.from('document-original'), mime: 'application/pdf', ext: 'pdf'});
const forkPersistence = {
  loadEvents: () => forkEvents, flushSync: () => {}, appendEvent: () => {},
  readImage: (id, img) => attachments.get(`${id}/image/${img}`),
  saveImage: (id, img, base64, mime) => { writes.push({kind: 'image', id, attachment: img}); attachments.set(`${id}/image/${img}`, {bytes: Buffer.from(base64, 'base64'), mime}); },
  readDocument: (id, doc) => attachments.get(`${id}/document/${doc}`),
  saveDocument: (id, doc, base64, name) => {
    writes.push({kind: 'document', id, attachment: doc, name});
    attachments.set(`${id}/document/${doc}`, {bytes: Buffer.from(base64, 'base64'), mime: 'application/pdf', ext: name.split('.').at(-1)});
  },
};
a.record.reasoningEffort = 'high';
a.events = forkEvents;
const forkManager = {
  sessions, persistence: forkPersistence, maxEvents: 100,
  ensureEvents: () => {}, flushDeltaBuf: () => {}, flushToolResultBuf: () => {},
  idGenerator: () => 'child', nextForkTitleNumber: () => 2,
  createSession: record => { sessions.set(record.id, {record, seq: 0}); return record; },
  append: (session, type, data) => { session.events.push({seq: ++session.seq, type, data}); },
  persistRecord: () => {}, copyForkImages: () => {},
};
const forked = await forkSession.call(forkManager, 'a');
assert.equal(forked.ok, true);
assert.equal(forked.record.reasoningEffort, 'high', 'fork must inherit the actual source reasoning effort');
assert.equal(attachments.get('child/image/img-1')?.bytes.toString(), 'image-original');
assert.equal(attachments.get('child/document/doc-1')?.bytes.toString(), 'document-original', 'fork must preserve original documents, not merely extracted text');
assert.equal(writes.find(write => write.kind === 'document').name, 'attachment.pdf', 'metadata file names cannot become persistence paths');
assert.equal(forked.warnings.length, 0);
assert.equal(forkTranscripts.get('child').length, 2, 'successful fork retains its real model transcript prefix');
assert.equal(a.events.length, 2, 'fork must never append to the source timeline');

assert.equal(typeof commandRoutes.copyAndroidForkAttachments, 'function');
const invalidReads = [];
const invalidStorage = {
  ...forkPersistence,
  readImage: (...args) => { invalidReads.push(args); return forkPersistence.readImage(...args); },
  readDocument: (...args) => { invalidReads.push(args); return forkPersistence.readDocument(...args); },
};
const invalid = commandRoutes.copyAndroidForkAttachments(invalidStorage, 'a', 'safe-child', [
  {data: {imageIds: ['../../escape'], documents: [{id: '..\\escape', name: 'notes.pdf'}]}},
]);
assert.equal(invalid.length, 2);
assert.ok(invalid.every(warning => warning.reason === 'invalid_reference'));
assert.equal(invalidReads.length, 0, 'invalid attachment ids cannot reach the filesystem boundary');
const invalidSession = commandRoutes.copyAndroidForkAttachments(invalidStorage, '../source', 'safe-child', forkEvents);
assert.ok(invalidSession.every(warning => warning.reason === 'invalid_session_id'));
assert.equal(invalidReads.length, 0, 'invalid session ids cannot reach the filesystem boundary');
const missing = commandRoutes.copyAndroidForkAttachments(forkPersistence, 'missing-source', 'safe-child', forkEvents);
assert.equal(missing.length, 2);
assert.ok(missing.every(warning => warning.reason === 'source_missing'));
const unavailable = commandRoutes.copyAndroidForkAttachments(undefined, 'a', 'safe-child', forkEvents);
assert.equal(unavailable.length, 2);
assert.ok(unavailable.every(warning => warning.reason === 'storage_unavailable'));
const corrupt = commandRoutes.copyAndroidForkAttachments({...forkPersistence,
  readDocument: (id, doc) => id === 'broken-child' ? {bytes: Buffer.from('corrupt'), mime: 'application/pdf', ext: 'pdf'} : forkPersistence.readDocument(id, doc),
}, 'a', 'broken-child', forkEvents);
assert.equal(corrupt.length, 1);
assert.equal(corrupt[0].reason, 'verification_failed');
const failedSave = commandRoutes.copyAndroidForkAttachments({...forkPersistence, saveDocument: () => { throw new Error('disk full'); }}, 'a', 'full-child', forkEvents);
assert.equal(failedSave.length, 1);
assert.equal(failedSave[0].reason, 'copy_failed');
forkManager.idGenerator = () => 'warning-child';
forkPersistence.readDocument = () => undefined;
const incompleteFork = await forkSession.call(forkManager, 'a');
assert.equal(incompleteFork.ok, true, 'missing originals do not destroy an otherwise valid conversation branch');
assert.equal(incompleteFork.warnings.length, 1, 'successful fork must report missing attachments');
assert.equal(sessions.get('warning-child').events.at(-1).type, 'fork_attachment_warning', 'missing originals must remain traceable after the response is dismissed');
const forkRoute = route('POST /sessions/:id/fork', {manager: {...forkManager, forkSession: () => Promise.resolve(incompleteFork)}});
assert.equal((await forkRoute({}, {id: 'a'}, {})).status, 401);
const forkReply = await forkRoute({}, {id: 'a'}, auth);
assert.equal(forkReply.status, 200);
assert.equal(forkReply.body.warnings[0].reason, 'source_missing', 'fork HTTP response must not conceal incomplete attachments');

let laneSequence = 0;
const queueMethods = vm.runInNewContext('({' +
  method('  steer(id, input) {', '\n  /**') + ',\n' +
  method('  queue(id, text, attachments) {', '\n  /**') + ',\n' +
  method('  retractQueued(id, laneId) {', '\n  /**') + '})', {
  randomId: () => ++laneSequence, redactText: text => text, countLaneAttachments: () => ({images: 0, documents: 0}), MAX_IMAGES: 4, MAX_DOCUMENTS: 4,
});
const queueEvents = [];
for (const session of [a, b]) {
  session.queueLane = [];
  session.steer = {values: [], push(text) { this.values.push(text); }};
}
const queueManager = {...queueMethods, sessions, cancelPlanAutoApprove: () => {}, persistDocuments: () => [],
  append: (session, type, data) => queueEvents.push({id: session.record.id, type, data}), touch: () => {}, now: () => 123};
assert.equal(queueManager.queue('a', 'idle guidance'), 'idle');
assert.equal(queueManager.steer('a', 'idle guidance'), 'idle');
a.running = true;
const queued = queueManager.queue('a', 'next turn');
assert.ok(queued.laneId);
assert.equal(queueEvents.at(-1).type, 'queue_pending');
assert.equal(b.queueLane.length, 0);
assert.equal(queueManager.steer('b', {laneId: queued.laneId}), 'idle');
assert.equal(queueManager.retractQueued('b', queued.laneId), 'lane_not_found');
assert.equal(queueManager.steer('a', {laneId: queued.laneId}), 'queued');
assert.equal(a.steer.values[0], 'next turn');
assert.equal(queueEvents.at(-1).data.status, 'steered');
assert.equal(queueManager.retractQueued('a', queued.laneId), 'lane_not_queued');
const retractable = queueManager.queue('a', 'cancel this');
a.running = false;
assert.equal(queueManager.retractQueued('a', retractable.laneId), 'retracted', 'queued entries can be retracted after the current run stops');
assert.equal(queueEvents.at(-1).data.status, 'retracted');
assert.ok(queueEvents.every(event => event.id === 'a'), 'queue events must never escape into another session');
const xuanjiReadQueue = vm.runInNewContext('({' + method('  xuanjiReadQueue(id) {', '\n  async xuanjiMutateTodos(') + '})',
  {redactText: text => text.replace('private-value', '***')}).xuanjiReadQueue;
queueManager.xuanjiReadQueue = id => xuanjiReadQueue.call(queueManager, id);
const readQueue = buildAndroidCommandRoutes(queueManager, withAuth, 'test-session-command')['GET /sessions/:id/xuanji/queue'];
assert.ok(readQueue, 'the production adapter must expose an authoritative queue read route');
assert.equal((await readQueue(null, {id: 'a'}, {})).status, 401);
assert.equal((await readQueue(null, {id: 'missing'}, auth)).status, 404);
a.running = true;
queueManager.queue('a', 'private-value', {images: ['raw-image-data'], documentNames: ['notes.pdf'], attachmentText: 'raw-document-text'});
const snapshot = await readQueue(null, {id: 'a'}, auth);
assert.equal(snapshot.body.id, 'a');
assert.equal(snapshot.body.durable, false, 'upstream queue entries are in memory and must not claim disk durability');
assert.equal(snapshot.body.lanes.at(-1).text, '***');
assert.equal(snapshot.body.lanes.at(-1).imageCount, 1);
assert.equal(snapshot.body.lanes.at(-1).documentNames[0], 'notes.pdf');
assert.ok(!JSON.stringify(snapshot.body).includes('raw-image-data'));
assert.ok(!JSON.stringify(snapshot.body).includes('raw-document-text'));
assert.equal((await readQueue(null, {id: 'b'}, auth)).body.lanes.length, 0);
console.log('PASS: actual bundled handoff/team drivers with short history, auth, busy guards, compact failure release, fork boundary, queue/steer isolation, retract semantics and private authoritative queue snapshots');
