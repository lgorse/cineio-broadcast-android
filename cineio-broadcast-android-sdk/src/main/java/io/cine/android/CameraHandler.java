package io.cine.android;

import android.os.Message;

import java.util.logging.Handler;

/**
 * Created by lgorse on 3/23/15.
 */
public abstract class CameraHandler extends android.os.Handler {


    public abstract void invalidateHandler();

    public abstract void handleMessage(Message inputMessage);


}
