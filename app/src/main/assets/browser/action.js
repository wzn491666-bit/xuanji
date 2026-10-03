/*
 * Rich-text insertion portions adapted from dsh-browser (MIT).
 * Copyright (c) 2026 Yuxiang Lin.
 * Source: https://github.com/omdsh-dev/dsh-browser
 * Revision: 23895d7292495f665e5676d2f174d022bb3c3605
 * Modified for Xuanji snapshot checks and browser actions.
 * Original license: docs/open-source/license-texts/research/omdsh-dev--dsh-browser/LICENSE
 */
(function(args) {
  const snapshot = window.__xuanjiBrowser;
  if (!snapshot || snapshot.token !== args.snapshotId || snapshot.url !== location.href)
    throw Error('页面快照已过期，请重新读页');
  const entry = args.elementId != null ? snapshot.elements.get(args.elementId) : null;
  const el = entry?.node;
  if (args.action !== 'scroll' || args.elementId != null) {
    if (!el || !el.isConnected || el.matches(':disabled') || el.closest('[hidden],[inert],[aria-hidden="true"],[aria-disabled="true"]'))
      throw Error('目标元素已失效或不可用');
    if (snapshot.signature(el) !== entry.signature) throw Error('目标元素已变化，请重新读页');
    const rect = el.getBoundingClientRect();
    if (!rect.width || !rect.height) throw Error('目标元素不可见');
    for (let parent = el; parent; parent = parent.parentElement) {
      const style = getComputedStyle(parent);
      if (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') throw Error('目标元素不可见');
    }
    el.scrollIntoView({block: 'center', inline: 'nearest', behavior: 'instant'});
    const target = el.getBoundingClientRect();
    const x = Math.max(0, Math.min(innerWidth - 1, (target.left + target.right) / 2));
    const y = Math.max(0, Math.min(innerHeight - 1, (target.top + target.bottom) / 2));
    const hit = document.elementFromPoint(x, y);
    if (!hit || (hit !== el && !el.contains(hit))) throw Error('目标元素被其他界面遮挡，请先处理遮挡内容');
  }
  let scroll;
  if (args.action === 'click') {
    if (el instanceof HTMLInputElement && el.type === 'file') throw Error('文件选择需要用户在浏览器中操作');
    if (el instanceof HTMLAnchorElement) {
      const url = new URL(el.href);
      if (!/^https?:$/.test(url.protocol) || url.username || url.password) throw Error('仅支持不含凭据的网页链接');
    }
    el.click();
  } else if (args.action === 'type') {
    if (typeof args.text !== 'string' || args.text.length > 16384) throw Error('输入文本无效');
    if (snapshot.sensitive(el)) throw Error('此字段需要用户手动输入');
    if (el.isContentEditable) {
      const boundary = el.closest('[contenteditable]');
      if (!boundary || boundary.getAttribute('contenteditable') === 'false') throw Error('此字段不可编辑');
      boundary.focus();
      const range = document.createRange(); range.selectNodeContents(boundary);
      const selection = getSelection(); selection.removeAllRanges(); selection.addRange(range);
      const applied = args.text ? document.execCommand('insertText', false, args.text) : document.execCommand('delete');
      if (!applied) throw Error('网页编辑器未接受输入，请手动编辑');
    } else {
      if (!(el instanceof HTMLInputElement || el instanceof HTMLTextAreaElement) || el.readOnly ||
          (el instanceof HTMLInputElement && !/^(text|search|url|number|date|time|datetime-local|month|week)$/.test(el.type)))
        throw Error('此字段需要用户手动输入');
      const prototype = el instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
      Object.getOwnPropertyDescriptor(prototype, 'value').set.call(el, args.text);
      el.dispatchEvent(new Event('input', {bubbles: true})); el.dispatchEvent(new Event('change', {bubbles: true}));
    }
  } else if (args.action === 'select') {
    if (!(el instanceof HTMLSelectElement) || el.multiple || snapshot.sensitive(el)) throw Error('此选择框需要用户手动操作');
    const matches = Array.from(el.options).filter(option => args.optionBy === 'label' ? option.label === args.value : option.value === args.value);
    if (matches.length !== 1 || matches[0].disabled || matches[0].parentElement?.disabled === true)
      throw Error('选项不存在、不唯一或不可用');
    Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype, 'value').set.call(el, matches[0].value);
    el.dispatchEvent(new Event('input', {bubbles: true})); el.dispatchEvent(new Event('change', {bubbles: true}));
  } else if (args.action === 'check') {
    if (!(el instanceof HTMLInputElement) || !/^(checkbox|radio)$/.test(el.type) || typeof args.checked !== 'boolean')
      throw Error('目标不是复选框或单选框');
    if (el.type === 'radio' && !args.checked) throw Error('单选框不能直接取消，请选择同组其他选项');
    if (el.checked !== args.checked) el.click();
  } else if (args.action === 'submit') {
    const form = el.form || el.closest('form');
    if (!(form instanceof HTMLFormElement)) throw Error('目标没有所属表单');
    form.requestSubmit();
  } else if (args.action === 'scroll') {
    const target = el || document.scrollingElement || document.documentElement;
    const amount = args.amount ?? Math.max(1, Math.floor((el ? target.clientHeight : innerHeight) * 0.8));
    if (!Number.isInteger(amount) || amount < 1 || amount > 10000) throw Error('滚动距离无效');
    const before = {x: target.scrollLeft, y: target.scrollTop};
    if (args.direction === 'top') target.scrollTo({top: 0, behavior: 'instant'});
    else if (args.direction === 'bottom') target.scrollTo({top: target.scrollHeight, behavior: 'instant'});
    else if (args.direction === 'up' || args.direction === 'down') target.scrollBy({top: args.direction === 'up' ? -amount : amount, behavior: 'instant'});
    else if (args.direction === 'left' || args.direction === 'right') target.scrollBy({left: args.direction === 'left' ? -amount : amount, behavior: 'instant'});
    else throw Error('滚动方向无效');
    scroll = {before, after: {x: target.scrollLeft, y: target.scrollTop}};
  } else throw Error('未知网页操作');
  snapshot.token = null;
  return {dispatched: true, action: args.action, elementId: args.elementId ?? null, needsSnapshot: true,
    url: location.href, ...(scroll ? {scroll} : {})};
})
