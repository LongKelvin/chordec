package com.example.chordec.chordec;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Point;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.easyandroidanimations.library.AnimationListener;
import com.easyandroidanimations.library.BounceAnimation;
import com.easyandroidanimations.library.FadeInAnimation;
import com.easyandroidanimations.library.FadeOutAnimation;
import com.easyandroidanimations.library.RotationAnimation;
import com.example.chordec.chordec.Database.Chord;
import com.example.chordec.chordec.Database.Database;
import com.example.chordec.chordec.Helper.Constants;
import com.rengwuxian.materialedittext.MaterialEditText;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.io.android.AudioDispatcherFactory;
import be.tarsos.dsp.pitch.PitchDetectionHandler;
import be.tarsos.dsp.pitch.PitchDetectionResult;
import be.tarsos.dsp.pitch.PitchProcessor;


public class MainActivity extends ActionBarActivity
    implements View.OnClickListener{

    // TAG
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String APP_FILE_NAME = "CHORDEC";
    private static final String FILE_EXTENSION = ".3gp";

    // Constants
    private static final int ROTATION_DURATION = 1500;
    private static final int TRANSLATE_DURATION = 1000;
    private static final int FADE_DURATION = 1000;
    private static final int PULSE_DURATION = 800;


    // widgets in activity_main.xml
    private ImageButton recordButton;
    private ImageButton pauseButton;
    private ImageButton stopButton;
    private ImageView   recordingImage;
    private TextView    timerTextView;
    private TextView    chordText;

    private TextView    hintText;
    private ImageView   hintImage;
    private TextView    hintText2;

    // layouts in activity_main.xml
    private RelativeLayout recordLayout;
    private LinearLayout timerLayout;



    // database
    private static Database database;

    // Media recorder
    private MediaRecorder audioRecorder;

    // file name
    private String   filePath;


    // timer
    private boolean isTimerRunning;
    private Timer timer;
    private TimerTask timerTask;
    private int   duration;

    //dimensions and positioning
    private int screenWidth;
    private int screenHeight;
    private int translateY;


    // states
    boolean isRecordLayoutVisible = false;
    boolean isPause = false;

    /*
        overridden methods - crucial for the activity class
    */

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        customizeActionBar();

        initializeDatabase();

        initializeState();

        initializeTimer();

        initializeWidgets();

        initializeLayout();

        initializeRecorder();

        initializePositioning();

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_database) {
            goToDatabaseActivity();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void customizeActionBar() {
        ActionBar menu = getSupportActionBar();
        menu.setIcon(R.drawable.app_logo);
        menu.setLogo(R.drawable.app_logo);
        menu.setDisplayUseLogoEnabled(true);
    }

    private void goToDatabaseActivity() {
        Intent intent = new Intent(this, DatabaseActivity.class);
        startActivity(intent);
    }

    /*
        initialize functions
    */

    private void initializeDatabase() {
        database = new Database(this);
    }

    private void initializeState() {
        isRecordLayoutVisible = false;
        isPause = false;
    }

    private void initializeTimer() {

        isTimerRunning = false;
        timer = new Timer();
        duration = 0;

        timerTask = new TimerTask() {

            @Override
            public void run() {

                if(isTimerRunning) {
                    duration += Constants.MILLISECONDS_RATE;
                    timerTextView.post(new Runnable() {
                        public void run() {
                            timerTextView.setText(
                                    Constants.getDurationFormat(duration));
                        }
                    });
                    recordingImage.post(new Runnable() {
                        public void run() {
                            new BounceAnimation(recordingImage).setNumOfBounces(1)
                                    .setDuration(PULSE_DURATION).animate();
                        }
                    });
                }
            }
        };

        timer.scheduleAtFixedRate(timerTask, 0, Constants.MILLISECONDS_RATE);
    }

    private void initializeWidgets() {
        recordButton = (ImageButton) findViewById(R.id.recordButton);
        recordButton.setOnClickListener(this);

        pauseButton = (ImageButton) findViewById(R.id.pauseButton);
        pauseButton.setOnClickListener(this);
        pauseButton.setBackgroundResource(R.drawable.pause);

        stopButton = (ImageButton) findViewById(R.id.stopButton);
        stopButton.setOnClickListener(this);

        timerTextView = (TextView) findViewById(R.id.timerTextView);

        hintText = (TextView) findViewById(R.id.hintText);
        hintImage = (ImageView) findViewById(R.id.hintImage);
        hintText2 = (TextView) findViewById(R.id.hintText2);

        recordingImage = (ImageView) findViewById(R.id.recordingImage);

        chordText = (TextView) findViewById(R.id.chordText);
    }


    private void initializeLayout() {
        recordLayout = (RelativeLayout) findViewById(R.id.recordLayout);
        timerLayout = (LinearLayout) findViewById(R.id.timerLayout);
    }

    private void initializeRecorder() {
        initializeMediaRecorder();
        //initializeAudioDispatching();
    }

    private void initializePositioning() {

        Point size = getScreenDimension();

        screenWidth = size.x;
        screenHeight = size.y;

        translateY = (int) (screenHeight - (
                getResources().getDimension(R.dimen.record_layout_height) * 5 / 6.0 +
                        getResources().getDimension(R.dimen.record_button_height) +
                        getResources().getDimension(R.dimen.record_button_margin_top)));

        Log.d(TAG, "translateY = " + translateY);
    }

    private void initializeFile() {
        String nextCardID = database.getStringNextChordId();

        filePath = Environment.getExternalStorageDirectory().
                getAbsolutePath() +
                "/" + APP_FILE_NAME + nextCardID + FILE_EXTENSION;

        File directory = new File(filePath).getParentFile();
        if (!directory.exists() && !directory.mkdirs()) {
            Log.e(TAG, "Path to file could not be created.");
        }

        Log.d(TAG, filePath);
    }

    private void initializeAudioDispatching() {

        AudioDispatcher dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(22050, 1024, 0);

        PitchDetectionHandler pdh = new PitchDetectionHandler() {
            @Override
            public void handlePitch(PitchDetectionResult result,AudioEvent e) {
                final float pitchInHz = result.getPitch();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        chordText.setText("" + pitchInHz);
                    }
                });
            }
        };
        AudioProcessor p = new PitchProcessor(PitchProcessor.PitchEstimationAlgorithm.FFT_YIN, 22050, 1024, pdh);
        dispatcher.addAudioProcessor(p);
        new Thread(dispatcher,"Audio Dispatcher").start();
    }

    /*
        event handlers
    */

    public void onClick(View v) {
        switch(v.getId()) {
            case R.id.recordButton:
                if(!isRecordLayoutVisible) {
                    animateRecordButton();
                    prepareMediaRecorder();
                    startMediaRecorder();
                    Log.d(TAG, "after pressing record button");
                }
                isRecordLayoutVisible = true;


                break;

            case R.id.pauseButton:
                if(isRecordLayoutVisible) {
                    changePauseButtonSrc();
                }

                break;

            case R.id.stopButton:
                if(isRecordLayoutVisible) {
                    stopRecording();
                }
                break;

            default:
                Log.e(TAG, "Widget is not recognized");
                break;
        }
    }

    /*
    * for record button
    * */

     private void animateRecordButton() {
        bounceAnimation();
    }

    private void bounceAnimation() {
        new RotationAnimation(recordButton)
                .setDuration(ROTATION_DURATION)
                .setListener(new AnimationListener() {
                    @Override
                    public void onAnimationEnd(com.easyandroidanimations.library.Animation animation) {
                        if (isRecordLayoutVisible)
                            transitionDownRecordButton();
                        else
                            transitionUpRecordButton();
                    }
                })
                .animate();
    }

    private void transitionUpRecordButton() {
        transitionRecordButton(0, 0, 0, -translateY);
    }

    private void transitionDownRecordButton() {
        transitionRecordButton(0, 0, 0, translateY);
    }

    private void transitionRecordButton(int startX, int stopX, int startY, final int stopY) {
        Animation animation = new TranslateAnimation(startX, stopX, startY, stopY);
        animation.setDuration(TRANSLATE_DURATION);
        animation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                recordButton.clearAnimation();
                RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                        recordButton.getWidth(), recordButton.getHeight());

                lp.addRule(RelativeLayout.CENTER_HORIZONTAL);


                if (stopY > 0) { //move down
                    int marginBottom =
                            (int)getResources().getDimension(R.dimen.record_layout_height)/2
                            - (int)getResources().getDimension(R.dimen.record_button_height)/2;

                    lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                    lp.setMargins(0, 0, 0, marginBottom);

                    recordButton.setBackgroundResource(R.drawable.white_bg);
                } else { //move up
                    lp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
                    lp.setMargins(0,
                            (int) getResources().getDimension(R.dimen.record_button_margin_top), 0, 0);

                    recordButton.setBackgroundColor(Color.TRANSPARENT);
                }

                recordButton.setLayoutParams(lp);
                changeLayoutsVisibility();
                changeWidgetsVisibility();
                startTimer();
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        recordButton.startAnimation(animation);
        recordButton.bringToFront();
    }

    private void changeLayoutsVisibility() {
        if(isRecordLayoutVisible) {
            setLayoutsVisible();
        } else {
            setLayoutsInvisible();
        }
    }

    private void changeWidgetsVisibility() {
        if(isRecordLayoutVisible) {
            setWidgetsVisible();
        } else {
            setWidgetsInvisible();
        }
    }

    private void setLayoutsVisible() {
        new FadeInAnimation(recordLayout).setDuration(FADE_DURATION).animate();
        new FadeInAnimation(timerLayout).setDuration(FADE_DURATION).animate();
    }

    private void setLayoutsInvisible() {
        new FadeOutAnimation(recordLayout).setDuration(FADE_DURATION).animate();
        new FadeOutAnimation(timerLayout).setDuration(FADE_DURATION).animate();
    }

    private void setWidgetsVisible() {
        new FadeOutAnimation(hintText).setDuration(FADE_DURATION).animate();
        new FadeOutAnimation(hintImage).setDuration(FADE_DURATION).animate();
        new FadeOutAnimation(hintText2).setDuration(FADE_DURATION).animate();

        new FadeInAnimation(recordingImage).setDuration(FADE_DURATION).animate();
        new FadeInAnimation(chordText).setDuration(FADE_DURATION).animate();

    }

    private void setWidgetsInvisible() {
        new FadeInAnimation(hintText).setDuration(FADE_DURATION).animate();
        new FadeInAnimation(hintImage).setDuration(FADE_DURATION).animate();
        new FadeInAnimation(hintText2).setDuration(FADE_DURATION).animate();

        new FadeOutAnimation(recordingImage).setDuration(FADE_DURATION).animate();
        new FadeOutAnimation(chordText).setDuration(FADE_DURATION).animate();
    }


    /*
    * for Pause button
    * */

    private void changePauseButtonSrc() {
        isPause = !isPause;
        if(isPause) {
            pauseButton.setBackgroundResource(R.drawable.play);
        } else {
            pauseButton.setBackgroundResource(R.drawable.pause);
        }
    }

    /*
    * for stop button
    * */

    private void stopRecording () {
        stopMediaRecorder();
        saveChord();
    }

    private void saveChord() {
        createSaveDialog();
    }

    private void createSaveDialog() {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        Date date = Calendar.getInstance().getTime();
        final String dateFormat = Constants.getDateFormat(date.getTime());

        // creating view for dialog

        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.dialog_ui, null);

        TextView chordDuration = (TextView) view.findViewById(R.id.chordDuration);
        chordDuration.setText(Constants.getDurationFormat(duration));

        TextView chordDate = (TextView) view.findViewById(R.id.chordDate);
        chordDate.setText(dateFormat);

        final MaterialEditText editText = (MaterialEditText) view.findViewById(R.id.editText);
        editText.setPrimaryColor(
                getResources().getColor(R.color.edit_text_floating_color));

        editText.setMaxCharacters(30);
        editText.setFloatingLabel(1);
        editText.setFloatingLabelText("Record Name");
        editText.setFloatingLabelTextColor(
                getResources().getColor(R.color.edit_text_floating_color));
        editText.setFloatingLabelTextSize(30);

        //hide soft keyboard
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(editText.getWindowToken(), 0);


        // pause timer
        pauseTimer();

        // configure dialog
        builder.setView(view)
                .setCancelable(false)
                .setNegativeButton(android.R.string.cancel,
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                                if (which == Dialog.BUTTON_NEGATIVE) {

                                    //resetting record layout
                                    isRecordLayoutVisible = false;
                                    animateRecordButton();

                                    //reset timer
                                    resetTimer();
                                    dialog.dismiss();

                                }
                            }
                        })
                .setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                                if(which == Dialog.BUTTON_POSITIVE) {
                                    String name = editText.getText().toString();

                                    if (!name.isEmpty()) {

                                        saveToDatabase(name, dateFormat);

                                        Toast.makeText(MainActivity.this,
                                                "Chord created", Toast.LENGTH_SHORT).show();

                                        //resetting record layout
                                        isRecordLayoutVisible = false;
                                        animateRecordButton();


                                        //reset timer
                                        resetTimer();

                                        dialog.dismiss();
                                    }
                                }
                            }
                        });
        builder.create().show();

    }

    private void saveToDatabase (String name, String date) {
        Chord chord = saveContent(name, date);
        database.insertChord(chord);
    }

    private Chord saveContent(String name, String date) {
        Chord chord = new Chord();

        chord.setChordName(name);
        chord.setChordID(database.getNextChordId());
        chord.setChordPath(filePath);
        chord.setChordDate(date);
        chord.setChordDuration(duration);

        //TODO: GET REAL SCORE
        chord.setChordScore("TEE HEE");

        return chord;
    }

    /*
    * Media recorder
    * */

    private void initializeMediaRecorder() {
        audioRecorder = new MediaRecorder();
        audioRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        audioRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        audioRecorder.setAudioEncoder(MediaRecorder.OutputFormat.AMR_NB);
    }

    private void prepareMediaRecorder() {
        initializeFile();
        audioRecorder.setOutputFile(filePath);
    }

    private void startMediaRecorder() {
        try {

            audioRecorder.prepare();
            audioRecorder.start();

            Log.d(TAG, "preparing media recorder");

        } catch (IllegalStateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void stopMediaRecorder() {
        audioRecorder.stop();
        audioRecorder.release();
    }

    /*
    * Timer utility functions
    * */

    private void startTimer() {
        isTimerRunning = true;
        duration = 0;
    }

    private void pauseTimer() {
        isTimerRunning = false;
    }

    private void resetTimer() {
        duration = 0;
        isTimerRunning = false;
    }


    /*
        helper functions
    */

    private Point getScreenDimension() {
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);

        return size;
    }


}
