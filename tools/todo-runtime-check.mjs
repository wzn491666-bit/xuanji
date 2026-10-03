import assert from 'node:assert/strict';
import {readFileSync, mkdtempSync, mkdirSync, cpSync} from 'node:fs';
import {execFileSync} from 'node:child_process';
import {fileURLToPath, pathToFileURL} from 'node:url';
import {createHash, timingSafeEqual} from 'node:crypto';
import path from 'node:path';
import vm from 'node:vm';
import {buildAndroidCommandRoutes, mutateTodos, restoreAndroidTodos, todoRevision, androidTodoState} from '../app/src/main/assets/android-runtime/command-routes.mjs';

const archive = fileURLToPath(new URL('../app/src/main/assets/runtime/rootfs.tgz', import.meta.url));
const prefix = './opt/tianshu/node_modules/tianshu-harness/dist/';
const bundled = name => execFileSync('tar', ['-xOf', archive, prefix + name], {encoding: 'utf8', maxBuffer: 32 * 1024 * 1024});
const server = bundled('chunk-DXY5P57D.js');
const overlay = new URL('../app/src/main/assets/android-runtime/command-transform.mjs', import.meta.url);
assert.ok(readFileSync(new URL('../app/src/main/assets/android-runtime/register.mjs', import.meta.url), 'utf8')
  .includes('injectAndroidCommands'), 'production loader must install the authenticated todo bridge');
const {injectAndroidCommands, injectAndroidAgent} = await import(overlay);
const transformed = injectAndroidCommands(server, 'file:///test/command-routes.mjs');
assert.ok(transformed.includes('buildAndroidCommandRoutes'));
execFileSync(process.execPath, ['--check', '--input-type=module'], {input: transformed});
assert.throws(() => injectAndroidCommands(transformed, 'file:///test/command-routes.mjs'), /incompatible/);
assert.ok(injectAndroidAgent(bundled('serve-agent-RW6S3C3G.js')).includes('xuanjiWriteTodos'));
const build = fileURLToPath(new URL('../app/build/', import.meta.url));
mkdirSync(build, {recursive: true});
const fixture = mkdtempSync(path.join(build, 'todo-runtime-check-'));
execFileSync('tar', ['-xzf', archive, '-C', fixture, './opt/tianshu/node_modules/tianshu-harness/dist/*.js',
  './opt/tianshu/node_modules/tianshu-harness/package.json']);
const dist = path.join(fixture, 'opt/tianshu/node_modules/tianshu-harness/dist');
const {TodoStore} = await import(pathToFileURL(path.join(dist, 'todo-store-EVMVALBU.js')));
const {createTodoTool} = await import(pathToFileURL(path.join(dist, 'todo-KJBCXBN2.js')));
const bridge = injectAndroidAgent(bundled('serve-agent-RW6S3C3G.js'));
const adapterText = bridge.slice(bridge.indexOf('    getTodos: () =>'), bridge.indexOf('    // Hot-inject MCP'));
function makeAgent() {
  const store = new TodoStore();
  const trace = [];
  const tools = new Map([['todo', createTodoTool(store)]]);
  const adapter = vm.runInNewContext('({' + adapterText + '})', {cwd: '/sdcard', stores: {refs: {todoStore: store}, toolRegistry: tools},
    agent: {capturePlanSteps: steps => trace.push(steps)}});
  return {adapter, store, trace};
}
const a = makeAgent(), b = makeAgent();
const base = [{id: 'a-1', content: '读屏验证', status: 'pending', activeForm: '正在验证读屏'},
  {id: 'a-2', content: '核对结果', status: 'completed'}];
assert.equal((await a.adapter.xuanjiWriteTodos(base)).isError, undefined);
assert.deepEqual(a.store.read(), base);
assert.equal(a.trace.length, 1, 'original todo tool must update the execution trace');
assert.deepEqual(b.store.read(), [], 'independent session must not share the process default store');
const revision = todoRevision(base);
let updated = mutateTodos(base, {operation: 'start', id: 'a-1', expectedRevision: revision});
assert.equal(updated[0].status, 'in_progress');
assert.equal(base[0].status, 'pending', 'mutation must not change the supplied snapshot');
assert.throws(() => mutateTodos(updated, {operation: 'done', id: 'a-1', expectedRevision: revision}), /已被天枢更新/);
assert.throws(() => mutateTodos(base, {operation: 'done', id: 'a-', expectedRevision: revision}), /不唯一/);
assert.throws(() => mutateTodos(base, {operation: 'clear', id: 'a-1', expectedRevision: revision}), /未知/);
assert.equal(mutateTodos(base, {operation: 'move', id: 'a-1', direction: 'up', expectedRevision: revision})[0].id, 'a-1');
updated = mutateTodos(base, {operation: 'move', id: 'a-1', direction: 'down', expectedRevision: revision});
assert.equal(updated[0].id, 'a-2');
assert.equal(mutateTodos(base, {operation: 'skip', id: 'a-1', expectedRevision: revision}).length, 1);
assert.equal(mutateTodos(base, {operation: 'edit', id: 'a-1', content: '新任务', expectedRevision: revision})[0].activeForm, undefined);

const managerStart = transformed.indexOf('  async xuanjiMutateTodos(');
const managerEnd = transformed.indexOf('  getAgentForSession(', managerStart);
const methods = vm.runInNewContext('({' + transformed.slice(managerStart, managerEnd) + '})', {androidTodoState});
const sessions = new Map([['a', {record: {id: 'a'}, seq: 0, agent: a.adapter}], ['b', {record: {id: 'b'}, seq: 0, agent: b.adapter}]]);
const records = {a: [], b: []};
let releaseFlush;
const manager = {
  ...methods, sessions, persistence: {flushThrough: (_id, seq) => new Promise(resolve => { releaseFlush = () => resolve(seq); })},
  ensureEventsAsync: async () => {},
  prepareSession: async id => sessions.has(id),
  getEventsAsync: async id => ({events: records[id], lastSeq: sessions.get(id).seq}),
  persistRecord: () => {},
  ownsSessionDurability: () => true,
  ensureAgentAsync: async session => session.agent,
  getSession: id => sessions.get(id)?.record,
  ensureSessionAgent: async id => sessions.has(id),
  getAgentForSession: id => sessions.get(id)?.agent,
  append(session, type, data) { records[session.record.id].push({seq: ++session.seq, type, data}); },
};
const authStart = server.indexOf('function extractBearerToken(');
const authEnd = server.indexOf('// src/server/cors.ts', authStart);
const wrapperStart = server.indexOf('function withAuth2(');
const wrapperEnd = server.indexOf('// src/server/storage-cleanup-route.ts', wrapperStart);
const withAuth = vm.runInNewContext(server.slice(authStart, authEnd) + server.slice(wrapperStart, wrapperEnd) + '\nwithAuth2',
  {createHash, timingSafeEqual, PROTOCOL_HEADERS: {'x-tianshu-protocol': '1'}});
const routes = buildAndroidCommandRoutes(manager, withAuth, 'test-token');
const read = (id, token = 'test-token') => routes['GET /sessions/:id/xuanji/todos'](null, {id}, {authorization: 'Bearer ' + token});
const write = (data, id = 'a') => routes['POST /sessions/:id/xuanji/todos'](data, {id}, {authorization: 'Bearer test-token'});
assert.equal((await read('a', 'wrong')).status, 401);
assert.equal((await read('missing')).status, 404);
assert.equal((await read('b')).body.items.length, 0);
assert.equal((await write({operation: 'done', id: 'a-1', expectedRevision: 'bad'})).status, 400);
sessions.get('a').running = true;
assert.equal((await write({operation: 'done', id: 'a-1', expectedRevision: revision})).status, 409);
assert.equal((await read('a')).status, 200, 'reading remains available while model is running');
sessions.get('a').running = false;
let completed = false;
const pending = write({operation: 'done', id: 'a-1', expectedRevision: revision}).then(result => { completed = true; return result; });
await new Promise(resolve => setImmediate(resolve));
assert.equal(completed, false, 'do not acknowledge before durable flush');
assert.equal(sessions.get('a').xuanjiTodoBusy, true);
assert.equal((await write({operation: 'done', id: 'a-2', expectedRevision: revision})).status, 409);
releaseFlush();
const committed = await pending;
assert.equal(committed.status, 200);
assert.equal(committed.body.durable, true);
assert.equal(committed.body.items[0].status, 'completed');
assert.equal(committed.body.revision, records.a[0].data.revision);
assert.equal(sessions.get('a').xuanjiTodoBusy, false);
assert.deepEqual(b.store.read(), []);
assert.equal((await write({operation: 'done', id: 'a-1', expectedRevision: revision})).status, 409);

const restored = makeAgent();
await restoreAndroidTodos({getAllEventsAsync: async () => ({events: records.a})}, 'a', restored.adapter);
assert.deepEqual(restored.store.read(), committed.body.items, 'rebuild restores the authoritative per-session state');
const failed = makeAgent();
await restoreAndroidTodos({getAllEventsAsync: async () => ({events: [
  {type: 'tool_use', data: {name: 'todo', id: 't', input: {action: 'write', todos: base}}},
  {type: 'todo_state', data: {items: base}},
  {type: 'tool_result', data: {name: 'todo', id: 't', isError: true}},
]})}, 'a', failed.adapter);
assert.deepEqual(failed.store.read(), [], 'legacy optimistic failed writes must never be restored');
const legacy = makeAgent();
await restoreAndroidTodos({getAllEventsAsync: async () => ({events: [
  {type: 'tool_use', data: {name: 'todo', id: 't', input: {action: 'write', todos: base}}},
  {type: 'tool_result', data: {name: 'todo', id: 't', isError: false}},
]})}, 'a', legacy.adapter);
assert.deepEqual(legacy.store.read(), base, 'legacy confirmed tool writes restore correctly');

const callbacksStart = transformed.indexOf('      onToolUse: (toolId,');
const callbacksEnd = transformed.indexOf('      onContextBudget:', callbacksStart);
const events = [];
const session = {agent: a.adapter, record: {}, seq: 0};
const context = {append: (_, type, data) => events.push({type, data}), flushToolResultBuf: () => {}, scanArtifacts: () => {}};
const callbacks = vm.runInNewContext('({' + transformed.slice(callbacksStart, callbacksEnd) + '})', {
  ...context,
  session, isActive: () => true, journalWrite: () => {}, redactValue: x => x, redactText: x => x,
  DELEGATION_TOOLS: new Set(), planSubmitToolIds: new Map(), androidTodoState,
  truncateUtf16Safe: x => x, containsRegisteredFrame: () => false,
});
callbacks.onToolUse.call(context, 'bad', 'todo', {action: 'write', todos: []});
assert.equal(events.filter(e => e.type === 'todo_state').length, 0, 'tool use cannot publish uncommitted state');
callbacks.onToolResult.call(context, 'bad', 'todo', 'invalid', true);
assert.equal(events.filter(e => e.type === 'todo_state').length, 0, 'failed tools cannot publish progress');
callbacks.onToolResult.call(context, 'good', 'todo', 'saved', false);
assert.equal(events.filter(e => e.type === 'todo_state').length, 1);
assert.equal(events.at(-1).data.revision, committed.body.revision);
console.log('PASS: real bundled TodoStore/tool, per-session isolation, execution trace, auth, conflict checks, write lock, durable acknowledgement, rebuild/legacy restoration and successful-tool SSE');

const overlayDir = path.join(fixture, 'opt/xuanji');
cpSync(new URL('../app/src/main/assets/android-runtime/', import.meta.url), overlayDir, {recursive: true});
// Test-only exports expose the production-loaded manager and route factory, without changing disk bytes.
const production = `
  import assert from 'node:assert/strict';
  import {registerHooks} from 'node:module';
  import vm from 'node:vm';
  registerHooks({load(url, context, nextLoad) {
    const result = nextLoad(url, context);
    if (!url.endsWith('/chunk-DXY5P57D.js')) return result;
    return {...result, source: Buffer.from(result.source).toString('utf8') + '\\nexport {buildSessionRoutes, RuntimeSessionManager};'};
  }});
  const {buildSessionRoutes, RuntimeSessionManager} = await import(${JSON.stringify(pathToFileURL(path.join(dist, 'chunk-DXY5P57D.js')).href)});
  const {TodoStore} = await import(${JSON.stringify(pathToFileURL(path.join(dist, 'todo-store-EVMVALBU.js')).href)});
  const {createTodoTool} = await import(${JSON.stringify(pathToFileURL(path.join(dist, 'todo-KJBCXBN2.js')).href)});
  const storedRecords = new Map(), storedEvents = new Map();
  const persistence = {
    loadRecords: () => [], loadEvents: id => storedEvents.get(id) ?? [],
    loadEventsAsync: async id => storedEvents.get(id) ?? [],
    saveRecord: record => storedRecords.set(record.id, {...record}),
    appendEvent: (id, event) => storedEvents.set(id, [...storedEvents.get(id) ?? [], event]),
    flushThrough: async (_id, seq, signal) => { signal?.throwIfAborted(); return seq; },
  };
  const createAgent = (_cwd, _id, _mode, _model, _tools, signal) => {
    signal?.throwIfAborted();
    const store = new TodoStore();
    return {...vm.runInNewContext('({' + ${JSON.stringify(adapterText)} + '})', {cwd: ${JSON.stringify(fixture)},
      stores: {refs: {todoStore: store}, toolRegistry: new Map([['todo', createTodoTool(store)]])}, agent: {capturePlanSteps: () => {}}}),
      getMessages: () => [], abort: () => {}, run: () => { throw new Error('Read/write must never spend model tokens'); }};
  };
  const manager = new RuntimeSessionManager({createAgent, persistence, defaultCwd: ${JSON.stringify(fixture)}, idleAgentTtlMs: 0, externalScanMs: 0});
  manager.createSession({id: 'smoke', cwd: ${JSON.stringify(fixture)}, workspaceMode: 'explicit'});
  const routes = buildSessionRoutes(manager, 'production-smoke-token');
  const auth = {authorization: 'Bearer production-smoke-token'};
  const params = {id: 'smoke'};
  const get = routes['GET /sessions/:id/xuanji/todos'];
  const post = routes['POST /sessions/:id/xuanji/todos'];
  assert.equal((await get(null, params, {})).status, 401);
  const initial = await get(null, params, auth);
  assert.equal(initial.status, 200);
  const added = await post({operation: 'add', content: '真实路由写入验证', expectedRevision: initial.body.revision}, params, auth);
  assert.equal(added.status, 200);
  assert.equal(added.body.durable, true);
  assert.equal(manager.getAgentForSession('smoke').getTodos()[0].content, '真实路由写入验证');
  const stopController = new AbortController();
  stopController.abort();
  manager.sessions.get('smoke').preparationController = stopController;
  const finished = await post({operation: 'done', id: added.body.items[0].id, expectedRevision: added.body.revision}, params, auth);
  assert.equal(finished.status, 200, 'a completed Stop must not cancel an unrelated todo persistence barrier');
  assert.equal(finished.body.seq, storedEvents.get('smoke').at(-1).seq);
  assert.equal(storedRecords.get('smoke').lastSeq, finished.body.seq, 'native mutation must persist the session index');
  assert.equal(storedRecords.get('smoke').persistenceState, 'saved');
  manager.sessions.get('smoke').agent = null;
  const restored = await get(null, params, auth);
  assert.equal(restored.body.items[0].id, added.body.items[0].id);
  assert.equal(storedEvents.get('smoke').at(-1).type, 'todo_state');
  const highWater = storedEvents.get('smoke').at(-1).seq;
  const coldPersistence = {...persistence, loadRecords: () => [{...storedRecords.get('smoke'), lastSeq: 0}]};
  const coldManager = new RuntimeSessionManager({createAgent, persistence: coldPersistence, defaultCwd: ${JSON.stringify(fixture)}, idleAgentTtlMs: 0, externalScanMs: 0});
  const coldRoutes = buildSessionRoutes(coldManager, 'production-smoke-token');
  const coldRead = await coldRoutes['GET /sessions/:id/xuanji/todos'](null, params, auth);
  const coldWrite = await coldRoutes['POST /sessions/:id/xuanji/todos']({operation: 'add', content: '重启后新增', expectedRevision: coldRead.body.revision}, params, auth);
  assert.equal(coldWrite.status, 200);
  assert.ok(storedEvents.get('smoke').at(-1).seq > highWater, 'cold mutation must synchronize event high water before appending');
  assert.equal(coldRead.body.seq, highWater, 'read snapshots must identify their event high water');
  coldManager.createSession({id: 'stopped-build', cwd: ${JSON.stringify(fixture)}, workspaceMode: 'explicit'});
  const cancelledBuild = coldManager.sessions.get('stopped-build');
  cancelledBuild.running = true;
  cancelledBuild.preparationController = stopController;
  assert.equal((await coldRoutes['GET /sessions/:id/xuanji/todos'](null, {id: 'stopped-build'}, auth)).status, 503,
    'a cancelled active run must not bypass the original build cancellation');
  cancelledBuild.running = false;
  assert.equal((await coldRoutes['GET /sessions/:id/xuanji/todos'](null, {id: 'stopped-build'}, auth)).status, 200,
    'an idle read must not inherit the cancelled run build signal');
  let captured;
  manager.enableTool = async () => true;
  manager.waitForRunSettled = async () => true;
  manager.prepareSession = async () => true;
  manager.submitRun = async (_id, prompt, images, requestId, opts) => { captured = {prompt, opts}; return {ok: true}; };
  const promptHandler = routes['POST /sessions/:id/prompt'];
  const command = '/plan 完成 Android 验证';
  assert.equal((await promptHandler({prompt: command, requestId: 'display-check'}, params, auth)).status, 200);
  assert.notEqual(captured.prompt, command, 'the official driver must still expand and reach the model');
  assert.equal(captured.opts.promptText, command, 'durable user history must contain the original short command');
  assert.equal((await promptHandler({prompt: '普通消息', requestId: 'normal-check'}, params, auth)).status, 200);
  assert.equal(captured.prompt, '普通消息');
  assert.equal(captured.opts.promptText, undefined, 'ordinary messages retain upstream behavior');
  console.log('PASS: actual production import hook, full upstream manager/route factory, authenticated durable mutation and lazy-agent restoration');
`;
process.stdout.write(execFileSync(process.execPath, ['--import', pathToFileURL(path.join(overlayDir, 'register.mjs')).href,
  '--input-type=module', '-e', production], {encoding: 'utf8', timeout: 45000,
  env: {...process.env, RIVET_HOME: path.join(fixture, 'home')}}));
