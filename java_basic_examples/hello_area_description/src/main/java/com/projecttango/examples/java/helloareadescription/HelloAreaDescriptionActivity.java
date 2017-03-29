/*
 * Copyright 2014 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.projecttango.examples.java.helloareadescription;

import android.app.Activity;
import android.app.FragmentManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.Tango.OnTangoUpdateListener;
import com.google.atap.tangoservice.TangoAreaDescriptionMetaData;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoEvent;
import com.google.atap.tangoservice.TangoInvalidException;
import com.google.atap.tangoservice.TangoOutOfDateException;
import com.google.atap.tangoservice.TangoPointCloudData;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoXyzIjData;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.projecttango.examples.java.helloareadescription.Helper.getEulerAngleZ;
import static com.projecttango.examples.java.helloareadescription.Helper.roundToNearestHalf;
import static java.lang.String.valueOf;

/**
 * Main Activity class for the Area Description example. Handles the connection to the Tango service
 * and propagation of Tango pose data to Layout view.
 */
public class HelloAreaDescriptionActivity extends Activity implements
        SetAdfNameDialog.CallbackListener,
        SaveAdfTask.SaveAdfListener, TextToSpeech.OnInitListener {

    private static final String TAG = HelloAreaDescriptionActivity.class.getSimpleName();
    private static final int SECS_TO_MILLISECS = 1000;
    private Tango mTango;
    private TangoConfig mConfig;

    private TextView mUuidTextView;
    private TextView mRelocalizationTextView;
    private TextView mCurrentLocationTextView;
    private TextView mZRotationTextView;
    private TextView mNextRotationTextView;
    private TextView mNextWaypointTextView;
    private TextView mDestinationTextView;
    private TextView mReachedDestinationTextView;
    private TextView mLandMarkTextView;
    private TextView mJSONTextView;

    private Button mSaveAdfButton;
    private Button mSaveLandButton;
    private EditText mLandmarkName;
    private Button mChooseLandButton;
    private EditText mDestLandmark;

    private float[] translation;
    private float[] orientation;

    private ArrayList<TangoPoseData> landmarkList = new ArrayList<TangoPoseData>();
    private ArrayList<String> landmarkName = new ArrayList<String>();
    private TangoPoseData currentPose;
    private ArrayList<String> savedWaypointNames = new ArrayList<String>();

    private double mPreviousPoseTimeStamp;
    private double mTimeToNextUpdate = UPDATE_INTERVAL_MS;

    private boolean mIsRelocalized;
    private boolean mIsLearningMode;
    private boolean mIsConstantSpaceRelocalize;

    private String mPositionString;
    private String mZRotationString;
    private float[] mDestinationTranslation = new float[3];

    // Long-running task to save the ADF.
    private SaveAdfTask mSaveAdfTask;

    private static final double UPDATE_INTERVAL_MS = 100.0;

    private final Object mSharedLock = new Object();

    private String jsonFileString;
    private String chosenLandmark;

    private Set coordinateSet = new HashSet<Node>();
    private float maxX = 0f;
    private float minX = 0f;
    private float maxY = 0f;
    private float minY = 0f;

    private List<Node> squashedPath;
    private float[] rotationsArray;
    private boolean mIsNavigatingMode = false;
    private int waypointIterator;

    private int chosenIndex = 0;

    private TextToSpeech mTts;

    private MyBroadcastReceiver myReceiver;
    private String selectedUUID;

    private ListView listView;

    private float mRotationDiff;

    private final float granularity = 0.5f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_area_learning);
        Intent intent = getIntent();

        mIsLearningMode = intent.getBooleanExtra(AdfUuidListViewActivity.USE_AREA_LEARNING, false);
        mIsConstantSpaceRelocalize = intent.getBooleanExtra(AdfUuidListViewActivity.LOAD_ADF, false);
        mTts = new TextToSpeech(HelloAreaDescriptionActivity.this, this);

        selectedUUID = getIntent().getExtras().getString("uuidName");
        String toastMessage = "Selected map: " + selectedUUID + "loaded";
        Toast.makeText(this, toastMessage, Toast.LENGTH_LONG).show();

        listView = (ListView) findViewById(R.id.list);
    }

    @Override
    protected void onResume() {
        super.onResume();

        myReceiver = new MyBroadcastReceiver();
        final IntentFilter intentFilter = new IntentFilter("YourAction");
        LocalBroadcastManager.getInstance(this).registerReceiver(myReceiver, intentFilter);

        // Initialize Tango Service as a normal Android Service, since we call mTango.disconnect()
        // in onPause, this will unbind Tango Service, so every time when onResume gets called, we
        // should create a new Tango object.
        mTango = new Tango(HelloAreaDescriptionActivity.this, new Runnable() {
            // Pass in a Runnable to be called from UI thread when Tango is ready, this Runnable
            // will be running on a new thread.
            // When Tango is ready, we can call Tango functions safely here only when there is no UI
            // thread changes involved.
            @Override
            public void run() {
                synchronized (HelloAreaDescriptionActivity.this) {
                    try {
                        mConfig = setTangoConfig(
                                mTango, mIsLearningMode, mIsConstantSpaceRelocalize);
                        mTango.connect(mConfig);
                        startupTango();

                    } catch (TangoOutOfDateException e) {
                        Log.e(TAG, getString(R.string.tango_out_of_date_exception), e);
                    } catch (TangoErrorException e) {
                        Log.e(TAG, getString(R.string.tango_error), e);
                    } catch (TangoInvalidException e) {
                        Log.e(TAG, getString(R.string.tango_invalid), e);
                    } catch (SecurityException e) {
                        // Area Learning permissions are required. If they are not available,
                        // SecurityException is thrown.
                        Log.e(TAG, getString(R.string.no_permissions), e);
                    }
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (HelloAreaDescriptionActivity.this) {
                            setupTextViewsAndButtons(mTango, mIsLearningMode,
                                    mIsConstantSpaceRelocalize);
                        }
                    }
                });
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Clear the relocalization state: we don't know where the device will be since our app
        // will be paused.
        mIsRelocalized = false;
        synchronized (this) {
            try {
                mTango.disconnect();
            } catch (TangoErrorException e) {
                Log.e(TAG, getString(R.string.tango_error), e);
            }
        }

        if (myReceiver != null)
            LocalBroadcastManager.getInstance(this).unregisterReceiver(myReceiver);
        myReceiver = null;
    }

//    @Override
//    protected void onDestroy() {
//        if (mIsNavigatingMode) {
//            mIsNavigatingMode = false;
//            storeValuesToJSON(selectedUUID);
//        }
//
//        super.onDestroy();
//    }

    /**
     * Sets Texts views to display statistics of Poses being received. This also sets the buttons
     * used in the UI. Please note that this needs to be called after TangoService and Config
     * objects are initialized since we use them for the SDK related stuff like version number
     * etc.
     */
    private void setupTextViewsAndButtons(Tango tango, boolean isLearningMode, boolean isLoadAdf) {
        mSaveAdfButton = (Button) findViewById(R.id.save_adf_button);
        mUuidTextView = (TextView) findViewById(R.id.adf_uuid_textview);
        mRelocalizationTextView = (TextView) findViewById(R.id.relocalization_textview);
        mDestinationTextView = (TextView) findViewById(R.id.destination_textview);
        mCurrentLocationTextView = (TextView) findViewById(R.id.current_location_textview);
        mZRotationTextView = (TextView) findViewById(R.id.z_rotation_textview);
        mNextRotationTextView = (TextView) findViewById(R.id.next_rotation_textview);
        mNextWaypointTextView = (TextView) findViewById(R.id.next_waypoint_textview);
        mReachedDestinationTextView = (TextView) findViewById(R.id.reached_destination_textview);
        mLandMarkTextView = (TextView) findViewById(R.id.landMarkTextView);
        mSaveLandButton = (Button) findViewById(R.id.land_button);
        mLandmarkName = (EditText) findViewById(R.id.landmarkName);
        mDestLandmark = (EditText) findViewById(R.id.destLandmark);
        mChooseLandButton = (Button) findViewById(R.id.chooseLandButton);
        mJSONTextView = (TextView) findViewById(R.id.JSONView);

        mChooseLandButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                chosenLandmark = mDestLandmark.getText().toString();
                loadWaypoint(mIsConstantSpaceRelocalize);
            }
        });

        mSaveLandButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                landmarkList.add(currentPose);
                Log.i("landmarkList.len =  ", valueOf(landmarkList.size()));

                Context context = getApplicationContext();
                CharSequence text = "Landmark saved";
                int duration = Toast.LENGTH_SHORT;

                Toast toast = Toast.makeText(context, text, duration);
                toast.show();

                String name = mLandmarkName.getText().toString();
                landmarkName.add(name);
            }
        });

        if (isLoadAdf) {
            mSaveLandButton.setVisibility(View.GONE);
            mLandmarkName.setVisibility(View.GONE);

            if (isLearningMode) {
                // Disable save ADF button until Tango relocalizes to the current ADF.
                mSaveAdfButton.setEnabled(false);
            } else {
                // Hide to save ADF button if learning mode is off.
                mSaveAdfButton.setVisibility(View.GONE);
            }

            ArrayList<String> fullUuidList;
            // Returns a list of ADFs with their UUIDs
            fullUuidList = tango.listAreaDescriptions();
            if (fullUuidList.size() == 0) {
                mUuidTextView.setText(R.string.no_uuid);
            } else {
                mUuidTextView.setText(getString(R.string.number_of_adfs) + fullUuidList.size()
                        + "Selected ADF is "
                        + selectedUUID);

                jsonFileString = "empty file";
                jsonFileString = readFile(selectedUUID);
                fillSavedNamesList();
            }

            loadValuesFromJson();
            printMatrix();
        }
    }

    /**
     * Sets up the tango configuration object. Make sure mTango object is initialized before
     * making this call.
     */
    private TangoConfig setTangoConfig(Tango tango, boolean isLearningMode, boolean isLoadAdf) {
        // Use default configuration for Tango Service.
        TangoConfig config = tango.getConfig(TangoConfig.CONFIG_TYPE_DEFAULT);
        // Check if learning mode
        if (isLearningMode) {
            // Set learning mode to config.
            config.putBoolean(TangoConfig.KEY_BOOLEAN_LEARNINGMODE, true);

        }
        // Check for Load ADF/Constant Space relocalization mode.
        if (isLoadAdf) {
            ArrayList<String> fullUuidList;
            // Returns a list of ADFs with their UUIDs.
            fullUuidList = tango.listAreaDescriptions();
            // Load the latest ADF if ADFs are found.
            if (fullUuidList.size() > 0) {
                if (selectedUUID == null) {
                    config.putString(TangoConfig.KEY_STRING_AREADESCRIPTION,
                            fullUuidList.get(fullUuidList.size() - 1));
                } else {
                    config.putString(TangoConfig.KEY_STRING_AREADESCRIPTION,
                            selectedUUID);
                }
            }
        }
        return config;
    }

    /**
     * Set up the callback listeners for the Tango service and obtain other parameters required
     * after Tango connection.
     */
    private void startupTango() {
        // Set Tango Listeners for Poses Device wrt Start of Service, Device wrt
        // ADF and Start of Service wrt ADF.
        ArrayList<TangoCoordinateFramePair> framePairs = new ArrayList<TangoCoordinateFramePair>();
        framePairs.add(new TangoCoordinateFramePair(
                TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                TangoPoseData.COORDINATE_FRAME_DEVICE));
        framePairs.add(new TangoCoordinateFramePair(
                TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                TangoPoseData.COORDINATE_FRAME_DEVICE));
        framePairs.add(new TangoCoordinateFramePair(
                TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE));

        mTango.connectListener(framePairs, new OnTangoUpdateListener() {

            @Override
            public void onPoseAvailable(final TangoPoseData pose) {
                // Make sure to have atomic access to Tango Data so that UI loop doesn't interfere
                // while Pose call back is updating the data.
                synchronized (mSharedLock) {
                    currentPose = pose;

                    // Check for Device wrt ADF pose, Device wrt Start of Service pose, Start of
                    // Service wrt ADF pose (This pose determines if the device is relocalized or
                    // not)

                    if (pose.baseFrame == TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION
                            && pose.targetFrame == TangoPoseData
                            .COORDINATE_FRAME_DEVICE) {
                        if (pose.statusCode == TangoPoseData.POSE_VALID) {

                            mIsRelocalized = true;

                            translation = pose.getTranslationAsFloats();
                            orientation = pose.getRotationAsFloats();

                            mPositionString = "X:" + translation[0] + ", Y:" + translation[1] + ", Z:" + translation[2];
                            mZRotationString = String.valueOf(getEulerAngleZ(orientation));

                            updateCoordinates();
                        } else {
                            mIsRelocalized = false;
                        }
                    }
                }

                // get current pose


                final double deltaTime = (pose.timestamp - mPreviousPoseTimeStamp) *
                        SECS_TO_MILLISECS;
                mPreviousPoseTimeStamp = pose.timestamp;
                mTimeToNextUpdate -= deltaTime;


                if (mTimeToNextUpdate < 0.0) {
                    mTimeToNextUpdate = UPDATE_INTERVAL_MS;

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            synchronized (mSharedLock) {
                                mSaveAdfButton.setEnabled(mIsRelocalized);
                                mRelocalizationTextView.setText(mIsRelocalized ?
                                        getString(R.string.localized) :
                                        getString(R.string.not_localized));

                                if (mIsRelocalized) {
                                    mJSONTextView.setText(jsonFileString);

                                    mCurrentLocationTextView.setText(mPositionString);
                                    mZRotationTextView.setText(mZRotationString);

                                    float lowerBound_X = mDestinationTranslation[0] - 0.15f;
                                    float lowerBound_Y = mDestinationTranslation[1] - 0.15f;
                                    float lowerBound_Z = mDestinationTranslation[2] - 0.15f;

                                    float upperBound_X = mDestinationTranslation[0] + 0.15f;
                                    float upperBound_Y = mDestinationTranslation[1] + 0.15f;
                                    float upperBound_Z = mDestinationTranslation[2] + 0.15f;

                                    mReachedDestinationTextView.setText(String.valueOf((lowerBound_X <= translation[0] && translation[0] <= upperBound_X) &&
                                            (lowerBound_Y <= translation[1] && translation[1] <= upperBound_Y) &&
                                            (lowerBound_Z <= translation[2] && translation[2] <= upperBound_Z)));

                                    if (mIsNavigatingMode) {
                                        Node nextWaypoint = squashedPath.get(waypointIterator);
                                        if ((roundToNearestHalf(translation[0]) == nextWaypoint.getX())
                                                && (roundToNearestHalf(translation[1]) == nextWaypoint.getY())) {
                                            // Made it to the nextWaypoint
                                            updateWaypoint();
                                        }
                                    }
                                }
                            }
                        }
                    });
                }
            }

            @Override
            public void onXyzIjAvailable(TangoXyzIjData xyzIj) {
                // We are not using onXyzIjAvailable for this app.
            }

            @Override
            public void onPointCloudAvailable(TangoPointCloudData xyzij) {
                // We are not using onPointCloudAvailable for this app.
            }

            @Override
            public void onTangoEvent(final TangoEvent event) {
                // Ignoring TangoEvents.
            }

            @Override
            public void onFrameAvailable(int cameraId) {
                // We are not using onFrameAvailable for this application.
            }
        });
    }

    private void updateCoordinates() {
        float x = roundToNearestHalf(translation[0]);
        float y = roundToNearestHalf(translation[1]);

        // Set the min and max to find the length of grid
        if (x > maxX) {
            maxX = x;
        }
        if (x < minX) {
            minX = x;
        }
        if (y > maxY) {
            maxY = y;
        }
        if (y < minY) {
            minY = y;
        }

        Node currentNode = new Node(x, y);

        coordinateSet.add(currentNode);
    }

    private void processArduinoValues(String arduinoSent) {

        Log.d("select", "processCalled");

        Log.d("arduinosent", arduinoSent);

        if (arduinoSent.equals("1")) {
            // Select is clicked
            Log.d("selectclick", "1 clicked");
            selectButtonClicked();
        }
        if (arduinoSent.equals("2")) {
            // Up is clicked
            upButtonClicked();
        }
        if (arduinoSent.equals("3")) {
            // Down is clicked
            downButtonClicked();
        }
        if (arduinoSent.equals("Rotation finished")) {
            ConvertTextToSpeech(arduinoSent + ", walk forward");
        }
    }


    private void fillSavedNamesList() {
        try {
            Log.d("Fill", "fill saved list ");
            JSONObject jsonObj = new JSONObject(jsonFileString);

            mJSONTextView.setText(String.valueOf(jsonObj));
            Log.i("JSON", String.valueOf(jsonObj));

            for (int i = 0; i < jsonObj.length(); i++) {
                String key = jsonObj.getString(String.valueOf(i));
                savedWaypointNames.add(key);
                Log.d("savedwaypoints", key);
            }

            ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                    android.R.layout.simple_list_item_1, android.R.id.text1, savedWaypointNames);
            listView.setAdapter(adapter);

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }


    private void selectButtonClicked() {
        if (mIsNavigatingMode) {
            Toast t;
            if (mRotationDiff > 0) {
                // rotate counterclockwise or turn left
                t = Toast.makeText(getApplicationContext(), "Rotate " + String.valueOf(mRotationDiff) + "(Counter-clockwise)", Toast.LENGTH_LONG);
                ConvertTextToSpeech("Rotate left " + (int) mRotationDiff + "degrees");

            } else {
                // rotate clockwise or turn right
                t = Toast.makeText(getApplicationContext(), "Rotate " + String.valueOf(mRotationDiff) + "(Clockwise)", Toast.LENGTH_LONG);
                ConvertTextToSpeech("Rotate right " + (int) Math.abs(mRotationDiff) + "degrees");
            }
            t.show();
        } else {
            if (savedWaypointNames.size() != 0) {
                Log.d("Select again", "Select clicked after");
                chosenLandmark = savedWaypointNames.get(chosenIndex);
                String speakToUser = chosenLandmark + " " + "Selected";
                ConvertTextToSpeech(speakToUser);
                loadWaypoint(mIsConstantSpaceRelocalize);

            }
            if (savedWaypointNames.size() == 0) {
                Log.d("select", "no waypoints");
            }
        }
    }

    private void upButtonClicked() {
        if (savedWaypointNames.size() != 0) {
            if (chosenIndex == 0) {
                chosenIndex = savedWaypointNames.size() - 1;
            } else {
                chosenIndex--;
            }

            String speakToUser = "Waypoint" + savedWaypointNames.get(chosenIndex);
            ConvertTextToSpeech(speakToUser);
        }
    }

    private void downButtonClicked() {
        if (savedWaypointNames.size() != 0) {
            if (chosenIndex == savedWaypointNames.size()) {
                chosenIndex = 0;
            } else {
                chosenIndex++;
            }


            String speakToUser = "Waypoint" + savedWaypointNames.get(chosenIndex);
            ConvertTextToSpeech(speakToUser);
        }
    }


    private String readFile(String adfId) {
        StringBuilder finalString = new StringBuilder();

        try {
            FileInputStream inStream = this.openFileInput(adfId);
            InputStreamReader inputStreamReader = new InputStreamReader(inStream);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

            String oneLine;

            while ((oneLine = bufferedReader.readLine()) != null) {
                finalString.append(oneLine);
            }

            bufferedReader.close();
            inStream.close();
            inputStreamReader.close();

        } catch (IOException e) {
            e.printStackTrace();


        }

        return finalString.toString();
    }

    private void saveLandmarks(String id) {

        // Save landmark coords (x,y,z) + waypoint name

        Log.d("Checking", "save Landmarks to file is called");

        int size = landmarkList.size();

        JSONObject jsonObj = new JSONObject();

        for (int i = 0; i < size; i++) {
            String xName = (landmarkName.get(i) + "_x");
            String yName = (landmarkName.get(i) + "_y");
            String zName = (landmarkName.get(i) + "_z");

            float translationStored[] = landmarkList.get(i).getTranslationAsFloats();
            String xPose = Float.toString(translationStored[0]);
            String yPose = Float.toString(translationStored[1]);
            String zPose = Float.toString(translationStored[2]);


            try {
                jsonObj.put(xName, xPose);
                jsonObj.put(yName, yPose);
                jsonObj.put(zName, zPose);
                jsonObj.put(String.valueOf(i), landmarkName.get(i));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        // Create a file in the Internal Storage
        String content = jsonObj.toString();
        Log.d("content", content);
        Log.d("filename", id);

        try {
            FileOutputStream outputStream = openFileOutput(id, Context.MODE_PRIVATE);
            outputStream.write(content.getBytes());
            outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        Log.d("checking ", "success stored landmarks");


    }

    /**
     * Implements SetAdfNameDialog.CallbackListener.
     */
    @Override
    public void onAdfNameOk(String name, String uuid) {
        saveAdf(name);
    }

    /**
     * Implements SetAdfNameDialog.CallbackListener.
     */
    @Override
    public void onAdfNameCancelled() {
        // Continue running.
    }

    /**
     * The "Save ADF" button has been clicked.
     * Defined in {@code activity_area_description.xml}
     */
    public void saveAdfClicked(View view) {
        showSetAdfNameDialog();
    }

    /**
     * Save the current Area Description File.
     * Performs saving on a background thread and displays a progress dialog.
     */
    private void saveAdf(String adfName) {
        mSaveAdfTask = new SaveAdfTask(this, this, mTango, adfName);
        mSaveAdfTask.execute();
    }

    /**
     * Handles failed save from mSaveAdfTask.
     */
    @Override
    public void onSaveAdfFailed(String adfName) {
        String toastMessage = String.format(
                getResources().getString(R.string.save_adf_failed_toast_format),
                adfName);
        Toast.makeText(this, toastMessage, Toast.LENGTH_LONG).show();
        mSaveAdfTask = null;
    }

    /**
     * Handles successful save from mSaveAdfTask.
     */
    @Override
    public void onSaveAdfSuccess(String adfName, String adfUuid) {
        String toastMessage = String.format(
                getResources().getString(R.string.save_adf_success_toast_format),
                adfName, adfUuid);
        Toast.makeText(this, toastMessage, Toast.LENGTH_LONG).show();
        mSaveAdfTask = null;


        saveLandmarks(adfUuid);
        storeValuesToJSON(adfUuid);
        finish();
    }

    /**
     * Shows a dialog for setting the ADF name.
     */
    private void showSetAdfNameDialog() {
        Bundle bundle = new Bundle();
        bundle.putString(TangoAreaDescriptionMetaData.KEY_NAME, "New ADF");
        // UUID is generated after the ADF is saved.
        bundle.putString(TangoAreaDescriptionMetaData.KEY_UUID, "");

        FragmentManager manager = getFragmentManager();
        SetAdfNameDialog setAdfNameDialog = new SetAdfNameDialog();
        setAdfNameDialog.setArguments(bundle);
        setAdfNameDialog.show(manager, "ADFNameDialog");
    }


    private void handlePathFinding() {
//        loadValuesFromJson();
        PathFinder.coordinateSet = coordinateSet;
        PathFinder.granularity = granularity;

        Node start = new Node(roundToNearestHalf(translation[0]), roundToNearestHalf(translation[1]));
        Node end = new Node(roundToNearestHalf(mDestinationTranslation[0]), roundToNearestHalf(mDestinationTranslation[1]));

        if (PathFinder.pathfind(start, end)) {
            Toast t1 = Toast.makeText(getApplicationContext(), "Path Found!", Toast.LENGTH_SHORT);
            t1.show();

            String path = "";
            for (int i = 0; i < PathFinder.squashedPath.size(); i++) {
                path += String.valueOf(PathFinder.squashedPath.get(i).getX()) + ", "
                        + String.valueOf(PathFinder.squashedPath.get(i).getY()) + "\n";
            }

            Toast t2 = Toast.makeText(getApplicationContext(), path, Toast.LENGTH_LONG);
            t2.show();

            squashedPath = PathFinder.squashedPath;
            getRotationsArray(squashedPath);
            mIsNavigatingMode = true;
            waypointIterator = 0;
            updateWaypoint();
        }
    }

    private void getRotationsArray(List<Node> squashedPath) {
        rotationsArray = new float[squashedPath.size()];
        rotationsArray[0] = (float) getEulerAngleZ(orientation);
        for (int i = 1; i < rotationsArray.length; i++) {
            Node curr = squashedPath.get(i);
            Node prev = squashedPath.get(i - 1);
            double yDiff = curr.getY() - prev.getY();
            double xDiff = curr.getX() - prev.getX();

            rotationsArray[i] = ((float) Math.toDegrees(Math.atan2(yDiff, xDiff)) + 360) % 360;
        }
    }

    private void updateWaypoint() {
        ++waypointIterator;

        if (waypointIterator == squashedPath.size()) {
            Toast t2 = Toast.makeText(getApplicationContext(),
                    "Reached Destination!", Toast.LENGTH_SHORT);
            t2.show();

            ConvertTextToSpeech("Reached Destination");

            mIsNavigatingMode = false;
            mNextWaypointTextView.setText("");
            mNextRotationTextView.setText("");
            return;
        }

        Node nextWaypoint = squashedPath.get(waypointIterator);

        mNextWaypointTextView.setText(nextWaypoint.getX() +
                ", " + nextWaypoint.getY());
        mNextRotationTextView.setText(String.valueOf(rotationsArray[waypointIterator]));

        // Calculate the necessary rotation difference
        float rotationDiff = rotationsArray[waypointIterator] - rotationsArray[waypointIterator - 1];

        // Checking for the min of rotating either left or right
        if (Math.abs(rotationDiff) > 180) {
            if (rotationDiff > 0) {
                rotationDiff = rotationDiff - 360;
            } else {
                rotationDiff = 360 - Math.abs(rotationDiff);
            }
        }
        mRotationDiff = rotationDiff;
        ConvertTextToSpeech("Please place the cane in front of you and press select to prepare for rotation");

        // Send rotation through bluetooth
        float[] rotation = new float[1];
        rotation[0] = rotationDiff;

        Intent serviceIntent = new Intent(getApplicationContext(), BluetoothChatService.class);
        serviceIntent.putExtra("rotation", rotation);
        getApplicationContext().startService(serviceIntent);
    }


    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = mTts.setLanguage(Locale.US);
            if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("error", "This Language is not supported");
            } else {
                ConvertTextToSpeech("Starting");
            }
        } else
            Log.e("error", "Initilization Failed!");
    }

    private void ConvertTextToSpeech(String text) {
        if (text != null) {
            mTts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
        }

    }


    public class MyBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {

            Log.d("broadcast", "broadcast started");

            Bundle b = intent.getExtras();
            if (b != null) {
                if (b.getString("valueName") != null) {
                    String arduinoValue = b.getString("valueName");
                    Log.d("click value broadcast: ", arduinoValue);
                    processArduinoValues(arduinoValue);
                }
            }

        }
    }

    public void loadWaypoint(boolean isLoadAdf) {
        if (isLoadAdf) {
            // Load saved landmarks and

            ArrayList<String> fullUuidList;
            // Returns a list of ADFs with their UUIDs
            fullUuidList = mTango.listAreaDescriptions();
            if (fullUuidList.size() > 0) {

                jsonFileString = "empty file";
                jsonFileString = readFile(selectedUUID);

                Log.d("jsonFileString", jsonFileString);

                // String from file to json and then set values of translation


                // Get pose from first landmark saved (for now)

                //String lastLandmark = landmarkName.get(landmarkName.size()-1);
                String xName = (chosenLandmark + "_x");
                String yName = (chosenLandmark + "_y");
                String zName = (chosenLandmark + "_z");

                try {
                    JSONObject jsonObj = new JSONObject(jsonFileString);
                    mDestinationTranslation[0] = Float.valueOf(jsonObj.getString(xName));
                    mDestinationTranslation[1] = Float.valueOf(jsonObj.getString(yName));
                    mDestinationTranslation[2] = Float.valueOf(jsonObj.getString(zName));
                    mDestinationTextView.setText("X:" + mDestinationTranslation[0] + ", Y:" + mDestinationTranslation[1] + ", Z:" + mDestinationTranslation[2]);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                if (mIsRelocalized) {
                    Log.d("pathfinding", "starting");
                    handlePathFinding();
                } else
                    Log.d("mIsRelocalized", String.valueOf(mIsRelocalized));
            }
        }
    }

    private void storeValuesToJSON(String fileName) {
        try {
            JSONObject jsonObj = new JSONObject(readFile(fileName));

            jsonObj.put("coordinateSet", new ObjectMapper().writeValueAsString(coordinateSet));
            jsonObj.put("maxX", maxX);
            jsonObj.put("maxY", maxY);
            jsonObj.put("minX", minX);
            jsonObj.put("minY", minY);

            String content = jsonObj.toString();

            try {
                FileOutputStream outputStream = openFileOutput(fileName, Context.MODE_PRIVATE);
                outputStream.write(content.getBytes());
                outputStream.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    public void loadValuesFromJson() {
        try {
            JSONObject jsonObj = new JSONObject(jsonFileString);
            coordinateSet = new ObjectMapper().readValue(jsonObj.getString("coordinateSet"), new TypeReference<Set<Node>>() {
            });
            maxX = (float) jsonObj.getDouble("maxX");
            maxY = (float) jsonObj.getDouble("maxY");
            minX = (float) jsonObj.getDouble("minX");
            minY = (float) jsonObj.getDouble("minY");
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (JsonParseException e) {
            e.printStackTrace();
        } catch (JsonMappingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void printMatrix() {
        int xLength = (int) ((maxX - minX) / granularity) + 1;
        int yLength = (int) ((maxY - minY) / granularity) + 1;
        int offsetX = Math.abs((int) (minX / granularity));
        int offsetY = Math.abs((int) (minY / granularity));
        String s = "";

        boolean[][] coordinateMatrix = new boolean[xLength][yLength];

        for (Object coordinate : coordinateSet) {
            Node current = (Node) coordinate;
            coordinateMatrix[(int) (current.getX() / granularity) + offsetX][(int) (current.getY() / granularity) + offsetY] = true;
        }

        for (int i = 0; i < xLength; i++) {
            for (int j = 0; j < yLength; j++) {
                if (coordinateMatrix[i][j]) {
                    s += "1,";
                } else {
                    s += "0,";
                }
            }
            s += "\n";
        }
        Log.i("Matrix", s);
    }
}
