package com.example.tuner;

import android.content.pm.PackageManager;
//import android.media.AudioFormat;
//import android.media.AudioRecord;
//import android.media.MediaRecorder;
import android.os.Bundle;
import android.Manifest;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Locale;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.io.android.AudioDispatcherFactory;
import be.tarsos.dsp.pitch.PitchDetectionHandler;
import be.tarsos.dsp.pitch.PitchDetectionResult;
import be.tarsos.dsp.pitch.PitchProcessor;

public class MainActivity extends AppCompatActivity {

    //Vars
    private SeekBar centSeekBar;
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private TextView frequencyText, tuningText;
    private AudioDispatcher dispatcher;
    private Thread dispatcherThread; //Thread for DSP dispatcher
    private EditText referenceFrequencyInput;
    private Button guitarButton, ukuleleButton, bassButton, setReferenceButton;
    private double refFrequency = 440.0; //default refFreq
    private float smoothedPitch = -1;
    //private static final float SMOOTHING_FACTOR = 0.5f; //0.1 float for default smoothing


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // GUI:
        frequencyText = findViewById(R.id.frequencyText);
        centSeekBar = findViewById(R.id.centSeekBar);
        referenceFrequencyInput = findViewById(R.id.referenceFrequencyInput);
        setReferenceButton = findViewById(R.id.setReferenceButton);
        referenceFrequencyInput = findViewById(R.id.referenceFrequencyInput);
        setReferenceButton = findViewById(R.id.setReferenceButton);
        tuningText = findViewById(R.id.tuningText);
        guitarButton = findViewById(R.id.guitarButton);
        ukuleleButton = findViewById(R.id.ukuleleButton);
        bassButton = findViewById(R.id.bassButton);

        //GUI listeners:
        guitarButton.setOnClickListener(v -> showTuning("guitar"));
        ukuleleButton.setOnClickListener(v -> showTuning("ukulele"));
        bassButton.setOnClickListener(v -> showTuning("bass"));


        setReferenceButton.setOnClickListener(v -> {
            String input = referenceFrequencyInput.getText().toString();
            if (!input.isEmpty()) {
                try {
                    double newReference = Double.parseDouble(input);
                    if (newReference > 0) {
                        refFrequency = newReference;
                    }
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }
            }
        });

        // Check for mic permissions:
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
        } else {
            startAudioProcessing();
        }
    }

    private void startAudioProcessing() {
        dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(22050, 1024, 0);
        PitchDetectionHandler pdh = new PitchDetectionHandler() {
            @Override
            public void handlePitch(PitchDetectionResult result, be.tarsos.dsp.AudioEvent e) {
                final float pitchInHz = result.getPitch();

                runOnUiThread(() -> {
                    if (pitchInHz > 0) { //if pitch detected
                        smoothedPitch=pitchInHz;
                        /*if (smoothedPitch < 0) { //smoothing
                            smoothedPitch = pitchInHz;
                        } else {
                            smoothedPitch = smoothedPitch * (1 - SMOOTHING_FACTOR) + pitchInHz * SMOOTHING_FACTOR;
                        }*/

                        String noteAndCents = getNoteAndCentsFromFrequency(smoothedPitch);
                        frequencyText.setText(String.format(Locale.US, "     %.2f Hz\n%s", smoothedPitch, noteAndCents));
                        frequencyText.setAlpha(1.0f);

                        double cents = getCentsFromFrequency(smoothedPitch);
                        int progress = (int) (50 + (cents / 50.0) * 50);
                        progress = Math.max(0, Math.min(100, progress));
                        centSeekBar.setProgress(progress);
                        centSeekBar.setAlpha(1.0f);

                        // Barvy podle přesnosti
                        int color;
                        if (Math.abs(cents) <= 3) { //For 3 cent deviation = green
                            color = ContextCompat.getColor(MainActivity.this, R.color.green);
                        } else if (Math.abs(cents) <= 10) {
                            color = ContextCompat.getColor(MainActivity.this, R.color.yellow);
                        } else {
                            color = ContextCompat.getColor(MainActivity.this, R.color.red);
                        }

                        centSeekBar.getProgressDrawable().setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);
                        centSeekBar.getThumb().setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);

                    } else { // Pokud neslyšíme nic
                        frequencyText.setText("Čekám na tón :)");
                        frequencyText.setAlpha(0.5f); // Lehce vybledlý text

                        centSeekBar.setProgress(50); // Reset doprostřed
                        centSeekBar.setAlpha(0.5f); // Vyšednutí posuvníku

                        int color = ContextCompat.getColor(MainActivity.this, R.color.gray);
                        centSeekBar.getProgressDrawable().setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);
                        centSeekBar.getThumb().setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);
                    }
                });
            }
        };
        dispatcher.addAudioProcessor(new PitchProcessor(
                PitchProcessor.PitchEstimationAlgorithm.FFT_YIN,
                22050,
                1024,
                pdh
        ));

        dispatcherThread = new Thread(dispatcher, "Audio Dispatcher");
        dispatcherThread.start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startAudioProcessing();
            }
        }
    }

    @Override
    //Ends microphone on app close - prevents bugs
    protected void onDestroy() {
        super.onDestroy();
        stopAudioProcessing();
    }

    @Override
    //Ends microphone on app pause
    protected void onPause() {
        super.onPause();
        stopAudioProcessing();
    }

    private void stopAudioProcessing() {
        if (dispatcher != null) {
            dispatcher.stop();
            dispatcher = null;
        }
        if (dispatcherThread != null) {
            try {
                dispatcherThread.interrupt(); //
                dispatcherThread.join(); //
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            dispatcherThread = null;
        }
    }
    private String getNoteAndCentsFromFrequency(float frequency) {
        if (frequency <= 0) return "Neznámý tón";

        //to MIDI - 69 = A4
        double noteNumber = 69 + 12 * Math.log(frequency / refFrequency) / Math.log(2);
        int midiNote = (int) Math.round(noteNumber);

        //Notes
        String[] noteNames = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};

        String noteName = noteNames[midiNote % 12];
        int octave = (midiNote / 12) - 1;

        //Nearest note freq
        double nearestNoteFrequency = refFrequency * Math.pow(2, (midiNote - 69) / 12.0);

        //Interval in cents
        double cents = 1200 * Math.log(frequency / nearestNoteFrequency) / Math.log(2);

        cents = Math.round(cents);

        return String.format(Locale.US, "          %s%d\n   (%.2f cents)", noteName, octave, cents);
    }
    private double getCentsFromFrequency(float frequency) {
        if (frequency <= 0) return 0;

        double noteNumber = 69 + 12 * Math.log(frequency / refFrequency) / Math.log(2);
        int midiNote = (int) Math.round(noteNumber);
        double nearestNoteFrequency = refFrequency * Math.pow(2, (midiNote - 69) / 12.0);

        return 1200 * Math.log(frequency / nearestNoteFrequency) / Math.log(2);
    }
    private void showTuning(String instrument) {
        String tuningInfo = "";
        switch (instrument) {
            case "guitar":
                tuningInfo = "Kytara:\nE4\nB3\nG3\nD3\nA2\nE2";
                break;
            case "ukulele":
                tuningInfo = "Ukulele:\nA4\nE4\nC4\nG4";
                break;
            case "bass":
                tuningInfo = "Baskytara:\nG2\nD2\nA1\nE1";
                break;
        }
        tuningText.setText(tuningInfo);
    }

}

