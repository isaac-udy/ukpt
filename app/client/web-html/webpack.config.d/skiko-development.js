// A development build skips dead-code elimination, so it keeps Compose UI's skiko-kjs module, which
// reads skikoApi from a skiko.mjs this client does not ship. Production removes it (verifyHtmlBundle).
if (config.mode === 'development') {
    config.externals = [].concat(config.externals || [], { './skiko.mjs': 'var { skikoApi: {} }' });
}
