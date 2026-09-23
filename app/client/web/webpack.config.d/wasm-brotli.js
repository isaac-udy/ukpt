// Writes a brotli-compressed `<name>.wasm.br` beside every `.wasm` asset of a production build.
// Object stores and CDNs that compress on the fly leave `application/wasm` uncompressed, and the
// wasm binaries are most of what a visitor downloads. The deploy uploads the `.br` bytes under the
// original `.wasm` name with `Content-Encoding: br` and `Content-Type: application/wasm`; the page
// still requests `<name>.wasm`. See the `ukpt-web-deploy` skill.
if (config.mode === 'production') {
    var zlib = require('zlib');

    config.plugins = config.plugins || [];
    config.plugins.push({
        apply: function (compiler) {
            var webpack = compiler.webpack;
            compiler.hooks.thisCompilation.tap('WasmBrotli', function (compilation) {
                var logger = compilation.getLogger('WasmBrotli');
                compilation.hooks.processAssets.tap(
                    {
                        name: 'WasmBrotli',
                        stage: webpack.Compilation.PROCESS_ASSETS_STAGE_OPTIMIZE_TRANSFER
                    },
                    function (assets) {
                        Object.keys(assets).forEach(function (name) {
                            if (!/\.wasm$/.test(name)) return;
                            var raw = assets[name].buffer();
                            var compressed = zlib.brotliCompressSync(raw, {
                                params: {
                                    [zlib.constants.BROTLI_PARAM_QUALITY]: zlib.constants.BROTLI_MAX_QUALITY,
                                    [zlib.constants.BROTLI_PARAM_SIZE_HINT]: raw.length
                                }
                            });
                            compilation.emitAsset(name + '.br', new webpack.sources.RawSource(compressed));

                            var mb = function (n) { return (n / 1024 / 1024).toFixed(2); };
                            logger.info(name + ': ' + mb(raw.length) + ' MB -> ' + mb(compressed.length) +
                                ' MB (' + (compressed.length / raw.length * 100).toFixed(1) + '%)');
                        });
                    }
                );
            });
        }
    });
}
