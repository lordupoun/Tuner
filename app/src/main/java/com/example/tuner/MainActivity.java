package com.example.tuner;

import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.Manifest;
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
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private TextView frequencyText;
    private AudioDispatcher dispatcher;
    private Thread dispatcherThread; // Přidáno: vlákno dispatcheru

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

        // Check for permissions:
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
                if (pitchInHz > 0) {
                    runOnUiThread(() -> {
                        String noteAndCents = getNoteAndCentsFromFrequency(pitchInHz);
                        frequencyText.setText(String.format(Locale.US, "Frekvence: %.2f Hz\nTón: %s", pitchInHz, noteAndCents));
                    });
                }
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
    //Ends microphone on app close
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

        // Převod frekvence na MIDI číslo
        double noteNumber = 69 + 12 * Math.log(frequency / 440.0) / Math.log(2);
        int midiNote = (int) Math.round(noteNumber);

        // Názvy not
        String[] noteNames = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};

        String noteName = noteNames[midiNote % 12];
        int octave = (midiNote / 12) - 1;

        // Ideální frekvence té noty
        double nearestNoteFrequency = 440.0 * Math.pow(2, (midiNote - 69) / 12.0);

        // Odchylka v centech
        double cents = 1200 * Math.log(frequency / nearestNoteFrequency) / Math.log(2);

        return String.format(Locale.US, "%s%d (%.2f cents)", noteName, octave, cents);
    }

}
