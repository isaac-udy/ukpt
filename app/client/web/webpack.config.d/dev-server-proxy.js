// Forwards urpc calls from the dev server to the Ktor server started by `./gradlew :app:server:run`.
// Unary calls are POSTs under /services; streaming calls share one WebSocket at /urpc.
if (config.devServer) {
    config.devServer.proxy = (config.devServer.proxy || []).concat([
        {
            context: ['/services', '/urpc'],
            target: 'http://localhost:8080',
            ws: true
        }
    ]);
}
