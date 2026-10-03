const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const {chromium} = require('playwright');

async function main() {
  const snapshotScript = fs.readFileSync(path.join(__dirname, '../app/src/main/assets/browser/snapshot.js'), 'utf8');
  const actionScript = fs.readFileSync(path.join(__dirname, '../app/src/main/assets/browser/action.js'), 'utf8');
  const fixture = `<main><h1>Browser fixture</h1><p>Visible article</p>
    <button id="click" onclick="document.querySelector('p').textContent='Clicked successfully'">Change article</button>
    <label>Search<input id="query" oninput="document.querySelector('h1').textContent=this.value"></label>
    <label>Password<input type="password" value="PRIVATE_PASSWORD"></label>
    <textarea name="api_key">PRIVATE_API_KEY</textarea><input type="email" value="PRIVATE_EMAIL">
    <button style="display:none">Hidden button</button><div style="opacity:0"><button>Faded button</button></div>
    <button id="disabled" disabled>Disabled button</button><input type="file" id="upload">
    <a href="https://user:pass@example.com">Link with credentials</a>
    <label>Category<select id="category" onchange="this.dataset.changed=this.value"><option value="one">First</option><option value="two">Second</option><option value="locked" disabled>Locked</option><option value="duplicate-a">Same label</option><option value="duplicate-b">Same label</option></select></label>
    <label>Accept terms<input id="check" type="checkbox" onchange="this.dataset.changed=String(this.checked)"></label>
    <div id="editor" contenteditable="true" aria-label="Notes">Old notes</div>
    <form id="search-form" onsubmit="event.preventDefault();this.dataset.submitted='yes'"><label>Form query<input id="form-query"></label></form>
    <fieldset disabled><input id="fieldset-input" aria-label="Disabled fieldset"></fieldset>
    <button id="aria-disabled" aria-disabled="true">ARIA disabled</button>
    <div id="scroll-region" role="button" aria-label="Scrollable region" style="height:80px;overflow:auto"><div style="height:900px">Scroll content</div></div>
    <section id="long">${Array.from({length: 240}, (_, i) => `<p>Paragraph ${i}: ${'Readable long document. '.repeat(12)}</p>`).join('')}</section>
    <iframe src="https://cross-origin.invalid/"></iframe></main>`;
  const server = http.createServer((request, response) => { response.setHeader('Content-Type', 'text/html'); response.end(fixture); });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  let browser;
  try {
    browser = await chromium.launch({headless: true, ...(process.env.BROWSER_CHANNEL ? {channel: process.env.BROWSER_CHANNEL} : {})});
    const page = await browser.newPage({viewport: {width: 390, height: 844}});
    await page.route('https://cross-origin.invalid/**', route => route.fulfill({status: 200, body: '<p>Excluded cross-origin frame</p>'}));
    await page.goto(`http://127.0.0.1:${server.address().port}/`);
    const snapshot = (token, options = {}) => page.evaluate(({script, token, options}) => (0, eval)(`(${script})`)(token, options), {script: snapshotScript, token, options});
    const action = args => page.evaluate(({script, args}) => (0, eval)(`(${script})`)(args), {script: actionScript, args});
    const first = await snapshot('snapshot-1');
    assert.equal(first.untrustedContent, true);
    assert.ok(first.text.includes('Visible article'));
    assert.ok(!JSON.stringify(first).includes('PRIVATE_'));
    assert.ok(!first.items.some(item => /Hidden|Faded/.test(item.name)));
    assert.ok(!JSON.stringify(first).includes('user:pass'));
    assert.ok(first.page, 'Snapshot must expose document paging');
    assert.equal(first.page.hasMore, true);
    const continuation = await snapshot('page-2', {textOffset: first.page.nextOffset});
    assert.ok(continuation.text.length > 0 && continuation.text !== first.text);
    let documentText = first.text + continuation.text;
    let cursor = continuation.page.nextOffset;
    for (let i = 0; cursor != null && i < 20; i++) {
      const part = await snapshot(`page-${i+3}`, {textOffset: cursor});
      documentText += part.text; cursor = part.page.nextOffset;
    }
    assert.equal(cursor, null);
    assert.ok(documentText.includes('Paragraph 239:'), 'Long-document paging must reach the end');
    assert.ok(first.limitations.some(item => /frame/i.test(item)));
    const region = await snapshot('region', {region: '#long', maxChars: 1000});
    assert.ok(region.text.startsWith('Paragraph 0:') && !region.text.includes('Visible article'));
    await assert.rejects(snapshot('bad-region', {region: '#missing'}), /未找到/);
    await assert.rejects(snapshot('bad-offset', {textOffset: -1}), /范围无效/);
    const beforeClick = await snapshot('before-click');
    const click = beforeClick.items.find(item => item.name === 'Change article');
    assert.equal((await action({snapshotId: 'before-click', elementId: click.id, action: 'click'})).dispatched, true);
    assert.equal(await page.locator('p').first().textContent(), 'Clicked successfully');
    const fresh = await snapshot('input');
    const input = fresh.items.find(item => item.name === 'Search');
    await action({snapshotId: 'input', elementId: input.id, action: 'type', text: 'Input event reached'});
    assert.equal(await page.locator('h1').textContent(), 'Input event reached');
    const invoke = async (label, actionName, rest = {}) => {
      const token = `${label}-${actionName}`;
      const state = await snapshot(token);
      const item = state.items.find(entry => entry.name === label);
      assert.ok(item, `Missing fixture target: ${label}`);
      return action({snapshotId: token, elementId: item.id, action: actionName, ...rest});
    };
    const category = (await snapshot('options')).items.find(item => item.name === 'Category');
    assert.ok(category.options.some(option => option.value === 'two' && option.label === 'Second'));
    await invoke('Category', 'select', {value: 'two'});
    assert.equal(await page.locator('#category').inputValue(), 'two');
    assert.equal(await page.locator('#category').getAttribute('data-changed'), 'two');
    await invoke('Category', 'select', {value: 'First', optionBy: 'label'});
    assert.equal(await page.locator('#category').inputValue(), 'one');
    await assert.rejects(invoke('Category', 'select', {value: 'locked'}), /不可用/);
    await assert.rejects(invoke('Category', 'select', {value: 'Same label', optionBy: 'label'}), /不唯一/);
    await invoke('Accept terms', 'check', {checked: true});
    assert.equal(await page.locator('#check').isChecked(), true);
    await invoke('Accept terms', 'check', {checked: false});
    assert.equal(await page.locator('#check').isChecked(), false);
    await invoke('Notes', 'type', {text: 'Rich notes'});
    assert.equal(await page.locator('#editor').innerText(), 'Rich notes');
    await invoke('Notes', 'type', {text: ''});
    assert.equal(await page.locator('#editor').textContent(), '');
    await invoke('Form query', 'submit');
    assert.equal(await page.locator('#search-form').getAttribute('data-submitted'), 'yes');
    await invoke('Scrollable region', 'scroll', {direction: 'down', amount: 300});
    assert.equal(await page.locator('#scroll-region').evaluate(el => el.scrollTop), 300);
    const scrollState = await snapshot('scroll-page');
    const moved = await action({snapshotId: 'scroll-page', action: 'scroll', direction: 'down', amount: 300});
    assert.ok(moved.scroll.after.y > moved.scroll.before.y);
    const viewport = await snapshot('viewport', {viewportOnly: true});
    assert.ok(viewport.viewport.scrollY > 0);
    await assert.rejects(invoke('Password', 'type', {text: 'blocked'}), /手动输入/);
    await assert.rejects(invoke('Disabled button', 'click'), /失效|不可用/);
    await assert.rejects(invoke('Disabled fieldset', 'type', {text: 'blocked'}), /失效|不可用/);
    await assert.rejects(invoke('ARIA disabled', 'click'), /失效|不可用/);
    await assert.rejects(invoke('Link with credentials', 'click'), /不含凭据/);
    const oneUse = await snapshot('single-use');
    const oneUseInput = oneUse.items.find(item => item.name === 'Search');
    await action({snapshotId: 'single-use', elementId: oneUseInput.id, action: 'type', text: 'Once'});
    await assert.rejects(action({snapshotId: 'single-use', elementId: oneUseInput.id, action: 'type', text: 'Twice'}), /过期/);
    const uploadState = await snapshot('upload');
    const upload = uploadState.items.find(item => item.kind === 'file');
    await assert.rejects(action({snapshotId: 'upload', elementId: upload.id, action: 'click'}), /用户/);
    const changedState = await snapshot('changed');
    const changed = changedState.items.find(item => item.name === 'Change article');
    await page.locator('#click').evaluate(el => { el.textContent = 'Different action'; });
    await assert.rejects(action({snapshotId: 'changed', elementId: changed.id, action: 'click'}), /变化|过期/);
    await page.locator('#click').evaluate(el => { el.textContent = 'Change article'; });
    const coveredState = await snapshot('covered');
    const covered = coveredState.items.find(item => item.name === 'Change article');
    await page.evaluate(() => { const overlay = document.createElement('div'); overlay.id='overlay'; overlay.style='position:fixed;inset:0;z-index:99999;background:white';document.body.append(overlay); });
    await assert.rejects(action({snapshotId: 'covered', elementId: covered.id, action: 'click'}), /遮挡/);
    await page.locator('#overlay').evaluate(el => el.remove());
    const hiddenState = await snapshot('hidden');
    const hiddenClick = hiddenState.items.find(item => item.name === 'Change article');
    await page.locator('#click').evaluate(el => { el.style.opacity = '0'; });
    await assert.rejects(action({snapshotId: 'hidden', elementId: hiddenClick.id, action: 'click'}), /不可见/);
    await snapshot('snapshot-2');
    await assert.rejects(action({snapshotId: 'input', elementId: input.id, action: 'type', text: 'stale'}), /过期/);
    const spa = await snapshot('spa');
    const spaInput = spa.items.find(item => item.name === 'Search');
    await page.evaluate(() => history.pushState({}, '', '/next'));
    await assert.rejects(action({snapshotId: 'spa', elementId: spaInput.id, action: 'type', text: 'stale'}), /过期/);
    await page.reload();
    await assert.rejects(action({snapshotId: 'snapshot-2', elementId: input.id, action: 'type', text: 'stale'}), /过期/);
    console.log('PASS: live DOM paging, viewport, form controls, rich text, scrolling, privacy, occlusion/identity/disabled guards and snapshot invalidation');
  } finally {
    if (browser) await browser.close();
    await new Promise(resolve => server.close(resolve));
  }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
