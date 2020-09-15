package com.jeffmony.mp4demo;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.jeffmony.mp4parser.MP4Structure;

import java.io.FileInputStream;
import java.io.IOException;

public class PrintStructureActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_print_structure);

        try {
            FileInputStream fis = getResources().openRawResourceFd(R.raw.video_test).createInputStream();
            MP4Structure.getInstance().printMP4Structure(fis.getChannel(), 0, 0, 0);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
