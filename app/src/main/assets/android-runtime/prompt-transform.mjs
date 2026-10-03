// Compatibility anchor from Tianshu 3.27.0 (Apache-2.0), src/prompt/static.ts.
export const original = `function buildSystemPrompt(ctx) {
  const base = ctx.audience === "subagent" ? buildSubagentSystemPrompt(BASE_PROMPT, ctx.tools) : BASE_PROMPT;
  const calibration = ctx.modelFamily ? MODEL_CALIBRATIONS[ctx.modelFamily] : void 0;
  if (calibration) return base + "\\n\\n" + calibration;
  return base;
}`;

export function injectAndroidEnvironment(source, environment) {
  if (!environment.trim() || !environment.includes('<xuanji-android-environment>')) throw new Error('Android environment context is invalid');
  if (source.split(original).length !== 2) throw new Error('Tianshu system prompt entry is incompatible with the Xuanji overlay');
  const context = JSON.stringify('\n\n' + environment.trim());
  const injected = original.replace('  const calibration =', `  const androidContext = ${context};\n  const calibration =`)
    .replace('return base + "\\n\\n" + calibration;', 'return base + androidContext + "\\n\\n" + calibration;')
    .replace('  return base;', '  return base + androidContext;');
  return source.replace(original, injected);
}
