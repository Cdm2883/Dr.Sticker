package android.app;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;

import java.util.List;

public interface IActivityManager extends IInterface {
    abstract class Stub extends Binder implements IActivityManager {
        public static IActivityManager asInterface(@SuppressWarnings("unused") IBinder obj) {
            throw new RuntimeException();
        }
    }

    List<ActivityManager.RunningTaskInfo> getTasks(int maxNum, int flags);

    List<ActivityManager.RunningTaskInfo> getTasks(int maxNum);

    void registerTaskStackListener(ITaskStackListener listener);

    void unregisterTaskStackListener(ITaskStackListener listener);
}
