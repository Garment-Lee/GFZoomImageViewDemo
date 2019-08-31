package com.ligf.gfzoomimageviewdemo;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import com.ligf.gfzoomimageviewdemo.widget.GFDrawingImageView;
import com.ligf.gfzoomimageviewdemo.widget.GFLargeImageView;
import com.ligf.gfzoomimageviewdemo.widget.GFZoomImageView;

import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{

    public static final String TAG = MainActivity.class.getName();

    private GFZoomImageView mGFZoomImageView = null;

    private GFLargeImageView mGFLargeImageView = null;

    private GFDrawingImageView mGFDrawingImageView = null;

    private Button mSaveBtn;

    private ImageView mShowImageView = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initViews();
        testMatrix();
    }

    private void initViews(){
//        mGFZoomImageView = (GFZoomImageView) findViewById(R.id.img_zoom);
//        mGFZoomImageView.setImageDrawable(getResources().getDrawable(R.mipmap.image2));
//        int width = mGFZoomImageView.getWidth();
//        int height = mGFZoomImageView.getHeight();
//        Log.i(TAG, "initViews width:" + width + ",height:" + height);
        mGFDrawingImageView = (GFDrawingImageView) findViewById(R.id.img_drawing);

        mSaveBtn = (Button) findViewById(R.id.btn_save);
        mSaveBtn.setOnClickListener(this);

        mShowImageView = (ImageView) findViewById(R.id.img_show);


        mGFLargeImageView = (GFLargeImageView) findViewById(R.id.img_large);
        InputStream inputStream = null;
        //获取View的宽高值
        mGFLargeImageView.post(new Runnable() {

            @Override
            public void run() {
                int width = mGFLargeImageView.getWidth(); // 获取宽度
                int height = mGFLargeImageView.getHeight(); // 获取高度
                mGFLargeImageView.setImageViewWidthHeight(width, height);
            }
        });
        ////        mGFLargeImageView.setImageDrawable(getResources().getDrawable(R.mipmap.aaa));

        try {
            inputStream = getAssets().open("image2.jpg");
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (inputStream != null){
            Bitmap bitmap = InputStream2Bitmap(inputStream);
            mGFDrawingImageView.setBitmap(bitmap);
        }
        Drawable drawable = getResources().getDrawable(R.mipmap.image2);
        Log.i(TAG, "initViews drawable bounds:" + drawable.getBounds().toString());
        Log.i(TAG, "initViews width:" + drawable.getIntrinsicWidth() + ",height:" + drawable.getIntrinsicHeight());
    }

    public void testMatrix(){
        float[] matrixValues = new float[9];
        Matrix matrixOne = new Matrix();
        Matrix matrixTwo = new Matrix();
        matrixOne.getValues(matrixValues);
        Log.i(TAG, "testMatrix matrixOne before post transX:" + matrixValues[Matrix.MTRANS_X] + ",transY:" + matrixValues[Matrix.MTRANS_Y] + ",scaleX:" + matrixValues[Matrix.MSCALE_X] + ",scaleY:" + matrixValues[Matrix.MSCALE_Y]);
        matrixOne.postScale(2, 2);
        matrixOne.getValues(matrixValues);
        Log.i(TAG, "testMatrix matrixOne after post first transX:" + matrixValues[Matrix.MTRANS_X] + ",transY:" + matrixValues[Matrix.MTRANS_Y] + ",scaleX:" + matrixValues[Matrix.MSCALE_X] + ",scaleY:" + matrixValues[Matrix.MSCALE_Y]);
        matrixOne.postScale(2, 2);
        matrixOne.getValues(matrixValues);

        Log.i(TAG, "testMatrix matrixOne after post second transX:" + matrixValues[Matrix.MTRANS_X] + ",transY:" + matrixValues[Matrix.MTRANS_Y] + ",scaleX:" + matrixValues[Matrix.MSCALE_X] + ",scaleY:" + matrixValues[Matrix.MSCALE_Y]);

        matrixTwo.getValues(matrixValues);
        Log.i(TAG, "testMatrix matrixTwo before post transX:" + matrixValues[Matrix.MTRANS_X] + ",transY:" + matrixValues[Matrix.MTRANS_Y] + ",scaleX:" + matrixValues[Matrix.MSCALE_X] + ",scaleY:" + matrixValues[Matrix.MSCALE_Y]);
        matrixTwo.postScale(2, 2, 100, 100);
        matrixTwo.getValues(matrixValues);
        Log.i(TAG, "testMatrix matrixTwo after post first transX:" + matrixValues[Matrix.MTRANS_X] + ",transY:" + matrixValues[Matrix.MTRANS_Y] + ",scaleX:" + matrixValues[Matrix.MSCALE_X] + ",scaleY:" + matrixValues[Matrix.MSCALE_Y]);
        matrixTwo.postScale(2, 2, 200, 300);
        matrixTwo.getValues(matrixValues);
        Log.i(TAG, "testMatrix matrixTwo after post second transX:" + matrixValues[Matrix.MTRANS_X] + ",transY:" + matrixValues[Matrix.MTRANS_Y] + ",scaleX:" + matrixValues[Matrix.MSCALE_X] + ",scaleY:" + matrixValues[Matrix.MSCALE_Y]);
    }

    /**
     * 将InputStream转换成Bitmap
     */
    public static Bitmap InputStream2Bitmap(InputStream is) {
        return BitmapFactory.decodeStream(is);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()){
            case R.id.btn_save:
                mGFDrawingImageView.setVisibility(View.GONE);
                Bitmap bitmap = mGFDrawingImageView.saveCompositeBitmap();
//                InputStream inputStream = GFDrawingImageView.bitmap2InputStream(bitmap);
                mShowImageView.setVisibility(View.VISIBLE);
                mShowImageView.setImageBitmap(bitmap);
//                mGFLargeImageView.setVisibility(View.VISIBLE);
//                mGFLargeImageView.setInputStream(inputStream);
                break;
        }
    }
}
