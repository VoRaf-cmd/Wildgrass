package javaengine;

import java.io.BufferedInputStream;
import java.io.IOException;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.BooleanControl;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;

final class SoundEffect implements AutoCloseable {
    private final Clip clip;
    private float baseGain;
    private float masterGain;
    private boolean muted;

    SoundEffect(String filename) {
        clip = loadClip(filename);
    }

    private static Clip loadClip(String filename) {
        Clip audio = null;
        try (var resource = SoundEffect.class.getResourceAsStream("/audio/" + filename)) {
            if (resource == null) {
                throw new IOException("Arquivo " + filename + " ausente");
            }
            try (var stream = AudioSystem.getAudioInputStream(new BufferedInputStream(resource))) {
                audio = AudioSystem.getClip();
                audio.open(stream);
                return audio;
            }
        } catch (IOException | UnsupportedAudioFileException | LineUnavailableException
                 | IllegalArgumentException e) {
            if (audio != null) {
                audio.close();
            }
            System.err.println("Som " + filename + " indisponível: " + e.getMessage());
            return null;
        }
    }

    void play() {
        if (clip != null) {
            clip.stop();
            clip.setFramePosition(0);
            clip.start();
        }
    }

    void stop() {
        if (clip != null) clip.stop();
    }

    void loop(float gainDb) {
        if (clip != null) {
            volume(gainDb);
            clip.loop(Clip.LOOP_CONTINUOUSLY);
        }
    }

    void volume(float gainDb) {
        baseGain = gainDb;
        applyGain();
    }

    void masterVolume(int percent) {
        muted = percent == 0;
        masterGain = muted ? 0.0f : (float) (20.0 * Math.log10(percent / 100.0));
        applyGain();
    }

    void pan(float value) {
        if (clip != null) {
            if (clip.isControlSupported(FloatControl.Type.PAN)) {
                ((FloatControl) clip.getControl(FloatControl.Type.PAN)).setValue(value);
            } else if (clip.isControlSupported(FloatControl.Type.BALANCE)) {
                ((FloatControl) clip.getControl(FloatControl.Type.BALANCE)).setValue(value);
            }
        }
    }

    private void applyGain() {
        if (clip != null && clip.isControlSupported(BooleanControl.Type.MUTE)) {
            ((BooleanControl) clip.getControl(BooleanControl.Type.MUTE)).setValue(muted);
        }
        if (clip != null && clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            var control = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            control.setValue(muted ? control.getMinimum()
                    : Math.clamp(baseGain + masterGain, control.getMinimum(), control.getMaximum()));
        }
    }

    @Override
    public void close() {
        if (clip != null) {
            clip.close();
        }
    }
}
