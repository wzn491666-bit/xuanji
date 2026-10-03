import assert from 'node:assert/strict';
import {readFileSync, mkdirSync, mkdtempSync, cpSync} from 'node:fs';
import {execFileSync} from 'node:child_process';
import {fileURLToPath, pathToFileURL} from 'node:url';
import path from 'node:path';
import vm from 'node:vm';
import {injectAndroidEnvironment, original} from '../app/src/main/assets/android-runtime/prompt-transform.mjs';

const builds = new URL('../app/build/', import.meta.url);
mkdirSync(builds, {recursive: true});
const fixtureRoot = mkdtempSync(path.join(fileURLToPath(builds), 'runtime-prompt-check-'));
const packageRoot = path.join(fixtureRoot, 'opt/tianshu/node_modules/tianshu-harness');
execFileSync('tar', ['-xzf', fileURLToPath(new URL('../app/src/main/assets/runtime/rootfs.tgz', import.meta.url)),
  '-C', fixtureRoot, './opt/tianshu/node_modules/tianshu-harness/dist/*.js', './opt/tianshu/node_modules/tianshu-harness/package.json']);
const baselineRoot = path.join(fixtureRoot, 'baseline');
cpSync(packageRoot, baselineRoot, {recursive: true});
const file = pathToFileURL(path.join(baselineRoot, 'dist/chunk-QL7XQBU6.js'));
const bundled = readFileSync(file, 'utf8');
const environment = readFileSync(new URL('../app/src/main/assets/android-runtime/android-environment.txt', import.meta.url), 'utf8');
const transformed = injectAndroidEnvironment(bundled, environment);
assert.equal(transformed.replace(transformed.slice(transformed.indexOf('function buildSystemPrompt(ctx) {'), transformed.indexOf('function detectModelFamily(')).trimEnd(), original), bundled);
assert.throws(() => injectAndroidEnvironment(bundled.replace(original, ''), environment), /incompatible/);
assert.throws(() => injectAndroidEnvironment(transformed, environment), /incompatible/);

// Execute the bundled static prompt builder and its actual subagent implementation.
function builder(source) {
  const start = source.indexOf('// src/prompt/static.ts');
  const end = source.indexOf('function detectModelFamily(');
  assert.ok(start > 0 && end > start);
  return vm.runInNewContext(`function init_esm_shims(){}\n${source.slice(start,end)}\nbuildSystemPrompt`);
}
const baseline = builder(bundled);
const augmented = builder(transformed);
for (const audience of [undefined, 'subagent']) for (const modelFamily of [undefined, 'deepseek', 'mimo', 'glm', 'openai', 'anthropic']) {
  const ctx = {tools: [], audience, modelFamily};
  const before = baseline(ctx);
  const after = augmented(ctx);
  const block = '\n\n' + environment.trim();
  assert.equal(after.replace(block, ''), before, `original prompt preserved: ${audience}/${modelFamily}`);
  assert.equal(after.split('<xuanji-android-environment>').length, 2);
  assert.ok(after.includes('proot') && after.includes('snapshotId'));
}
assert.ok(bundled.includes("messages: [{ role: \"system\", content: this.systemPrompt }, ...result]"));
assert.ok(bundled.includes('this.systemPrompt = buildSystemPrompt(config.staticCtx)'));
const overlay = path.join(fixtureRoot, 'opt/xuanji');
cpSync(new URL('../app/src/main/assets/android-runtime/', import.meta.url), overlay, {recursive: true});
const augmentedModule = pathToFileURL(path.join(packageRoot, 'dist/chunk-QL7XQBU6.js')).href;
const check = `
  import assert from 'node:assert/strict';
  const baseline = await import(${JSON.stringify(file.href)});
  const augmented = await import(${JSON.stringify(augmentedModule)});
  for (const audience of [undefined, 'subagent']) {
    const config = {model: 'test', maxTokens: 128, staticCtx: {tools: [], audience, modelFamily: 'deepseek'},
      volatileCtx: {cwd: '/sdcard', tools: [], gitStatus: ''}};
    const oldEngine = new baseline.PromptEngine(config);
    const engine = new augmented.PromptEngine(config);
    const messages = [{role: 'user', content: 'Check the environment.'}];
    const oldRequest = oldEngine.buildOaiRequest(messages);
    const request = engine.buildOaiRequest(messages);
    assert.equal(request.messages[0].role, 'system');
    assert.ok(request.messages[0].content.includes('<xuanji-android-environment>'));
    assert.equal(request.messages[0].content.replace(${JSON.stringify('\n\n' + environment.trim())}, ''), oldRequest.messages[0].content);
    assert.deepEqual(request.messages.slice(1), oldRequest.messages.slice(1));
    assert.equal(engine.buildOaiRequest(messages).messages[0].content, request.messages[0].content);
  }
  console.log('PASS: production Node import hook injects Android context into actual Tianshu main/subagent OAI request system messages; original history and stable prefix preserved');
`;
process.stdout.write(execFileSync(process.execPath, ['--import', pathToFileURL(path.join(overlay, 'register.mjs')).href, '--input-type=module', '-e', check], {encoding: 'utf8'}));
console.log('PASS: bundled static prompt compatibility and model calibrations preserved');
