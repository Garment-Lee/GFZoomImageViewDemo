package com.ligf.gfzoomimageviewdemo;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.ligf.gfzoomimageviewdemo.widget.GFZoomImageView;

public class MainActivity extends AppCompatActivity {

    private GFZoomImageView mGFZoomImageView = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initViews();
    }

    private void initViews(){
        mGFZoomImageView = (GFZoomImageView) findViewById(R.id.img_zoom);
        mGFZoomImageView.setImageDrawable(getResources().getDrawable(R.mipmap.image2));
    }
}
