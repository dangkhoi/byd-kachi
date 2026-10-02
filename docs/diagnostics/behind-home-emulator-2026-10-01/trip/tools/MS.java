import android.content.ComponentName;
import android.content.ContextWrapper;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.IBinder;
import java.util.List;

/**
 * Probe only (scratchpad, NOT app code): đọc phiên nhạc + bắn MỘT lệnh transport bằng MediaController — đúng API
 * Kachi sẽ dùng (MediaBridge), gọi bằng uid 2000 qua app_process. usage: MS list | play <pkg> | uri <pkg> <url> |
 * search <pkg> <q> | pause <pkg>
 */
public class MS {
    static class Ctx extends ContextWrapper {
        Ctx() { super(null); }
        @Override public String getPackageName() { return "com.android.shell"; }
        @Override public String getOpPackageName() { return "com.android.shell"; }
    }

    public static void main(String[] a) throws Throwable {
        IBinder b = (IBinder) Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, "media_session");
        Object sm = Class.forName("android.media.session.ISessionManager$Stub").getMethod("asInterface", IBinder.class).invoke(null, b);
        @SuppressWarnings("unchecked")
        List<MediaSession.Token> toks = (List<MediaSession.Token>) sm.getClass()
            .getMethod("getSessions", ComponentName.class, int.class).invoke(sm, null, 0);
        Ctx c = new Ctx();
        String op = a.length > 0 ? a[0] : "list";
        for (MediaSession.Token t : toks) {
            MediaController mc = new MediaController(c, t);
            PlaybackState st = mc.getPlaybackState();
            MediaMetadata md = mc.getMetadata();
            String title = md == null ? null : md.getString(MediaMetadata.METADATA_KEY_TITLE);
            System.out.println("session pkg=" + mc.getPackageName() + " state=" + (st == null ? "null" : st.getState())
                + " actions=" + (st == null ? -1 : st.getActions()) + " title=" + title);
            if (a.length < 2 || !mc.getPackageName().equals(a[1])) continue;
            MediaController.TransportControls tc = mc.getTransportControls();
            switch (op) {
                case "play": tc.play(); break;
                case "pause": tc.pause(); break;
                case "uri": tc.playFromUri(Uri.parse(a[2]), null); break;
                case "search": tc.playFromSearch(a[2], null); break;
                default: break;
            }
            System.out.println("  -> " + op + " sent to " + a[1]);
        }
    }
}
