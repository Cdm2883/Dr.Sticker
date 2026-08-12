package vip.cdms.drsticker.services.shizuku;

interface IShizukuConditionCallback {
    oneway void onTopTaskChanged(String packageName, String activityName);
}
