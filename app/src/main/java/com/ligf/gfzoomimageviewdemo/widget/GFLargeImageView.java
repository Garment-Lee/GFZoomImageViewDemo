package com.ligf.gfzoomimageviewdemo.widget;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 加载大图ImageView。
 * <p>实现原理：
 *  使用Matrix进行图片的缩放和拖动的变换记录，通过Matrix中的MTRANS_X、MTRANS_Y、MSCALE_X、MSCALE_Y，换算出原图中该显示的图片块，使用BitmapRegionDecoder加载该图片块。<p/>
 *  <p>待完善地方：
 *  有点卡顿。
 *  <p/>
 */
public class GFLargeImageView extends android.support.v7.widget.AppCompatImageView {

    private final String TAG = getClass().getName();
    private Context mContext = null;
    private enum State {NONE, DRAG, ZOOM, ANIMATE_ZOOM}

    /**
     * 图片操作状态（如拖动、缩放、正在播放缩放动画、无状态）
     */
    private State mState = State.NONE;

    /**
     * 当前缩放图片矩阵
     */
    private Matrix mImageMatrix = null;

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
     * 初始缩放大小值(在图片初始完整显示在View中的缩放比例)
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
    private float mCurrentScale = -1;

    /**
     * 缩放手势检测器
     */
    private ScaleGestureDetector mScaleGestureDetector = null;

    /**
     * 手势检测器
     */
    private GestureDetector mGestureDetector = null;

    /**
     * 缓存矩阵的数值
     */
    private float[] mMatrixValues;

    private BitmapRegionDecoder mDecoder;

    public GFLargeImageView(Context context) {
        super(context);
        init(context);
    }

    public GFLargeImageView(Context context, AttributeSet attrs){
        super(context, attrs);
        init(context);
    }

    public GFLargeImageView(Context context, AttributeSet attrs, int defStyle){
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
                            invalidate();
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_POINTER_UP:
                        setState(State.NONE);

                        break;
                }
            }
//            setImageMatrix(mImageMatrix);
            return true;
        }
    }

    private void init(Context context){
        this.mContext = context;
        mImageMatrix = new Matrix();
        setState(State.NONE);
        mScaleGestureDetector = new ScaleGestureDetector(mContext, new ZoomImageViewScaleGestureListener());
        mGestureDetector = new GestureDetector(new ZoomImageViewGestureListener());
        mMatrixValues = new float[9];
        setOnTouchListener(new ZoomImageViewOnTouchListener());
    }

    public void setInputStream(InputStream inputStream){
        Bitmap bitmap = InputStream2Bitmap(inputStream);
        if (mImageMatrix == null){
            return;
        }
        //原始图片的长度
        mOriginalImageWidth = bitmap.getWidth();
        //原始图片的高度
        mOriginalImageHeight = bitmap.getHeight();

        //初始化BitmapRegionDecoder
        try {
            mDecoder = BitmapRegionDecoder.newInstance(inputStream, false);
        } catch (IOException e) {
            e.printStackTrace();
        }
        initImageView();
    }

    public void setImageViewWidthHeight(int viewWidth, int viewHeight){
        mViewWidth = viewWidth;
        mViewHeight = viewHeight;
        initImageView();
    }

    /**
     * 初始化图片（图片居中完整显示）
     */
    private void initImageView(){
        if (mViewWidth == 0 || mViewHeight == 0 || mOriginalImageWidth == 0 || mOriginalImageHeight == 0){
            return;
        }
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
        mMinScale = mInitialScale;
        mMaxScale = 1;
        if (matchViewHeight > mViewHeight){
            mImageMatrix.postTranslate(0, 0);
        } else {
            mImageMatrix.postTranslate(XSpace / 2, YSpace / 2);
        }
        invalidate();
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
//        setImageMatrix(mImageMatrix);
        Log.i(TAG, "init drawable:" + drawable.getBounds().toString());
        //初始化BitmapRegionDecoder
        try {
            mDecoder = BitmapRegionDecoder.newInstance(Drawable2InputStream(drawable), false);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Bitmap bitmap = calculateBitmapRegion();
        if (bitmap != null){
            //居中显示图片
            float[] imageMatrix = new float[9];
            mImageMatrix.getValues(imageMatrix);
            float transX = imageMatrix[Matrix.MTRANS_X];
            float transY = imageMatrix[Matrix.MTRANS_Y];
            float left = 0;
            float top = 0;
            if (transX > 0){
                left = transX;
            }
            if (transY > 0){
                top = transY;
            }
            canvas.drawBitmap(bitmap, left, top, null);
        }
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
       //限制图片缩放大小超过原图的大小，不然BitmapRegionDecoder加载图片块会报超出界限的异常。
        if (imageMatrix[Matrix.MSCALE_X] * deltaScale > mMaxScale){
//            mImageMatrix.setScale(mMaxScale, mMaxScale, focusX, focusY);
            return;
        } else if (imageMatrix[Matrix.MSCALE_X] * deltaScale < mMinScale){
//            mImageMatrix.setScale(mMinScale, mMinScale, focusX, focusY);
            return;
        } else {
            mImageMatrix.postScale((float) deltaScale, (float) deltaScale, focusX, focusY);
        }
        mImageMatrix.getValues(imageMatrix);
        Log.i(TAG, "scaleImage after scale scaleX:" + imageMatrix[Matrix.MSCALE_X] + ", scaleY:" + imageMatrix[Matrix.MSCALE_Y] + ",transX:" + imageMatrix[Matrix.MTRANS_X] + ",transY:" + imageMatrix[Matrix.MTRANS_Y]);
        mCurrentScale = imageMatrix[Matrix.MSCALE_X];
        Log.i(TAG, "scaleImage scaleX:" + imageMatrix[Matrix.MSCALE_X] + ",scaleY:" + imageMatrix[Matrix.MSCALE_Y]);
        fixScaleTranslate();
        invalidate();
        //刷新图片
//        setImageMatrix(mImageMatrix);
    }

    /**
     * 修复缩放后的图片的位置
     */
    private void fixScaleTranslate(){
        fixTrans();
        mImageMatrix.getValues(mMatrixValues);
        Log.i(TAG, "fixScaleTranslate trans_x:" + mMatrixValues[Matrix.MTRANS_X] + ";trans_y:" + mMatrixValues[Matrix.MTRANS_Y]);
        //图片大小小于ImageView时，居中显示缩放后的图片
        if (getImageWidth() < mViewWidth){
            mMatrixValues[Matrix.MTRANS_X] = (mViewWidth - getImageWidth()) / 2;
        }
        if (getImageHeight() < mViewHeight){
            Log.i(TAG, "fixScaleTranslate getImageHeight() < mViewHeight mViewHeight:" + mViewHeight + ",ImageHeight:" + getImageHeight());
            mMatrixValues[Matrix.MTRANS_Y] = (mViewHeight - getImageHeight()) / 2;
            Log.i(TAG, "fixScaleTranslate MTRANS_Y:" + mMatrixValues[Matrix.MTRANS_Y]);
        }
        mImageMatrix.setValues(mMatrixValues);
    }

    /**
     * 移动缩放后的图片，保持图片缩放后还是占满ImageView（防止图片缩放后，位置偏向一边，使一边留白）
     */
    private void fixTrans() {
        mImageMatrix.getValues(mMatrixValues);
        float transX = mMatrixValues[Matrix.MTRANS_X];
        float transY = mMatrixValues[Matrix.MTRANS_Y];

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

    /**
     * 设置图片操作状态，如拖动、缩放、执行缩放动画。
     * @param state
     */
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
            Log.i(TAG, "onScaleBegin....");
            setState(State.ZOOM);
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            scaleImage(detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            super.onScaleEnd(detector);
            setState(State.NONE);
            Log.i(TAG, "onScaleEnd....");
//            float targetZoom = mCurrentScale;
//            //缩放倍数大于最大的倍数
//            if (mCurrentScale > mMaxScale){
//                targetZoom = mMaxScale;
//            }
//            //缩放图片倍数小于初始值
//            if (mCurrentScale < mInitialScale){
//                targetZoom = mInitialScale;
//            }
//            DoubleTapZoom doubleTapZoom = new DoubleTapZoom(targetZoom, mViewWidth / 2, mViewHeight /2);
//            compatPostOnAnimation(doubleTapZoom);
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
            valueAnimator.setDuration(300);
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

    private static final BitmapFactory.Options options = new BitmapFactory.Options();

    static
    {
        options.inPreferredConfig = Bitmap.Config.RGB_565;
    }

    /**
     * 待显示图片块的矩形范围
     */
    private Rect mRect = new Rect();

    /**
     * 计算得到需要展示的图片
     * @return
     */
    public Bitmap calculateBitmapRegion(){
        if (mCurrentScale < 0)
            return null;
        mImageMatrix.getValues(mMatrixValues);
        float transX = mMatrixValues[Matrix.MTRANS_X];
        float transY = mMatrixValues[Matrix.MTRANS_Y];
        //该显示的图片块的范围
        float blockImageLeft = 0;
        float blockImageRight = 0;
        float blockImageTop = 0;
        float blockImageBottom = 0;
        if (transX <= 0){
            //偏移位移超出左边界
            blockImageLeft = -transX;
            blockImageRight = -transX + mViewWidth;
        }
        if (transY < 0){
            //偏移位移超出上边界
            blockImageTop = -transY;
            blockImageBottom = -transY + mViewHeight;
        }
        if (transX > 0){
            //偏移位移在左边界内
            blockImageLeft = 0;
            blockImageRight = getImageWidth();
        }
        if (transY > 0){
            //偏移位移在右边界内
            blockImageTop = 0;
            blockImageBottom = getImageHeight();
        }
        mRect.left = blockImageLeft == 0 ? 0 : (int)(blockImageLeft / mCurrentScale);
        mRect.right = (int)(blockImageRight / mCurrentScale);
        mRect.top = blockImageTop == 0 ? 0 : (int)(blockImageTop / mCurrentScale);
        mRect.bottom = (int)(blockImageBottom / mCurrentScale);
        Log.i(TAG, "calculateBitmapRegion transX:" + transX + ",transY:" + transY);
        Log.i(TAG, "calculateBitmapRegion mCurrent:" + mCurrentScale);
        Log.i(TAG, "calculateBitmapRegion blockImageLeft:" + blockImageLeft + ",blockImageRight:" + blockImageRight + ",blockImageTop:" + blockImageTop + ",blockImageBottom:" + blockImageBottom);
        Log.i(TAG, "calculateBitmapRegion mRect.left:" + mRect.left + ",mRect.right:" + mRect.right + ",mRect.top:" + mRect.top + ",mRect.bottom:" + mRect.bottom);

        Bitmap bitmap = mDecoder.decodeRegion(mRect, options);
        //对图片进行缩放操作，显示到View中
        Bitmap resultBitmap = scaleMatrix(bitmap, mCurrentScale, mCurrentScale);
        return resultBitmap;
    }

    /**
     * 使用Matrix
     * @param bitmap 原始的Bitmap
     * @param scaleW 目标宽度
     * @param scaleH 目标高度
     * @return 缩放后的Bitmap
     */
    public static Bitmap scaleMatrix(Bitmap bitmap, float scaleW, float scaleH){
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        Matrix matrix = new Matrix();
        matrix.postScale(scaleW, scaleH); // 长和宽放大缩小的比例
        return Bitmap.createBitmap(bitmap, 0, 0, w, h, matrix, true);
    }

    /**
     *  Drawable转换成InputStream
     */
    public static InputStream Drawable2InputStream(Drawable d) {
        Bitmap bitmap = drawable2Bitmap(d);
        return Bitmap2InputStream(bitmap);
    }

    /**
     *  将Bitmap转换成InputStream
     */
    public static InputStream Bitmap2InputStream(Bitmap bm) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bm.compress(Bitmap.CompressFormat.JPEG, 100, baos);
        InputStream is = new ByteArrayInputStream(baos.toByteArray());
        return is;
    }

    /**
     *  Drawable转换成Bitmap
     */
    public static Bitmap drawable2Bitmap(Drawable drawable) {
        Bitmap bitmap = Bitmap
                .createBitmap(
                        drawable.getIntrinsicWidth(),
                        drawable.getIntrinsicHeight(),
                        drawable.getOpacity() != PixelFormat.OPAQUE ? Bitmap.Config.ARGB_8888
                                : Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(),
                drawable.getIntrinsicHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    /**
     * 将InputStream转换成Bitmap
     */
    public static Bitmap InputStream2Bitmap(InputStream is) {
        return BitmapFactory.decodeStream(is);
    }
}
