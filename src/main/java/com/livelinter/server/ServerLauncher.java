package com.livelinter.server;

import java.util.concurrent.ExecutionException;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;

public final class ServerLauncher {
    private ServerLauncher() {
    }

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        LiveLinterServer server = new LiveLinterServer();
        Launcher<LanguageClient> launcher = LSPLauncher.createServerLauncher(server, System.in, System.out);
        LanguageClient client = launcher.getRemoteProxy();
        server.connect(client);
        launcher.startListening().get();
    }
}
