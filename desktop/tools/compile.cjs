const fs = require('node:fs');
const path = require('node:path');
const { compile } = require('@vue/compiler-dom');
const root = path.resolve(__dirname, '..');
const source = fs.readFileSync(path.join(root, 'ui/layout.html'), 'utf8');
const { code } = compile(source, { mode: 'function', prefixIdentifiers: true });
fs.writeFileSync(path.join(root, 'ui/generated.js'), 'window.rakotoRender = (function () {\n' + code + '\n})();\n');
fs.copyFileSync(require.resolve('vue/dist/vue.runtime.global.prod.js'), path.join(root, 'ui/vue.js'));
