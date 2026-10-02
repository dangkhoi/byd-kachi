import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import java.lang.reflect.Method;

/** Probe only (scratchpad, not app code): start an activity with a chosen ActivityOptions mode. */
public class LB {
    public static void main(String[] a) throws Throwable {
        String mode = a[0];                       // plain | behind | avoid
        ComponentName cn = ComponentName.unflattenFromString(a[1]);
        int display = a.length > 2 ? Integer.parseInt(a[2]) : -1;
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(cn);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ActivityOptions o;
        if (mode.equals("behind")) {
            o = ActivityOptions.makeTaskLaunchBehind();
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        } else {
            o = ActivityOptions.makeBasic();
        }
        if (display >= 0) o.setLaunchDisplayId(display);
        Bundle b = o.toBundle();
        if (mode.equals("avoid")) b.putBoolean("android.activity.avoidMoveToFront", true);
        Object am = Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null);
        Method m = null;
        for (Method x : am.getClass().getMethods()) {
            if (x.getName().equals("startActivityAsUser") && x.getParameterTypes().length == 11) m = x;
        }
        Object r = m.invoke(am, null, "com.android.shell", i, null, null, null, 0, 0, null, b, 0);
        System.out.println("mode=" + mode + " flags=0x" + Integer.toHexString(i.getFlags()) + " result=" + r);
    }
}
