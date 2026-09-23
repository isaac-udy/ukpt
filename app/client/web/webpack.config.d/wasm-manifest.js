// Emits `wasm-manifest.js` beside the bundle, recording the uncompressed size of every `.wasm`
// asset (the app module and skiko) and their total. index.html counts decoded bytes against that
// total to drive the boot screen's download bar: a compressed response's Content-Length is the
// compressed size, or absent, while the stream the page reads delivers decoded bytes.
//
// `wasm-manifest.js` has a stable name, so a deploy must serve it with `no-cache`: a cached
// manifest measures binaries it has never seen.
config.plugins = config.plugins || [];
config.plugins.push({
    apply: function (compiler) {
        var webpack = compiler.webpack;
        compiler.hooks.thisCompilation.tap('WasmManifest', function (compilation) {
            compilation.hooks.processAssets.tap(
                {
                    name: 'WasmManifest',
                    // Late enough that the content hashes in the asset names are final.
                    stage: webpack.Compilation.PROCESS_ASSETS_STAGE_SUMMARIZE
                },
                function (assets) {
                    var files = {};
                    var total = 0;
                    Object.keys(assets).forEach(function (name) {
                        if (!/\.wasm$/.test(name)) return;
                        files[name] = assets[name].size();
                        total += files[name];
                    });

                    var source = new webpack.sources.RawSource(
                        'self.__WASM_MANIFEST__=' + JSON.stringify({ total: total, files: files }) + ';\n'
                    );
                    // The dev server rebuilds into a compilation that already carries the previous
                    // manifest, and emitting over an existing asset is an error.
                    if (compilation.getAsset('wasm-manifest.js')) {
                        compilation.updateAsset('wasm-manifest.js', source);
                    } else {
                        compilation.emitAsset('wasm-manifest.js', source);
                    }
                }
            );
        });
    }
});
