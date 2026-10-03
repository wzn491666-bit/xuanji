import {registerHooks} from 'node:module';
import {readFileSync} from 'node:fs';
import {fileURLToPath, pathToFileURL} from 'node:url';
import {injectAndroidEnvironment} from './prompt-transform.mjs';
import {injectAndroidCommands, injectAndroidAgent} from './command-transform.mjs';

const root = fileURLToPath(new URL('../tianshu/node_modules/tianshu-harness', import.meta.url));
const pkg = JSON.parse(readFileSync(`${root}/package.json`, 'utf8'));
if (pkg.version !== '3.27.0') throw new Error(`Xuanji Android context requires Tianshu 3.27.0; found ${pkg.version}`);
const target = pathToFileURL(`${root}/dist/chunk-QL7XQBU6.js`).href;
const environment = readFileSync(new URL('./android-environment.txt', import.meta.url), 'utf8');
// Validate before startup so a renamed upstream entry cannot silently skip the context.
injectAndroidEnvironment(readFileSync(new URL(target), 'utf8'), environment);
const server = pathToFileURL(`${root}/dist/chunk-DXY5P57D.js`).href;
const agent = pathToFileURL(`${root}/dist/serve-agent-RW6S3C3G.js`).href;
const commands = new URL('./command-routes.mjs', import.meta.url).href;
injectAndroidCommands(readFileSync(new URL(server), 'utf8'), commands);
injectAndroidAgent(readFileSync(new URL(agent), 'utf8'));
registerHooks({load(url, context, nextLoad) {
  const result = nextLoad(url, context);
  if (![target, server, agent].includes(url)) return result;
  const source = Buffer.from(result.source).toString('utf8');
  return {...result, source: url === target ? injectAndroidEnvironment(source, environment) :
    url === server ? injectAndroidCommands(source, commands) : injectAndroidAgent(source)};
}});
