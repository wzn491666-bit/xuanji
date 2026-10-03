// Exact compatibility anchors from Tianshu 3.27.0 (Apache-2.0).
function replaceOnce(source, anchor, replacement) {
  if (source.split(anchor).length !== 2) throw new Error('Tianshu command adapter is incompatible with the Xuanji overlay');
  return source.replace(anchor, replacement);
}

export function injectAndroidAgent(source) {
  const anchor = '    getTodos: () => stores.refs.todoStore.read(),';
  return replaceOnce(source, anchor, `${anchor}
    xuanjiRestoreTodos: (items) => {
      stores.refs.todoStore.write(items);
      if (items.length) agent.capturePlanSteps(items);
    },
    xuanjiWriteTodos: (items) => stores.toolRegistry.get("todo").execute({
      input: {action: "write", todos: items}, cwd,
      onPlanSteps: (steps) => agent.capturePlanSteps(steps)
    }),`);
}

export function injectAndroidCommands(source, moduleUrl) {
  let result = replaceOnce(source, '  return routes;\n}\nasync function extractDocumentsToText(documents) {',
    '  Object.assign(routes, buildAndroidCommandRoutes(manager, withAuth2, apiToken));\n  return routes;\n}\nasync function extractDocumentsToText(documents) {');
  result = replaceOnce(result, '      const promptText = documents?.length ? prompt : void 0;',
    '      const promptText = documents?.length || (trimmed.startsWith("/") && prompt !== data.prompt) ? data.prompt : void 0;');
  result = replaceOnce(result, `  requestHandoff(id, note) {
    const session = this.sessions.get(id);
    if (!session) return { ok: false, error: "Session not found" };
    if (session.running)`, `  requestHandoff(id, note) {
    const session = this.sessions.get(id);
    if (!session) return { ok: false, error: "Session not found" };
    if (session.running || session.activeRunSettlement || session.xuanjiTodoBusy)`);
  result = replaceOnce(result, '    if (!this.run(id, buildHandoffPrompt(src, note))) {',
    '    if (!this.run(id, buildHandoffPrompt(src, note), void 0, false, void 0, {promptText: note ? `/handoff ${note}` : "/handoff"})) {');
  result = replaceOnce(result, `      storePlan(resume.planJson, params.id);
      const ok = manager.run(params.id, resume.prompt);`, `      const session = manager.sessions.get(params.id);
      if (!session || session.running || session.activeRunSettlement || session.xuanjiTodoBusy) {
        return {status: 409, body: {error: "Session is already running or updating", code: "busy"}};
      }
      storePlan(resume.planJson, params.id);
      const ok = manager.run(params.id, resume.prompt, void 0, false, void 0, {promptText: "/team-resume " + data.groupId});`);
  result = replaceOnce(result, '      return { status: 200, body: { resumed: true, message: resume.prompt } };',
    '      return { status: 200, body: { resumed: true, message: "Team resumed" } };');
  result = replaceOnce(result, '    if (session.running || session.activeRunSettlement) throw Object.assign',
    '    if (session.running || session.activeRunSettlement || session.xuanjiTodoBusy) throw Object.assign');
  result = replaceOnce(result, `  async forkSession(id, opts = {}) {
    const src = this.sessions.get(id);
    if (!src) return { ok: false, reason: "not_found" };
    if (src.running)`, `  async forkSession(id, opts = {}) {
    const src = this.sessions.get(id);
    if (!src) return { ok: false, reason: "not_found" };
    if (src.running || src.activeRunSettlement || src.xuanjiTodoBusy)`);
  result = replaceOnce(result, `      model: src.record.model,
      domain: src.record.domain,`, `      model: src.record.model,
      reasoningEffort: src.record.reasoningEffort,
      domain: src.record.domain,`);
  result = replaceOnce(result, '    this.copyForkImages(id, created.id, eventsPrefix);',
    '    const attachmentWarnings = copyAndroidForkAttachments(this.persistence, id, created.id, eventsPrefix);');
  result = replaceOnce(result, `      destination
    });
    this.persistRecord(child);`, `      destination
    });
    if (attachmentWarnings.length) this.append(child, "fork_attachment_warning", {warnings: attachmentWarnings});
    this.persistRecord(child);`);
  result = replaceOnce(result, '    return { ok: true, record: { ...child.record } };',
    '    return { ok: true, record: { ...child.record }, warnings: attachmentWarnings };');
  result = replaceOnce(result, '      if (result.ok) return { status: 200, body: { session: result.record } };',
    '      if (result.ok) return { status: 200, body: { session: result.record, warnings: result.warnings } };');
  result = replaceOnce(result, '  getAgentForSession(id) {', `  xuanjiReadQueue(id) {
    const session = this.sessions.get(id);
    if (!session) return void 0;
    return {id, seq: session.seq, running: Boolean(session.running), durable: false,
      lanes: session.queueLane.map(entry => ({id: entry.id, text: redactText(entry.text),
        status: entry.status, ts: entry.ts,
        ...(entry.images?.length ? {imageCount: entry.images.length} : {}),
        ...(entry.documentNames?.length ? {documentNames: [...entry.documentNames]} : {})}))};
  }
  async xuanjiMutateTodos(id, mutate) {
    const session = this.sessions.get(id);
    if (!session) throw Object.assign(new Error("会话不存在"), {status: 404});
    if (session.running || session.xuanjiTodoBusy) throw Object.assign(new Error("天枢正在执行，请稍后编辑待办"), {status: 409});
    session.xuanjiTodoBusy = true;
    try {
      await this.ensureEventsAsync(session);
      const agent = await this.ensureAgentAsync(session);
      if (this.sessions.get(id) !== session || !this.ownsSessionDurability(session)) throw Object.assign(new Error("会话已失效"), {status: 409});
      if (!this.persistence?.flushThrough) throw new Error("待办存储不支持持久化确认");
      const items = mutate(agent.getTodos());
      const receipt = await agent.xuanjiWriteTodos(items);
      if (receipt.isError) throw Object.assign(new Error(receipt.content), {status: 400});
      const committed = agent.getTodos();
      this.append(session, "todo_state", androidTodoState(committed));
      const seq = session.seq;
      const watermark = await this.persistence.flushThrough(id, seq);
      if (!Number.isSafeInteger(watermark) || watermark < seq) throw new Error("待办持久化确认不完整");
      if (!this.ownsSessionDurability(session)) throw Object.assign(new Error("会话已失效"), {status: 409});
      session.record.durableWatermark = watermark;
      session.record.persistenceState = "saved";
      this.persistRecord(session);
      return {items: committed, notice: receipt.content, durable: true, seq};
    } finally { session.xuanjiTodoBusy = false; }
  }
  getAgentForSession(id) {`);
  result = replaceOnce(result, '  run(id, prompt, images, recovery = false, receipt, opts) {\n    const session = this.sessions.get(id);\n    if (!session || session.running) return false;',
    '  run(id, prompt, images, recovery = false, receipt, opts) {\n    const session = this.sessions.get(id);\n    if (!session || session.running || session.xuanjiTodoBusy) return false;');
  result = replaceOnce(result, `    const created = this.createAgent(
      session.record.cwd,
      session.record.id,
      session.approvalMode,
      session.record.model,
      session.record.allowedTools,
      session.preparationController?.signal
    );`, `    const created = this.createAgent(
      session.record.cwd,
      session.record.id,
      session.approvalMode,
      session.record.model,
      session.record.allowedTools,
      session.running ? session.preparationController?.signal : void 0
    );`);
  const finish = `    const finish = (agent) => {
      session.agent = agent;
      this.applySelections(session);
      this.warnIfHistoryLost(session);
      return agent;
    };
    if (created && typeof created.then === "function") {
      const pending = created.then(finish);
      this.agentBuilds.set(session.record.id, pending);
      const clearSlot = () => {
        if (this.agentBuilds.get(session.record.id) === pending) this.agentBuilds.delete(session.record.id);
      };
      void pending.then(clearSlot, clearSlot);
      return pending;
    }
    return finish(created);`;
  result = replaceOnce(result, finish, `    const pending = Promise.resolve(created).then(async (agent) => {
      await restoreAndroidTodos(this, session.record.id, agent);
      session.agent = agent;
      this.applySelections(session);
      this.warnIfHistoryLost(session);
      return agent;
    });
    this.agentBuilds.set(session.record.id, pending);
    const clearSlot = () => {
      if (this.agentBuilds.get(session.record.id) === pending) this.agentBuilds.delete(session.record.id);
    };
    void pending.then(clearSlot, clearSlot);
    return pending;`);
  result = replaceOnce(result, `        if (name === "todo") {
          const items = extractTodoState(input);
          if (items) this.append(session, "todo_state", { items });
        }`, '');
  result = replaceOnce(result, `        if (name === "plan_task" && isError === false) {
          const items = session.agent?.getTodos?.();
          if (items && items.length > 0) this.append(session, "todo_state", { items });
        }`, `        if ((name === "todo" || name === "plan_task") && isError === false) {
          const items = session.agent?.getTodos?.();
          if (items) this.append(session, "todo_state", androidTodoState(items));
        }`);
  const injectedImport = `import {buildAndroidCommandRoutes, restoreAndroidTodos, androidTodoState, copyAndroidForkAttachments} from ${JSON.stringify(moduleUrl)};\n`;
  const insertion = result.startsWith('#!') ? result.indexOf('\n') + 1 : 0;
  return result.slice(0, insertion) + injectedImport + result.slice(insertion);
}
