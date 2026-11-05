// Generates the quotes and tips for each platform from shared/motivation.json.
// Run after editing the JSON: node scripts/generate-motivation.mjs
// Outputs:
//   extension/motivation.js                              (classic script, sets globalThis.LockMotivation)
//   core/src/main/kotlin/app/lockin/core/Motivation.kt   (object Motivation)
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.join(path.dirname(fileURLToPath(import.meta.url)), '..');
const data = JSON.parse(readFileSync(path.join(root, 'shared', 'motivation.json'), 'utf8'));

function validate(list, name) {
  if (!Array.isArray(list) || list.length < 10) throw new Error(`${name} needs at least 10 entries`);
  const seen = new Set();
  for (const item of list) {
    if (typeof item !== 'string' || item.trim() !== item || item.length === 0) {
      throw new Error(`${name} has an empty or padded entry: ${JSON.stringify(item)}`);
    }
    if (seen.has(item)) throw new Error(`${name} has a duplicate: ${item}`);
    seen.add(item);
  }
}
validate(data.quotes, 'quotes');
validate(data.tips, 'tips');

function kotlinString(text) {
  return '"' + text.replace(/\\/g, '\\\\').replace(/"/g, '\\"').replace(/\$/g, '\\$') + '"';
}

const js = `// GENERATED from shared/motivation.json by scripts/generate-motivation.mjs. Do not edit by hand.
(function (global) {
  'use strict';
  global.LockMotivation = {
    QUOTES: ${JSON.stringify(data.quotes, null, 2).split('\n').join('\n    ')},
    TIPS: ${JSON.stringify(data.tips, null, 2).split('\n').join('\n    ')}
  };
})(typeof globalThis !== 'undefined' ? globalThis : this);
`;

const kt = `// GENERATED from shared/motivation.json by scripts/generate-motivation.mjs. Do not edit by hand.
package app.lockin.core

object Motivation {
    val quotes: List<String> = listOf(
${data.quotes.map((q) => '        ' + kotlinString(q) + ',').join('\n').replace(/,$/, '')}
    )

    val tips: List<String> = listOf(
${data.tips.map((t) => '        ' + kotlinString(t) + ',').join('\n').replace(/,$/, '')}
    )
}
`;

writeFileSync(path.join(root, 'extension', 'motivation.js'), js);
mkdirSync(path.join(root, 'core', 'src', 'main', 'kotlin', 'app', 'lockin', 'core'), { recursive: true });
writeFileSync(path.join(root, 'core', 'src', 'main', 'kotlin', 'app', 'lockin', 'core', 'Motivation.kt'), kt);
console.log(`wrote ${data.quotes.length} quotes and ${data.tips.length} tips`);
