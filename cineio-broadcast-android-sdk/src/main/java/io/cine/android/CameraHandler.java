package io.cine.android;

import android.os.Message;

import java.util.logging.Handler;

/**
 * Created by lgorse on 3/23/15.
 */
public abstract class CameraHandler extends android.os.Handler {
    public static final int MSG_SET_SURFACE_TEXTURE = 0;
    public static final int MSG_SURFACE_CHANGED = 1;
    public static final int MSG_CAPTURE_FRAME = 2;


    public abstract void invalidateHandler();

    public abstract void handleMessage(Message inputMessage);


}
