package com.dank420girl.core.platform.scheduler;

/**
 * A scheduled task that can be cancelled, independent of the scheduler that runs it.
 */
public interface TaskHandle {
    /** A handle for work that already ran or was never scheduled. */
    TaskHandle NONE = new TaskHandle() {
        @Override
        public void cancel() {
        }

        @Override
        public boolean isCancelled() {
            return true;
        }
    };

    void cancel();

    boolean isCancelled();
}
