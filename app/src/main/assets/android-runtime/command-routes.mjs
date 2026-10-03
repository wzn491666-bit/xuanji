import {createHash, randomUUID} from 'node:crypto';

const statuses = new Set(['pending', 'in_progress', 'completed']);
const fault = (status, message) => Object.assign(new Error(message), {status});

// Fork attachment ids are persistence keys; metadata names must never become paths.
export function copyAndroidForkAttachments(persistence, sourceId, childId, events) {
  const safeId = id => typeof id === 'string' && /^[A-Za-z0-9_-]{1,128}$/.test(id);
  const references = new Map();
  const add = (kind, id, name) => {
    const key = `${kind}:${typeof id}:${String(id)}`;
    if (!references.has(key)) references.set(key, {kind, id, ...(typeof name === 'string' ? {name: name.slice(0, 160)} : {})});
  };
  for (const event of events) {
    for (const id of Array.isArray(event.data?.imageIds) ? event.data.imageIds : []) add('image', id);
    for (const doc of Array.isArray(event.data?.documents) ? event.data.documents : []) add('document', doc?.id, doc?.name);
  }
  const warnings = [];
  for (const reference of references.values()) {
    const {kind, id} = reference;
    const warn = reason => warnings.push({kind, id: typeof id === 'string' ? id.slice(0, 128) : '',
      ...(reference.name ? {name: reference.name} : {}), reason});
    if (!safeId(sourceId) || !safeId(childId) || sourceId === childId) { warn('invalid_session_id'); continue; }
    if (!safeId(id)) { warn('invalid_reference'); continue; }
    const read = kind === 'image' ? persistence?.readImage : persistence?.readDocument;
    const save = kind === 'image' ? persistence?.saveImage : persistence?.saveDocument;
    if (typeof read !== 'function' || typeof save !== 'function') { warn('storage_unavailable'); continue; }
    try {
      const original = read.call(persistence, sourceId, id);
      if (!original) { warn('source_missing'); continue; }
      if (!Buffer.isBuffer(original.bytes) || typeof original.mime !== 'string' ||
        (kind === 'document' && !/^[a-z0-9]{1,16}$/.test(original.ext))) { warn('invalid_reference'); continue; }
      save.call(persistence, childId, id, original.bytes.toString('base64'),
        kind === 'image' ? original.mime : `attachment.${original.ext}`);
      const copied = read.call(persistence, childId, id);
      if (!copied || !Buffer.isBuffer(copied.bytes) || !original.bytes.equals(copied.bytes) || copied.mime !== original.mime ||
        (kind === 'document' && copied.ext !== original.ext)) warn('verification_failed');
    } catch { warn('copy_failed'); }
  }
  return warnings;
}

export function validateTodos(items) {
  if (!Array.isArray(items) || items.length > 500) throw fault(400, '待办清单格式无效或超过 500 项');
  const ids = new Set();
  return items.map(item => {
    if (!item || typeof item.id !== 'string' || !item.id.trim() || item.id.length > 128 || ids.has(item.id) ||
      typeof item.content !== 'string' || !item.content.trim() || item.content.length > 10000 || !statuses.has(item.status) ||
      (item.activeForm !== undefined && (typeof item.activeForm !== 'string' || !item.activeForm.trim() || item.activeForm.length > 10000))) {
      throw fault(400, '待办条目格式无效，ID 必须唯一');
    }
    ids.add(item.id);
    return {id: item.id, content: item.content, status: item.status, ...(item.activeForm !== undefined ? {activeForm: item.activeForm} : {})};
  });
}

export const todoRevision = items => createHash('sha256').update(JSON.stringify(validateTodos(items))).digest('hex');

export function androidTodoState(items) {
  try { return {items, authoritative: true, revision: todoRevision(items)}; }
  catch { return {items, authoritative: true}; }
}

export function mutateTodos(items, data) {
  const current = validateTodos(items);
  if (!data || typeof data.expectedRevision !== 'string' || !/^[a-f0-9]{64}$/.test(data.expectedRevision)) throw fault(400, '缺少待办版本，请先刷新');
  if (data.expectedRevision !== todoRevision(current)) throw fault(409, '待办已被天枢更新，请刷新后重新操作');
  const content = () => {
    if (typeof data.content !== 'string' || !data.content.trim() || data.content.length > 10000) throw fault(400, '请填写任务内容（最多 10000 字）');
    return data.content;
  };
  if (data.operation === 'add') return validateTodos([...current, {id: `user-${randomUUID()}`, content: content(), status: 'pending'}]);
  if (typeof data.id !== 'string' || !data.id || data.id.length > 128) throw fault(400, '缺少任务 ID');
  const matches = current.some(t => t.id === data.id) ? current.filter(t => t.id === data.id) : current.filter(t => t.id.startsWith(data.id));
  if (matches.length !== 1) throw fault(matches.length ? 400 : 404, matches.length ? '任务 ID 前缀不唯一，请使用完整 ID' : '未找到该任务');
  const item = matches[0];
  const index = current.indexOf(item);
  switch (data.operation) {
    case 'done': item.status = 'completed'; break;
    case 'start':
      current.forEach(t => { if (t.status === 'in_progress') t.status = 'pending'; });
      item.status = 'in_progress'; break;
    case 'pending': item.status = 'pending'; break;
    case 'edit': item.content = content(); delete item.activeForm; break;
    case 'skip': current.splice(index, 1); break;
    case 'move': {
      if (!['up', 'down'].includes(data.direction)) throw fault(400, '方向必须是 up 或 down');
      const target = index + (data.direction === 'up' ? -1 : 1);
      if (target >= 0 && target < current.length) [current[index], current[target]] = [current[target], current[index]];
      break;
    }
    default: throw fault(400, '未知的待办操作');
  }
  return validateTodos(current);
}

// Old todo_state events were optimistic. Only replay confirmed writes.
export async function restoreAndroidTodos(manager, id, agent) {
  if (!agent.xuanjiRestoreTodos || agent.getTodos().length) return;
  const history = await manager.getAllEventsAsync(id);
  const writes = new Map();
  let latest;
  let previous;
  for (const event of history?.events ?? []) {
    try {
      if (event.type === 'tool_use' && event.data.name === 'todo' && event.data.input?.action === 'write') {
        writes.set(event.data.id, validateTodos(event.data.input.todos));
      }
      if (event.type === 'tool_result' && event.data.name === 'todo' && event.data.isError === false && writes.has(event.data.id)) {
        latest = writes.get(event.data.id);
      }
      if (event.type === 'todo_state' && (event.data.authoritative === true ||
        (previous?.type === 'tool_result' && previous.data.name === 'plan_task' && previous.data.isError === false))) {
        latest = validateTodos(event.data.items);
      }
    } catch { /* Ignore malformed historical payloads, never convert them into current state. */ }
    previous = event;
  }
  if (latest !== undefined) agent.xuanjiRestoreTodos(latest);
}

export function buildAndroidCommandRoutes(manager, withAuth, apiToken) {
  const safe = handler => withAuth(async (body, params) => {
    try { return {status: 200, body: await handler(body, params.id)}; }
    catch (error) { return {status: error.status ?? 500, body: {error: error.message ?? '待办操作失败'}}; }
  }, apiToken);
  return {
    'GET /sessions/:id/xuanji/queue': safe((_, id) => {
      const snapshot = manager.xuanjiReadQueue(id);
      if (!snapshot) throw fault(404, '会话不存在');
      return snapshot;
    }),
    'GET /sessions/:id/xuanji/todos': safe(async (_, id) => {
      if (!manager.getSession(id)) throw fault(404, '会话不存在');
      if (!await manager.prepareSession(id)) throw fault(409, '会话已失效');
      if (!await manager.ensureSessionAgent(id)) throw fault(503, '天枢代理尚未就绪');
      const events = await manager.getEventsAsync(id);
      if (!events) throw fault(409, '会话已失效');
      const items = validateTodos(manager.getAgentForSession(id).getTodos());
      return {items, revision: todoRevision(items), seq: events.lastSeq};
    }),
    'POST /sessions/:id/xuanji/todos': safe(async (data, id) => {
      const result = await manager.xuanjiMutateTodos(id, items => mutateTodos(items, data));
      return {...result, revision: todoRevision(result.items)};
    }),
  };
}
