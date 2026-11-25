package com.gtnewhorizons.angelica.glsm;

import java.util.concurrent.CompletableFuture;

import org.junit.platform.launcher.LauncherInterceptor;
import top.outlands.foundation.boot.MainThreadExecutor;

/** Runs integration tests on Foundation's SDL thread, including loader-owned display initialization. */
public final class SdlTestLauncherInterceptor implements LauncherInterceptor {
    @Override
    public <T> T intercept(Invocation<T> invocation) {
        if (MainThreadExecutor.isMainThread()) {
            return invocation.proceed();
        }
        var result = new CompletableFuture<T>();
        MainThreadExecutor.start("GL integration test coordinator", () -> {
            try {
                result.complete(MainThreadExecutor.submit(invocation::proceed));
            } catch (RuntimeException | Error failure) {
                result.completeExceptionally(failure);
            }
        });
        return result.join();
    }

    @Override
    public void close() {
        // Foundation stops the event loop before intercept returns.
    }
}
