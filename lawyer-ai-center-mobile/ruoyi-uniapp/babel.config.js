// HBuilderX and CLI both need to transpile optional chaining for webpack 4.
const path = require('path')
const roots = [process.env.VUE_CLI_CONTEXT, process.env.UNI_HBUILDERX_PLUGINS && path.join(process.env.UNI_HBUILDERX_PLUGINS, 'uniapp-cli'), __dirname].filter(Boolean)
const resolve = name => require.resolve(name, { paths: roots })
module.exports = {
    presets: [[resolve('@vue/app'), { modules: 'commonjs', useBuiltIns: process.env.UNI_PLATFORM === 'h5' ? 'usage' : 'entry' }]],
    plugins: [resolve('@babel/plugin-proposal-optional-chaining'), resolve('@babel/plugin-proposal-nullish-coalescing-operator')]
}
