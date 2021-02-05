package com.example.myapplication;

import android.graphics.Bitmap;
import android.graphics.RectF;

import java.util.List;
import java.util.Objects;

//object recognition interface
public interface ImageClassifier {

    // class of recognized object
    public class Recognition {
        //unique identifier
        private final String id;

        //object name
        private final String title;

        //confidence score
        private final Float confidence;

        //location of detected object
        private RectF location;

        //constructor
        public Recognition(String id, String title, Float confidence, RectF location) {
            this.id = id;
            this.title = title;
            this.confidence = confidence;
            this.location = location;
        }

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public Float getConfidence() {
            return confidence;
        }

        public RectF getLocation() {
            return new RectF(location);
        }

        public void setLocation(RectF location) {
            this.location = location;
        }

        @Override
        public String toString() {
            String resultString = "";
            if (id != null) {
                resultString += "[" + id + "] ";
            }

            if (title != null) {
                resultString += title + " ";
            }

            if (confidence != null) {
                resultString += String.format("(%.1f%%) ", confidence * 100.0f); //make confidence range from 0 to 100 and in percentage
            }

            if (location != null) {
                resultString += location + " ";
            }

            return resultString.trim(); //trim white space of both ends of string
        }

    }



    List<Recognition> detectObjects(Bitmap bitmap);

    void enableStatLogging(final boolean debug);

    String getStatString();

    void close();

}
