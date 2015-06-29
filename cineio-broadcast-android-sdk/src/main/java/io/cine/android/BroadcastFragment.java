package io.cine.android;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.List;

import io.cine.android.streaming.AspectFrameLayout;
import io.cine.android.streaming.AudioEncoderConfig;
import io.cine.android.streaming.CameraSurfaceRenderer;
import io.cine.android.streaming.CameraUtils;
import io.cine.android.streaming.EncodingConfig;
import io.cine.android.streaming.FFmpegMuxer;
import io.cine.android.streaming.MicrophoneEncoder;
import io.cine.android.streaming.Muxer;
import io.cine.android.streaming.ScreenShot;
import io.cine.android.streaming.TextureMovieEncoder;


/**
 * A simple {@link android.app.Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link BroadcastFragment.OnFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {@link BroadcastFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class BroadcastFragment extends android.support.v4.app.Fragment implements  EncodingConfig.EncodingCallback, SurfaceTexture.OnFrameAvailableListener {
    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    private OnFragmentInteractionListener mListener;

    private static final String TAG = "BroadcastFragment";
    private static final boolean VERBOSE = false;
    // this is static so it survives activity restarts
    private static TextureMovieEncoder sVideoEncoder = new TextureMovieEncoder();
    private GLSurfaceView mGLView;
    private CameraSurfaceRenderer mRenderer;
    private Camera mCamera;
    private CameraHandler mCameraHandler;
    private boolean mRecordingEnabled;      // controls button state
    private Muxer mMuxer;
    private AudioEncoderConfig mAudioConfig;
    private MicrophoneEncoder mAudioEncoder;
    private Camera.CameraInfo mCameraInfo;
    private AspectFrameLayout mFrameLayout;
    private EncodingConfig mEncodingConfig;
    private String requestedCamera;
    private View broadcastView;
    Bundle extras;

    private Button recordingButton;

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment BroadcastFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static BroadcastFragment newInstance(String param1, String param2) {
        BroadcastFragment fragment = new BroadcastFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    public BroadcastFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
           // mParam1 = getArguments().getString(ARG_PARAM1);
          //  mParam2 = getArguments().getString(ARG_PARAM2);
            extras = getArguments();
        }


    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        int layout = extras.getInt("LAYOUT", R.layout.fragment_broadcast_layout);
        broadcastView = inflater.inflate(layout, container, false);
        //broadcastView.setKeepScreenOn(true);
        initializeEncodingConfig(extras);
        initializeMuxer();
        initializeAudio();
        initializeVideo();
        initializeGLView();
        Button toggleRecording = (Button) broadcastView.findViewById(R.id.toggleRecording_button);

        toggleRecording.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // show interest in events resulting from ACTION_DOWN
                if(event.getAction()==MotionEvent.ACTION_DOWN)
                    return true;

                // don't handle event unless its ACTION_UP so "doSomething()" only runs once.
                if(event.getAction()!=MotionEvent.ACTION_UP)
                    return false;

                toggleRecordingHandler();
                return true;
            }
        });
        return broadcastView;
    }

    @Override
    public void onStart(){
        super.onStart();
    }

    @Override
    public void onResume(){
        Log.d(TAG, "onResume -- acquiring camera");
        super.onResume();
//        initializeEncodingConfig();
        updateControls();
        openCamera();

        // Set the preview aspect ratio.
        mFrameLayout = (AspectFrameLayout) broadcastView.findViewById(R.id.cameraPreview_afl);

        mGLView.onResume();
        mGLView.queueEvent(new Runnable() {
            @Override
            public void run() {
                mRenderer.setCameraPreviewSize(mEncodingConfig.getLandscapeWidth(), mEncodingConfig.getLandscapeHeight());
            }
        });
        Log.d(TAG, "onResume complete: " + this);

    }


    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
//        activity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
//        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
//        try {
//            mListener = (OnFragmentInteractionListener) activity;
//        } catch (ClassCastException e) {
//            throw new ClassCastException(activity.toString()
//                     + " must implement OnFragmentInteractionListener");
//        }
    }

    @Override
    public void onPause() {
        Log.d(TAG, "onPause -- releasing camera");
        super.onPause();
        if (mRecordingEnabled) {
            toggleRecordingHandler();
        }
        releaseCamera();
        mGLView.queueEvent(new Runnable() {
            @Override
            public void run() {
                // Tell the renderer that it's about to be paused so it can clean up.
                mRenderer.notifyPausing();
            }
        });
        mGLView.onPause();
        getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Log.d(TAG, "onPause complete");
    }

    @Override
    public void onStop(){
        super.onStop();
    }

    @Override
    public void onDestroyView(){
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();
        mCameraHandler.invalidateHandler();     // paranoia
    }



    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    // TODO: Rename method, update argument and hook method into UI event
    public void onButtonPressed(Uri uri) {
        if (mListener != null) {
            mListener.onFragmentInteraction(uri);
        }
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p/>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnFragmentInteractionListener {
        // TODO: Update argument type and name
        public void onFragmentInteraction(Uri uri);
    }

    /**
     * Updates the on-screen controls to reflect the current state of the app.
     */
    private void updateControls() {
        Button recordingButton = (Button) broadcastView.findViewById(R.id.toggleRecording_button);
       // recordingButton.setPressed(mRecordingEnabled);
        if (mRecordingEnabled){
            recordingButton.setBackgroundResource(R.drawable.shutter_button_pressed);
        }else {
            recordingButton.setBackgroundResource(R.drawable.shutter_button_default);
        }
    }

    /**
     * onClick handler for "record" button.
     */
    public void toggleRecordingHandler() {
        mRecordingEnabled = !mRecordingEnabled;
        if (mRecordingEnabled) {
            startRecording();
        } else {
            stopRecording();
        }
    }

    public boolean isRecording(){
        return  mRecordingEnabled;
    }

    private void startRecording() {
        mRecordingEnabled = true;
        mMuxer.prepare(mEncodingConfig);
        mAudioEncoder.startRecording();
        toggleRecording();
    }

    private void stopRecording() {
        mRecordingEnabled = false;
        mAudioEncoder.stopRecording();
        toggleRecording();
    }

    private void toggleRecording() {
        mGLView.queueEvent(new Runnable() {
            @Override
            public void run() {
                // notify the renderer that we want to change the encoder's state
                mRenderer.changeRecordingState(mRecordingEnabled);
            }
        });
        //updateControls();
    }


    private void initializeVideo() {
        // Define a handler that receives camera-control messages from other threads.  All calls
        // to Camera must be made on the same thread.  Note we create this before the renderer
        // thread, so we know the fully-constructed object will be visible.
        mCameraHandler = new CameraHandler(this);
        mRecordingEnabled = sVideoEncoder.isRecording();
    }

    /**
     * Opens a camera, and attempts to establish preview mode at the specified width and height.
     * <p/>
     * Sets mCameraPreviewWidth and mCameraPreviewHeight to the actual width/height of the preview.
     */
    private void openCamera() {
        if (mCamera != null) {
            throw new RuntimeException("camera already initialized");
        }

        Camera.CameraInfo info = new Camera.CameraInfo();

        // Try to find a front-facing camera (e.g. for videoconferencing).
        int numCameras = Camera.getNumberOfCameras();
        int cameraToFind;
        if(requestedCamera != null && requestedCamera.equals("back")){
            cameraToFind = Camera.CameraInfo.CAMERA_FACING_BACK;
        }else{
            cameraToFind = Camera.CameraInfo.CAMERA_FACING_FRONT;
        }
        for (int i = 0; i < numCameras; i++) {
            Camera.getCameraInfo(i, info);
            if (info.facing == cameraToFind) {
                mCameraInfo = info;
                mCamera = Camera.open(i);
                break;
            } else {
                mCameraInfo = info;
            }
        }
        if (mCamera == null) {
            Log.d(TAG, "No front-facing camera found; opening default");
            mCamera = Camera.open();    // opens first back-facing camera
        }
        if (mCamera == null) {
            throw new RuntimeException("Unable to open camera");
        }

        Camera.Parameters parms = mCamera.getParameters();

        int realMachineFps = CameraUtils.chooseFixedPreviewFps(parms, mEncodingConfig.getMachineVideoFps());
        mEncodingConfig.setMachineVideoFps(realMachineFps);
        // Give the camera a hint that we're recording video.  This can have a big
        // impact on frame rate.
        parms.setRecordingHint(true);
        List<Integer> supportedFormats = parms.getSupportedPictureFormats();
        Log.d(TAG, "TOTAL SUPPORTED FORMATS: " + supportedFormats.size());
        for (Integer i : supportedFormats) {
            Log.d(TAG, "SUPPORTED FORMAT: " + i);
        }
        parms.setPreviewFormat(ImageFormat.NV21);

        // leave the frame rate set to default
        mCamera.setParameters(parms);
    }

    /**
     * Stops camera preview, and releases the camera to the system.
     */
    private void releaseCamera() {
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
            Log.d(TAG, "releaseCamera -- done");
        }
    }


    @Override
    public void muxerStatusUpdate(EncodingConfig.MUXER_STATE muxerState) {
        updateStatusText(muxerState);
        handleStreamingUpdate(muxerState);
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        // The SurfaceTexture uses this to signal the availability of a new frame.  The
        // thread that "owns" the external texture associated with the SurfaceTexture (which,
        // by virtue of the context being shared, *should* be either one) needs to call
        // updateTexImage() to latch the buffer.
        //
        // Once the buffer is latched, the GLSurfaceView thread can signal the encoder thread.
        // This feels backward -- we want recording to be prioritized over rendering -- but
        // since recording is only enabled some of the time it's easier to do it this way.
        //
        // Since GLSurfaceView doesn't establish a Looper, this will *probably* execute on
        // the main UI thread.  Fortunately, requestRender() can be called from any thread,
        // so it doesn't really matter.
        if (VERBOSE) Log.d(TAG, "ST onFrameAvailable");
        mGLView.requestRender();
    }

    protected TextureMovieEncoder getsVideoEncoder(){
        return sVideoEncoder;
    }

    private void initializeGLView() {
        // Configure the GLSurfaceView.  This will start the Renderer thread, with an
        // appropriate EGL context.
        mGLView = (GLSurfaceView) broadcastView.findViewById(R.id.fragment_cameraPreview_surfaceView);
        mGLView.setEGLContextClientVersion(2);     // select GLES 2.0
        mRenderer = new CameraSurfaceRenderer(mCameraHandler, sVideoEncoder, mMuxer);
        mGLView.setRenderer(mRenderer);
        mGLView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }



    private void initializeMuxer(){
        mMuxer = new FFmpegMuxer();
    }

    private void initializeAudio() {
        mAudioConfig = AudioEncoderConfig.createDefaultProfile();
        mEncodingConfig.setAudioEncoderConfig(mAudioConfig);
        mAudioEncoder = new MicrophoneEncoder(mMuxer);
    }

    private void initializeEncodingConfig(Bundle extras) {
        String outputString;
        int width = -1;
        int height = -1;
        String orientation = null;

        if (extras != null) {
            outputString = extras.getString("PUBLISH_URL");
            width = extras.getInt("WIDTH", -1);
            height = extras.getInt("HEIGHT", -1);
            orientation = extras.getString("ORIENTATION");
            this.requestedCamera = extras.getString("CAMERA");
        }else{
            outputString = Environment.getExternalStorageDirectory().getAbsolutePath() + "/cineio-recording.mp4";
        }

        if(orientation != null && orientation.equals("landscape")){
            getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
        if(orientation != null && orientation.equals("portrait")){
            getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

        mEncodingConfig = new EncodingConfig(this);
        mEncodingConfig.forceOrientation(orientation);
        if(width != -1){
            Log.v(TAG, "SETTING WIDTH TO: " + width);
            mEncodingConfig.setWidth(width);
        }
        if(height != -1){
            Log.v(TAG, "SETTING HEIGHT TO: " + height);
            mEncodingConfig.setHeight(height);
        }
        mEncodingConfig.setOutput(outputString);
    }

    private void updateStatusText(final EncodingConfig.MUXER_STATE muxerState){
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {

                TextView fileText = (TextView) broadcastView.findViewById(R.id.streamingStatus);
                String statusText;
                switch (muxerState) {
                    case PREPARING:
                        statusText = "Preparing";
                        break;
                    case CONNECTING:
                        statusText = "Connecting";
                        break;
                    case READY:
                        statusText = "Ready";
                        break;
                    case STREAMING:
                        statusText = "Streaming";
                        break;
                    case SHUTDOWN:
                        statusText = "Ready";
                        break;
                    default:
                        statusText = "Unknown";
                        break;
                }
                fileText.setText(statusText);

            }
        });

    }

    private void handleStreamingUpdate(final EncodingConfig.MUXER_STATE muxerState) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {

                updateControls();

                Button recordingButton = (Button) broadcastView.findViewById(R.id.toggleRecording_button);
                RelativeLayout recordingButtonLayout = (RelativeLayout) broadcastView.findViewById(R.id.camera_button_holder);

                switch (muxerState) {
                    case PREPARING:
                        recordingButton.setEnabled(false);
                        recordingButtonLayout.setVisibility(View.GONE);
                        break;

                    case READY:
                        break;

                    case STREAMING:
                        recordingButton.setEnabled(true);
                        recordingButtonLayout.setVisibility(View.VISIBLE);
                        break;

                    case CONNECTING:
                        int currentOrientation = getResources().getConfiguration().orientation;
                        if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                            getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                        } else {
                            getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
                        }

                        recordingButton.setEnabled(false);
                        recordingButtonLayout.setVisibility(View.GONE);
                        break;

                    case SHUTDOWN:
                        recordingButton.setEnabled(true);
                        recordingButtonLayout.setVisibility(View.VISIBLE);
                        getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                        break;
                }
            }
        });
    }

    private int getDeviceRotationDegrees() {
        // fake out the forced orientation
        if (this.mEncodingConfig.hasForcedOrientation()){
            if (this.mEncodingConfig.forcedLandscape()){
                return 90;
            } else {
                return 0;
            }
        }

        switch (getActivity().getWindowManager().getDefaultDisplay().getRotation()) {
            // normal portrait
            case Surface.ROTATION_0:
                return 0;
            // expected landscape
            case Surface.ROTATION_90:
                return 90;
            // upside down portrait
            case Surface.ROTATION_180:
                return 180;
            // "upside down" landscape
            case Surface.ROTATION_270:
                return 270;
        }
        return 0;
    }

    private void setEncoderOrientation() {
        mEncodingConfig.setOrientation(getActivity().getWindowManager().getDefaultDisplay().getRotation());
    }

    private void handleSetCameraOrientation() {
        setEncoderOrientation();
        Log.d(TAG, "handle setting camera orientation");
        int degrees = getDeviceRotationDegrees();
        int result;
        if (mCameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (mCameraInfo.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (mCameraInfo.orientation - degrees + 360) % 360;
        }

        Camera.Parameters parms = mCamera.getParameters();

        Log.d(TAG, "SETTING ASPECT RATIO: " + mEncodingConfig.getAspectRatio());
        mFrameLayout.setAspectRatio(mEncodingConfig.getAspectRatio());

        CameraUtils.choosePreviewSize(parms, mEncodingConfig.getLandscapeWidth(), mEncodingConfig.getLandscapeHeight());

        mCamera.setParameters(parms);

        mGLView.queueEvent(new Runnable() {
            @Override
            public void run() {
                mRenderer.setCameraPreviewSize(mEncodingConfig.getLandscapeWidth(), mEncodingConfig.getLandscapeHeight());
            }
        });

        mCamera.setDisplayOrientation(result);
    }

    /**
     * Connects the SurfaceTexture to the Camera preview output, and starts the preview.
     */
    private void handleSetSurfaceTexture(SurfaceTexture st) {
        st.setOnFrameAvailableListener(this);
        try {
            mCamera.setPreviewTexture(st);
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
        mCamera.startPreview();
    }

    /**
     *
     *Sends a message to the encoder.
     * The encoder is directly connected to the EGLSurface, it accepts messages and it guarantees the
     * EGLContext's state so it's convenient to run the command via the encoder.
     * All the user has to do is define a screenshot and call this method. The app takes care of the rest.
     * Only customization he may want is to handle the frame messages (see below) to react to the status
     * of the frame save.
     */
    protected void saveFrame(ScreenShot screenShot){
        Message message = new Message();
        TextureMovieEncoder textureMovieEncoder = getsVideoEncoder();
        message.what = TextureMovieEncoder.MSG_ENCODER_SAVEFRAME;
        message.obj = screenShot;
        TextureMovieEncoder.EncoderHandler mEncoderHandler = textureMovieEncoder.getHandler();
        if (mEncoderHandler != null) {
            mEncoderHandler.sendMessage(message);
        }else{
            Log.d("TextureMovieEncoder EncoderHandler is null", "in plain English you are probably not recording right now");
        }
    }

    /**
     * takes the saveframe status message (Which is returned via the encoder
     * when capture begins, ends or fails).
     * Then it dispatches to 3 methods based on whether the status is beginning, saved or failed.
     * Useful to set it up this way because the 3 handle methods are overridable - you can use them
     * to send a message to your cameraHandler, to update the UI for instance.
     * @param inputMessage
     */
    private void handleSaveFrameMessage(Message inputMessage) {
        switch(inputMessage.arg1){
            case ScreenShot.SAVING_FRAME:
                handleSavingFrame((String) inputMessage.obj);
                break;
            case ScreenShot.SAVED_FRAME:
                handleSavedFrame((ScreenShot) inputMessage.obj);
                break;
            case ScreenShot.FAILED_FRAME:
                handleFailedFrame((String) inputMessage.obj);
            default:
                break;
        }
    }

    protected void handleFailedFrame(String errorString) {
        Log.i("I FAILED TO SAVE", errorString);
    }

    /**
     * When the frame has been saved the message object will contain
     * the file path of the bitmap
     * @param screenShot
     */
    protected void handleSavedFrame(ScreenShot screenShot) {
        Log.i("I SAVED A FRAME", screenShot.getFilePath());
    }

    protected void handleSavingFrame(String savingString) {
        Log.i("I'M SAVING A FRAME", savingString);
    }

    protected CameraHandler getCameraHandler(){
        return mCameraHandler;
    }

    /**
     * Handles camera operation requests from other threads.  Necessary because the Camera
     * must only be accessed from one thread.
     * <p/>
     * The object is created on the UI thread, and all handlers run there.  Messages are
     * sent from other threads, using sendMessage().
     */
    public static class CameraHandler extends io.cine.android.CameraHandler {
        public static final int MSG_SET_SURFACE_TEXTURE = 0;
        public static final int MSG_SURFACE_CHANGED = 1;
        public static final int MSG_CAPTURE_FRAME = 2;

        // Weak reference to the Activity; only access this from the UI thread.
        private WeakReference<BroadcastFragment> mWeakFragment;

        public CameraHandler(BroadcastFragment fragment) {
            mWeakFragment = new WeakReference<BroadcastFragment>(fragment);
        }

        /**
         * Drop the reference to the activity.  Useful as a paranoid measure to ensure that
         * attempts to access a stale Activity through a handler are caught.
         */
        public void invalidateHandler() {
            mWeakFragment.clear();
        }

        @Override  // runs on UI thread
        public void handleMessage(Message inputMessage) {
            int what = inputMessage.what;
            Log.d(TAG, "CameraHandler [" + this + "]: what=" + what);

            BroadcastFragment fragment = mWeakFragment.get();
            if (fragment == null) {
                Log.w(TAG, "CameraHandler.handleMessage: activity is null");
                return;
            }

            switch (what) {
                case MSG_SURFACE_CHANGED:
                    fragment.handleSetCameraOrientation();
                    break;
                case MSG_SET_SURFACE_TEXTURE:
                    fragment.handleSetSurfaceTexture((SurfaceTexture) inputMessage.obj);
                    break;
                case MSG_CAPTURE_FRAME:
                    fragment.handleSaveFrameMessage(inputMessage);
                    break;
                default:
                    throw new RuntimeException("unknown msg " + what);
            }
        }
    }




}
