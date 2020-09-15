package com.jeffmony.mp4demo;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private TextView mPrintMp4View;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initViews();
    }

    private void initViews() {
        mPrintMp4View = findViewById(R.id.print_mp4_btn);

        mPrintMp4View.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        Intent intent = new Intent();
        if (v == mPrintMp4View) {
            intent.setClass(this, PrintStructureActivity.class);
            startActivity(intent);
        }
    }
}