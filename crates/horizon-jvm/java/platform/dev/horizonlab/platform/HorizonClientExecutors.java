package dev.horizonlab.platform;

import java.lang.reflect.Modifier;
import java.util.concurrent.Executor;

/** Executor fields whose constructor lambdas the allocated client did not run. */
final class HorizonClientExecutors {
    private HorizonClientExecutors() {}

    static void initialize(Executor client) throws ReflectiveOperationException {
        for (Class<?> owner = client.getClass(); owner != null; owner = owner.getSuperclass()) {
            for (var field : owner.getDeclaredFields()) {
                if (field.getType() != Executor.class || Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                // Keep a mod's own executor. Missing ones use the client's real task queue,
                // so work from a background thread still waits for the client thread.
                if (field.get(client) == null) field.set(client, client);
            }
        }
    }
}
