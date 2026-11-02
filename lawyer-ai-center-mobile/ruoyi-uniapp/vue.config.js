// Compile uView and application optional chaining with HBuilderX's webpack 4.
const path = require('path')
const roots = [process.env.VUE_CLI_CONTEXT, process.env.UNI_HBUILDERX_PLUGINS && path.join(process.env.UNI_HBUILDERX_PLUGINS, 'uniapp-cli'), __dirname].filter(Boolean)
module.exports = {
    transpileDependencies: ['uview-ui'],
    chainWebpack(config) {
        config.module.rule('js').uses.delete('cache-loader')
        config.module.rule('js').use('babel-loader').tap(options => ({
            ...options,
            plugins: [...((options || {}).plugins || []),
                require.resolve('@babel/plugin-proposal-optional-chaining', { paths: roots }),
                require.resolve('@babel/plugin-proposal-nullish-coalescing-operator', { paths: roots })]
        }))
    }
}
