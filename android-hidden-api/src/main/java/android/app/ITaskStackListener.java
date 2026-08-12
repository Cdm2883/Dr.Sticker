package android.app;

import android.os.Binder;

public interface ITaskStackListener {
    abstract class Stub extends Binder implements ITaskStackListener {
    }

    @SuppressWarnings("unused")
    void onTaskStackChanged();

    @SuppressWarnings("unused")
    void onTaskMovedToFront(int taskId);

    @SuppressWarnings("unused")
    void onTaskMovedToFront(ActivityManager.RunningTaskInfo taskInfo);
}
