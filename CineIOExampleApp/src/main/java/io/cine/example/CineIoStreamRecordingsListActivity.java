package io.cine.example;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

import io.cine.android.CineIoClient;
import io.cine.android.api.Stream;
import io.cine.android.api.StreamRecording;
import io.cine.android.api.StreamRecordingsResponseHandler;
import io.cine.example.R;

public class CineIoStreamRecordingsListActivity extends Activity implements AdapterView.OnItemClickListener {

    private final static String TAG = "CineIoStreamRecordingsListActivity";

    private CineIoClient mClient;
    private Stream stream;
    private ArrayList<StreamRecording> mStreamRecordings;
    private ListView recordingListView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cine_io_stream_recordings_list);
        Bundle extras = getIntent().getExtras();
        mClient = new CineIoClient(extras.getString("SECRET_KEY"));
        try {
            stream = new Stream(new JSONObject(extras.getString("STREAM_DATA")));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        recordingListView = (ListView) findViewById(R.id.streamRecordings);

        mClient.getStreamRecordings(stream.getId(), new StreamRecordingsResponseHandler() {
            @Override
            public void onSuccess(ArrayList<StreamRecording> streamRecordings) {
                setStreamRecordings(streamRecordings);
            }
        });


    }

    private void setStreamRecordings(ArrayList<StreamRecording> streamRecordings) {
        mStreamRecordings = streamRecordings;
        final Activity me = this;
        // This is the array adapter, it takes the context of the activity as a
        // first parameter, the type of list view as a second parameter and your
        // array as a third parameter.
        ArrayAdapter<StreamRecording> arrayAdapter = new ArrayAdapter<StreamRecording>(
                this,
                android.R.layout.simple_list_item_1,
                streamRecordings);

        recordingListView.setAdapter(arrayAdapter);
        recordingListView.setOnItemClickListener(this);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.cine_io_stream_recordings_list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        StreamRecording recording = mStreamRecordings.get(i);
        mClient.playRecording(recording, this);
    }
}
