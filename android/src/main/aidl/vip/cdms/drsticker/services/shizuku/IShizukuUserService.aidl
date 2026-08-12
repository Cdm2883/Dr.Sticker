package vip.cdms.drsticker.services.shizuku;

interface IShizukuUserService {
    boolean registerConditionListener(IBinder callback);
    void unregisterConditionListener();

    boolean swipe(int startX, int startY, int endX, int endY, long durationMillis);
}
