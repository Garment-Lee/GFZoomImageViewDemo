package com.ligf.gfzoomimageviewdemo.widget;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/**
 * Created by ligf on 2017/2/7.
 */
public class GFZoomImageView extends android.support.v7.widget.AppCompatImageView {

    private final String TAG = getClass().getName();
    private Context mContext = null;
    private enum State {NONE, DRAG, ZOOM, ANIMATE_ZOOM}

    /**
     * 图片操作状态（拖动、缩放、正在播放缩放动画、无状态）
     */
    private State mState = State.NONE;

    /**
     * 当前缩放图片矩阵
     */
    private Matrix mImageMatrix = null;

    /**
     * ImageView的缩放类型
     */
    private ScaleType mScaleType = null;

    /**
     * ImageView的宽度
     */
    private int mViewWidth;

    /**
     * ImageView的高度
     */
    private int mViewHeight;

    /**
     * 原始图片宽度
     */
    private int mOriginalImageWidth;

    /**
     * 原始图片高度
     */
    private int mOriginalImageHeight;

    /**
     * 初始缩放大小值
     */
    private float mInitialScale;

    /**
     * 图片最小的缩放数值（相对图片初始完整显示在View中来说）
     */
    private float mMinScale;

    /**
     * 图片最大的缩放数值（相对图片初始完整显示在View中来说）
     */
    private float mMaxScale;

    /**
     * 当前的缩放倍数
     */
    private float mCurrentScale;

    /**
     * 缩放手势检测器
     */
    private ScaleGestureDetector mScaleGestureDetector = null;

    /**
     * 手势检测器
     */
    private GestureDetector mGestureDetector = null;

    private float[] matrixValues;

    public GFZoomImageView(Context context) {
        super(context);
        init(context);
    }

    public GFZoomImageView(Context context, AttributeSet attrs){
        super(context, attrs);
        init(context);
    }

    public GFZoomImageView(Context context, AttributeSet attrs, int defStyle){
        super(context, attrs, defStyle);
        init(context);
    }

    /**
     * 触屏事件监听器
     */
    private class ZoomImageViewOnTouchListener implements OnTouchListener{

        private PointF startPoint = new PointF();
        private PointF endPoint = new PointF();

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            mScaleGestureDetector.onTouchEvent(event); //缩放监听
            mGestureDetector.onTouchEvent(event); //双击屏幕监听
            endPoint.set(event.getX(), event.getY());
            //进入拖动模式
            if (mState == State.NONE || mState == State.DRAG){  //拖动事件
                switch (event.getAction()){
                    case MotionEvent.ACTION_DOWN:
                        setState(State.DRAG);
                        startPoint.set(event.getX(), event.getY());
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (mState == State.DRAG){
                            float deltaX = endPoint.x - startPoint.x;
                            float deltaY = endPoint.y - startPoint.y;

                            float fixX = getFixDragTrans(deltaX, getImageWidth(), mViewWidth);
                            float fixY = getFixDragTrans(deltaY, getImageHeight(), mViewHeight);
                            mImageMatrix.postTranslate(fixX, fixY);
                            fixTrans();
                            startPoint.set(endPoint);
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_POINTER_UP:
                        setState(State.NONE);

                        break;
                }
            }
            setImageMatrix(mImageMatrix);
            return true;
        }
    }

    private void init(Context context){
        this.mContext = context;
        mImageMatrix = new Matrix();
        mScaleType = ScaleType.MATRIX;
        //设置图片缩放类型
        setScaleType(mScaleType);
        setImageMatrix(mImageMatrix);
        setState(State.NONE);
        mScaleGestureDetector = new ScaleGestureDetector(mContext, new ZoomImageViewScaleGestureListener());
        mGestureDetector = new GestureDetector(new ZoomImageViewGestureListener());
        matrixValues = new float[9];
        setOnTouchListener(new ZoomImageViewOnTouchListener());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        Drawable drawable = getDrawable();
        if (drawable == null || drawable.getIntrinsicWidth() == 0 || drawable.getIntrinsicHeight() == 0){
            setMeasuredDimension(0, 0);
            return;
        }
        int drawableWidth = drawable.getIntrinsicWidth();
        int drawableHeight = drawable.getIntrinsicHeight();
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        Log.i(TAG, "onMeasure111 width:" + getWidth() + ",height:" + getHeight());
        mViewWidth = getViewSize(widthMode, widthSize, drawableWidth);
        mViewHeight = getViewSize(heightMode, heightSize, drawableHeight);
        setMeasuredDimension(mViewWidth, mViewHeight);
        Log.i(TAG, "onMeasure222 width:" + getWidth() + ",height:" + getHeight());
        fitImageToView();
    }

    /**
     * get the view size base on the layout params
     * @param mode
     * @param size
     * @param drawableSize
     * @return
     */
    private int getViewSize(int mode, int size, int drawableSize){
        int viewSize;
        switch (mode){
            case MeasureSpec.EXACTLY:
                viewSize = size;
                break;
            case MeasureSpec.AT_MOST:
                viewSize = Math.min(drawableSize, size);
                break;
            case MeasureSpec.UNSPECIFIED:
                viewSize = size;
                break;
            default:
                viewSize = size;
        }
        return viewSize;
    }

    /**
     * 初始化图片显示到View中，完整居中显示图片
     */
    private void fitImageToView(){
        Drawable drawable = getDrawable();
        if (drawable == null || drawable.getIntrinsicWidth() == 0 || drawable.getIntrinsicHeight() == 0){
            return;
        }
        if (mImageMatrix == null){
            return;
        }
        //原始图片的长度
        mOriginalImageWidth = drawable.getIntrinsicWidth();
        //原始图片的高度
        mOriginalImageHeight = drawable.getIntrinsicHeight();
        //初始缩放大小
        float scaleX = (float) mViewWidth / mOriginalImageWidth;
        float scaleY = scaleX;
        Log.i(TAG, "fitImageToView scaleX:" + scaleX);
        //算出缩放后的图片是否需要平移，居中显示图片
        float XSpace = mViewWidth - scaleX * mOriginalImageWidth;
        float YSpace = mViewHeight - scaleY * mOriginalImageHeight;
        float matchViewWidth = mViewWidth - XSpace;
        float matchViewHeight = mViewHeight - YSpace;
        mImageMatrix.setScale(scaleX, scaleY);
        mCurrentScale = scaleX;
        mInitialScale = scaleX;
        mMinScale = mInitialScale * 0.7f;
        mMaxScale = mInitialScale * 6;
        if (matchViewHeight > mViewHeight){
            mImageMatrix.postTranslate(0, 0);
        } else {
            mImageMatrix.postTranslate(XSpace / 2, YSpace / 2);
        }
        setImageMatrix(mImageMatrix);
    }

    /**
     * 设置图片的缩放比例
     * @param deltaScale
     * @param focusX
     * @param focusY
     */
    private void scaleImage(double deltaScale, float focusX, float focusY){
        fixScaleTranslate();
        Log.i(TAG, "focusX: " + focusX + ";focusY: " + focusY);
        float[] imageMatrix = new float[9];
        mImageMatrix.getValues(imageMatrix);
        Log.i(TAG, "scaleImage before scale scaleX:" + imageMatrix[Matrix.MSCALE_X] + ", scaleY:" + imageMatrix[Matrix.MSCALE_Y] + ",transX:" + imageMatrix[Matrix.MTRANS_X] + ",transY:" + imageMatrix[Matrix.MTRANS_Y]);
        mImageMatrix.postScale((float) deltaScale, (float) deltaScale, focusX, focusY);
        mImageMatrix.getValues(imageMatrix);
        Log.i(TAG, "scaleImage after scale scaleX:" + imageMatrix[Matrix.MSCALE_X] + ", scaleY:" + imageMatrix[Matrix.MSCALE_Y] + ",transX:" + imageMatrix[Matrix.MTRANS_X] + ",transY:" + imageMatrix[Matrix.MTRANS_Y]);
        mCurrentScale = imageMatrix[Matrix.MSCALE_X];
        Log.i(TAG, "scaleImage scaleX:" + imageMatrix[Matrix.MSCALE_X] + ",scaleY:" + imageMatrix[Matrix.MSCALE_Y]);
        fixScaleTranslate();
        //刷新图片
        setImageMatrix(mImageMatrix);
    }

    /**
     * 修复缩放后的图片的位置
     */
    private void fixScaleTranslate(){
        fixTrans();
        mImageMatrix.getValues(matrixValues);
        Log.i(TAG, "fixScaleTranslate trans_x:" + matrixValues[Matrix.MTRANS_X] + ";trans_y:" + matrixValues[Matrix.MTRANS_Y]);
        //图片大小小于ImageView时，居中显示缩放后的图片
        if (getImageWidth() < mViewWidth){
            matrixValues[Matrix.MTRANS_X] = (mViewWidth - getImageWidth()) / 2;
        }
        if (getImageHeight() < mViewHeight){
            Log.i(TAG, "fixScaleTranslate getImageHeight() < mViewHeight mViewHeight:" + mViewHeight + ",ImageHeight:" + getImageHeight());
            matrixValues[Matrix.MTRANS_Y] = (mViewHeight - getImageHeight()) / 2;
            Log.i(TAG, "fixScaleTranslate MTRANS_Y:" + matrixValues[Matrix.MTRANS_Y]);
        }
        mImageMatrix.setValues(matrixValues);
    }

    /**
     * 移动缩放后的图片，保持图片缩放后还是占满ImageView
     */
    private void fixTrans() {
        mImageMatrix.getValues(matrixValues);
        float transX = matrixValues[Matrix.MTRANS_X];
        float transY = matrixValues[Matrix.MTRANS_Y];

        float fixTransX = getFixTrans(transX, mViewWidth, getImageWidth());
        float fixTransY = getFixTrans(transY, mViewHeight, getImageHeight());

        if (fixTransX != 0 || fixTransY != 0) {
            mImageMatrix.postTranslate(fixTransX, fixTransY);
        }
    }

    /**
     * 得到缩放后的图片需要修复的位移
     * @param trans 当前图片的位移大小
     * @param viewSize ImageView的尺寸
     * @param contentSize 缩放后的图片的尺寸
     * @return
     */
    private float getFixTrans(float trans, float viewSize, float contentSize) {
        float minTrans, maxTrans;

        if (contentSize <= viewSize) {
            minTrans = 0;
            maxTrans = viewSize - contentSize;
        } else {
            minTrans = viewSize - contentSize;      //image内容大于view
            maxTrans = 0;
        }

        if (trans < minTrans)
            return -trans + minTrans;     //缩小时或者拖动时，防止超出右侧边界（防止右边出现空白，向右移动）（下侧同理）
        if (trans > maxTrans)
            return -trans + maxTrans;       //缩小时或者拖动时，防止超出左侧边界（防止左边出现空白，向左移动）（上侧同理）
        return 0;
    }

    private void setState(State state){
        mState = state;
    }

    /**
     * 获取拖动位移
     * @param delta
     * @param contentSize
     * @param viewSize
     * @return
     */
    private float getFixDragTrans(float delta, float contentSize, float viewSize){
        if (contentSize < viewSize){
            return 0;
        }
        return delta;
    }

    /**
     * 获取当前图片的宽度
     * @return
     */
    private float getImageWidth(){
        return mCurrentScale * mOriginalImageWidth;
    }

    /**
     * 获取当前图片的高度
     * @return
     */
    private float getImageHeight(){
        return mCurrentScale * mOriginalImageHeight;
    }

    /**
     * 执行动画
     * @param runnable
     */
    private void  compatPostOnAnimation(Runnable runnable) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            postOnAnimation(runnable);
        } else {
            postDelayed(runnable, 1000 / 60);
        }
    }

    /**
     * 手势监听器，主要用来监听双击屏幕的动作
     */
    private class ZoomImageViewGestureListener extends GestureDetector.SimpleOnGestureListener{
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            boolean consumed = false;
            if (mState == State.NONE){
                float targetScale = (mCurrentScale == mInitialScale) ? mMaxScale : mInitialScale;
                DoubleTapZoom doubleTapZoom = new DoubleTapZoom(targetScale, e.getX(), e.getY());
                compatPostOnAnimation(doubleTapZoom);
                consumed = true;
            }
            return consumed;
        }
    }

    /**
     * 缩放手势监听器
     */
    private class ZoomImageViewScaleGestureListener extends ScaleGestureDetector.SimpleOnScaleGestureListener{
        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            Log.i(TAG, "### onScaleBegin....");
            setState(State.ZOOM);
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            Log.i(TAG, "### onScale....");

            scaleImage(detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            super.onScaleEnd(detector);
            setState(State.NONE);
            Log.i(TAG, "### onScaleEnd....");
            float targetZoom = mCurrentScale;
            //缩放倍数大于最大的倍数
            if (mCurrentScale > mMaxScale){
                targetZoom = mMaxScale;
            }
            //缩放图片倍数小于初始值
            if (mCurrentScale < mInitialScale){
                targetZoom = mInitialScale;
            }
            DoubleTapZoom doubleTapZoom = new DoubleTapZoom(targetZoom, mViewWidth / 2, mViewHeight /2);
            compatPostOnAnimation(doubleTapZoom);
        }
    }

    /**
     * 执行图片缩放动画Runnable
     */
    private class DoubleTapZoom implements Runnable{

        /**
         * 起始缩放大小值
         */
        private float startScale;
        /**
         * 目标放大倍数
         */
        private float targetScale;
        private float focusX;
        private float focusY;

        public DoubleTapZoom(float targetScale, float focusX, float focusY){
            startScale = mCurrentScale;
            this.targetScale = targetScale;
            this.focusX = focusX;
            this.focusY = focusY;
        }

        @Override
        public void run() {
            setState(State.ANIMATE_ZOOM);
            ValueAnimator valueAnimator = ValueAnimator.ofFloat(startScale, targetScale);
            valueAnimator.setDuration(500);
            valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    //待设置的缩放倍数
                    float scaleValue = (Float) animation.getAnimatedValue();
                    //算出deltaScale
                    float deltaScale = scaleValue / mCurrentScale;
                    scaleImage(deltaScale, focusX, focusY);
                }
            });
            valueAnimator.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animator) {

                }

                @Override
                public void onAnimationEnd(Animator animator) {
                    setState(State.NONE);
                }

                @Override
                public void onAnimationCancel(Animator animator) {

                }

                @Override
                public void onAnimationRepeat(Animator animator) {

                }
            });
            valueAnimator.start();
        }
    }
}
