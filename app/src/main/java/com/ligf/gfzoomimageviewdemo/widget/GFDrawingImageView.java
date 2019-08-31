package com.ligf.gfzoomimageviewdemo.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Region;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * 可绘图的ImageView
 *
 * @author Garment
 */
public class GFDrawingImageView extends View {

    public static final String TAG = GFDrawingImageView.class.getName();

    /**
     * 待绘制的Bitmap对象(原始图片)
     */
    private Bitmap mDrawingBitmap;

    /**
     * 原始图片的宽度
     */
    private int mOriginalImageWidth;

    /**
     * 原始图片的高度
     */
    private int mOriginalImageHeight;

    /**
     * View的宽度
     */
    private int mViewWidth;

    /**
     * View的高度
     */
    private int mViewHeight;

    /**
     * 当前缩放图片矩阵
     */
    private Matrix mDrawingImageMatrix = null;

    /**
     * 当前缩放图片矩阵的逆矩阵
     */
    private Matrix mDrawingImageReverseMatrix = null;

    /**
     * 初始缩放大小值(初始居中完整显示时的缩放比例)
     */
    private float mInitialScale;

    /**
     * 图片最小的缩放倍数（相对初始缩放大小值）
     */
    private float mMinScale;

    /**
     * 图片最大的缩放倍数（相对初始缩放大小值）
     */
    private float mMaxScale;

    /**
     * 当前的缩放倍数
     */
    private float mCurrentScale = -1;

    private enum State {NONE, DRAWING, DRAG, ZOOM, ANIMATE_ZOOM}

    /**
     * View当前操作状态
     */
    private State mCurrentState = State.NONE;

    private float[] mMatrixValues;

    /**
     * 绘制线画笔
     */
    private Paint mDrawingViewPaint;

    /**
     * 绘制矩形框画笔
     */
    private Paint mDrawingRectanglePaint;

    /**
     * 删除按钮图标
     */
    private Bitmap mDeleteIconBitmap;

    private Context mContext;

    /**
     * 正在编辑绘图标志
     */
    private boolean isEditingFlag;

    public GFDrawingImageView(Context context) {
        super(context);
        mContext = context;
        init();
    }

    public GFDrawingImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        init();
    }

    public GFDrawingImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        init();
    }

    private void init() {
        setState(State.NONE);
        mDrawingImageMatrix = new Matrix();
        mDrawingImageReverseMatrix = new Matrix();
        mMatrixValues = new float[9];
        mDrawingViewPaint = new Paint();
        // 画笔颜色为蓝色
        mDrawingViewPaint.setColor(Color.BLUE);
        // 宽度5个像素
        mDrawingViewPaint.setStrokeWidth(5);

        mDrawingRectanglePaint = new Paint();
        mDrawingRectanglePaint.setColor(Color.RED);
        mDrawingRectanglePaint.setStrokeWidth(3);

        try {
            InputStream inputStream = mContext.getAssets().open("u2_5.png");
            mDeleteIconBitmap = inputStream2Bitmap(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setState(State state) {
        mCurrentState = state;
    }

    /**
     * 设置要显示的图片Bitmap对象
     *
     * @param bitmap
     */
    public void setBitmap(Bitmap bitmap) {
        mDrawingBitmap = bitmap;
        mOriginalImageWidth = bitmap.getWidth();
        mOriginalImageHeight = bitmap.getHeight();
        initView();
    }

    /**
     * 初始化View对象,居中完整显示图片
     */
    private void initView() {
        if (mOriginalImageWidth == 0 || mOriginalImageHeight == 0) {
            return;
        }
        mViewWidth = getMeasuredWidth();
        mViewHeight = getMeasuredHeight();
        //如果没有得到View的长宽，延迟重新获取
        if (mViewWidth == 0 || mViewHeight == 0) {
            post(new Runnable() {
                @Override
                public void run() {
                    initView();
                }
            });
            return;
        }
        //初始缩放大小
        float scaleX = (float) mViewWidth / mOriginalImageWidth;
        float scaleY = scaleX;

        //算出缩放后的图片是否需要平移，居中显示图片
        float XSpace = mViewWidth - scaleX * mOriginalImageWidth;
        float YSpace = mViewHeight - scaleY * mOriginalImageHeight;
        float matchViewWidth = mViewWidth - XSpace;
        float matchViewHeight = mViewHeight - YSpace;
        mDrawingImageMatrix.setScale(scaleX, scaleY);

        mCurrentScale = scaleX;
        mInitialScale = scaleX;
        //最小就是原始的缩放大小
        mMinScale = mInitialScale;
        //最大放大6倍
        mMaxScale = mInitialScale * 6;

        //居中修复
        if (matchViewHeight > mViewHeight) {
            mDrawingImageMatrix.postTranslate(0, 0);
        } else {
            mDrawingImageMatrix.postTranslate(XSpace / 2, YSpace / 2);
        }

        //View刷新重绘
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                startX = event.getX();
                startY = event.getY();
                //得到画布Bitmap对象，用于绘制图片
                mDrawingCanvasBitmap = getDrawingCanvasBitmap();

                startPoint0.x = event.getX(0);
                startPoint0.y = event.getY(0);

                break;
            case MotionEvent.ACTION_MOVE:
                if (mCurrentState == State.NONE) {
                    endX = event.getX();
                    endY = event.getY();
                    //需要滑动一定距离才能进入绘图模式
                    if (getPointDistance(new PointF(startX, startY), new PointF(endX, endY)) > 3) {
                        mCurrentState = State.DRAWING;
                        startX = event.getX();
                        startY = event.getY();
                    }
                }

                //进入绘制图片模式
                if (mCurrentState == State.DRAWING) {
                    Log.i(TAG, "ACTION_POINTER_UP Drawing pointer count:" + event.getPointerCount());
                    if (isFromDragScaleToDrawing) {
                        startX = event.getX();
                        startY = event.getY();
                        isFromDragScaleToDrawing = false;
                    }
                    endX = event.getX();
                    endY = event.getY();

                    //清屏操作
                    mDrawingViewPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                    mDrawingCanvas.drawPaint(mDrawingViewPaint);
                    mDrawingViewPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));

                    if (mDrawingCanvas != null) {
                        mDrawingCanvas.drawLine(startX, startY, endX, endY, mDrawingViewPaint);
                    }

                    invalidate();
                }

                //进入拖动图片或缩放图片模式
                if (mCurrentState == State.DRAG || mCurrentState == State.ZOOM) {
                    int count = event.getPointerCount();
                    Log.i(TAG, "ACTION_MOVE count:" + count);
                    float point0X = event.getX(0);
                    float point0Y = event.getY(0);
                    float point1X = event.getX(1);
                    float point1Y = event.getY(1);
                    Log.i(TAG, "ACTION_MOVE point0X:" + point0X + ",point0Y:" + point0Y + ",point1X:" + point1X + ",point1Y:" + point1Y);

                    endPoint0.x = event.getX(0);
                    endPoint0.y = event.getY(0);
                    endPoint1.x = event.getX(1);
                    endPoint1.y = event.getY(1);

                    float startSpan = getPointDistance(startPoint0, startPoint1);
                    float endSpan = getPointDistance(endPoint0, endPoint1);
                    float deltaSpan = endSpan - startSpan;

                    if (Math.abs(deltaSpan) < 6) {
                        setState(State.DRAG);
                        //拖动操作
                        fixScaleTranslate();
                        float deltaX0 = endPoint0.x - startPoint0.x;
                        float deltaX1 = endPoint1.x - startPoint1.x;
                        float transX = (deltaX0 + deltaX1) / 2;
                        float deltaY0 = endPoint0.y - startPoint0.y;
                        float deltaY1 = endPoint1.y - startPoint1.y;
                        float transY = (deltaY0 + deltaY1) / 2;
                        mDrawingImageMatrix.postTranslate(transX, transY);
                        fixScaleTranslate();
                        invalidate();
                    } else {
                        setState(State.ZOOM);
                        //缩放操作
                        if (deltaSpan > 0) {
                            //放大
                            float scale = endSpan / startSpan;//得到缩放倍数
                            scaleImage(scale, (startPoint0.x + startPoint1.x) / 2, (startPoint0.y + startPoint1.y) / 2);
                        } else {
                            //缩小
                            float scale = endSpan / startSpan; //得到缩放倍数
                            scaleImage(scale, (startPoint0.x + startPoint1.x) / 2, (startPoint0.y + startPoint1.y) / 2);
                        }
                    }

                    startPoint0.x = endPoint0.x;
                    startPoint0.y = endPoint0.y;

                    startPoint1.x = endPoint1.x;
                    startPoint1.y = endPoint1.y;

                }

                break;
            case MotionEvent.ACTION_UP:

                //先执行是否需要删除的动作
                if (isEditingFlag) {
                    int removeIndex = -1;
                    for (int i = 0; i < mDrawingViewList.size(); i++) {
                        DrawingView drawingView = mDrawingViewList.get(i);
                        if (drawingView.drawRectangleFlag) {
                            //利用矩阵算出矩形框绘制的位置
                            mDrawingImageMatrix.getValues(mMatrixValues);
                            Rect rect = drawingView.region.getBounds();
                            float left = rect.left * mMatrixValues[0] + rect.top * mMatrixValues[1] + mMatrixValues[2];
                            float top = rect.left * mMatrixValues[3] + rect.top * mMatrixValues[4] + mMatrixValues[5];
                            float touchX = event.getX();
                            float touchY = event.getY();
                            //判断如果点击删除的位置在指定的区域，则设置删除标志
                            if (touchX > (left - 50) && touchX < (left + 50) && touchY > (top - 50) && touchY < (top + 50)) {
                                removeIndex = i;
                            }
                        }
                    }
                    if (removeIndex >= 0) {
                        mDrawingViewList.remove(removeIndex);
                        invalidate();
                    }
                }

                if (mCurrentState == State.NONE) {
                    setState(State.NONE);
                    //先清除正在编辑标志
                    isEditingFlag = false;
                    int count = mDrawingViewList.size();
                    for (DrawingView drawingView : mDrawingViewList) {
                        drawingView.drawRectangleFlag = false;
                    }
                    //从后面开始遍历，最新添加的View放在队列的后面
                    for (int i = count - 1; i >= 0; i--) {
                        if (isEditingFlag){
                            continue;
                        }
                        //通过逆转矩阵反算出触点在原始图片中的位置
                        mDrawingImageMatrix.invert(mDrawingImageReverseMatrix);
                        mDrawingImageReverseMatrix.getValues(mMatrixValues);
                        float originalX = event.getX() * mMatrixValues[0] + event.getY() * mMatrixValues[1] + mMatrixValues[2];
                        float originalY = event.getX() * mMatrixValues[3] + event.getY() * mMatrixValues[4] + mMatrixValues[5];
                        DrawingView drawingView = mDrawingViewList.get(i);
                        if (drawingView.responseRegion.contains((int) originalX, (int) originalY)) {
                            drawingView.drawRectangleFlag = true;
                            //设置正在编辑标志为true
                            isEditingFlag = true;
                        } else {
                            drawingView.drawRectangleFlag = false;
                        }
                    }
                    invalidate();
                }

                //手指离开屏幕后，生成绘制图片
                if (mCurrentState == State.DRAWING) {
                    float pointDistance = getPointDistance(new PointF(startX, startY), new PointF(endX, endY));
                    if (pointDistance > 0) {
                        DrawingView drawingView = new DrawingView();

                        //截取绘制图片
                        try {
                            mDecoder = BitmapRegionDecoder.newInstance(bitmap2InputStream(mDrawingCanvasBitmap), false);
                            mDrawingRect.left = (int) Math.min(startX, endX);
                            mDrawingRect.right = (int) Math.max(startX, endX);
                            mDrawingRect.top = (int) Math.min(startY, endY);
                            mDrawingRect.bottom = (int) Math.max(startY, endY);
                            Bitmap bitmap = mDecoder.decodeRegion(mDrawingRect, mOptions);
                            drawingView.drawingBitmap = scaleBitmapByMatrix(bitmap, 1 / mCurrentScale, 1 / mCurrentScale);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        //算出绘制图片所占的区域
                        mDrawingImageMatrix.invert(mDrawingImageReverseMatrix);
                        mDrawingImageReverseMatrix.getValues(mMatrixValues);
                        drawingView.region = new Region();
                        //利用逆转矩阵反算出绘制图片在原始合成图的左上角坐标
                        float regionLeft = (mDrawingRect.left) * mMatrixValues[0] + (mDrawingRect.top) * mMatrixValues[1] + mMatrixValues[2];
                        float regionTop = (mDrawingRect.left) * mMatrixValues[3] + (mDrawingRect.top) * mMatrixValues[4] + mMatrixValues[5];
                        //利用逆转矩阵反算出绘制图片在原始合成图的右下角坐标
                        float regionRight = (mDrawingRect.right) * mMatrixValues[0] + (mDrawingRect.bottom) * mMatrixValues[1] + mMatrixValues[2];
                        float regionBottom = (mDrawingRect.right) * mMatrixValues[3] + (mDrawingRect.bottom) * mMatrixValues[4] + mMatrixValues[5];

                        drawingView.region.set((int) regionLeft, (int) regionTop, (int) regionRight, (int) regionBottom);

                        //算出图片点击响应区域
                        drawingView.responseRegion = new Region();
                        //利用逆转矩阵反算出绘制图片在原始合成图的左上角坐标
                        float responseRegionLeft = (mDrawingRect.left - 30) * mMatrixValues[0] + (mDrawingRect.top - 30) * mMatrixValues[1] + mMatrixValues[2];
                        float responseRegionTop = (mDrawingRect.left - 30) * mMatrixValues[3] + (mDrawingRect.top - 30) * mMatrixValues[4] + mMatrixValues[5];
                        //利用逆转矩阵反算出绘制图片在原始合成图的右下角坐标
                        float responseRegionRight = (mDrawingRect.right + 30) * mMatrixValues[0] + (mDrawingRect.bottom + 30) * mMatrixValues[1] + mMatrixValues[2];
                        float responseRegionBottom = (mDrawingRect.right + 30) * mMatrixValues[3] + (mDrawingRect.bottom + 30) * mMatrixValues[4] + mMatrixValues[5];

                        drawingView.responseRegion.set((int) responseRegionLeft, (int) responseRegionTop, (int) responseRegionRight, (int) responseRegionBottom);

                        //设置绘图对象的矩阵（相对于背景图片的位置矩阵）
                        float transX = regionLeft;
                        float transY = regionTop;
                        drawingView.drawingMatrix.setTranslate(transX, transY);

                        mDrawingViewList.add(drawingView);
                        mDrawingCanvasBitmap = null;
                        invalidate();
                    }
                    setState(State.NONE);
                }

                break;

            case MotionEvent.ACTION_POINTER_DOWN:

                setState(State.DRAG);
                startPoint1.x = event.getX(1);
                startPoint1.y = event.getY(1);

                break;

            case MotionEvent.ACTION_POINTER_UP:
                if (mCurrentState == State.DRAG || mCurrentState == State.ZOOM) {
                    isFromDragScaleToDrawing = true;
                }
                setState(State.DRAWING);
                Log.i(TAG, "ACTION_POINTER_UP pointer count:" + event.getPointerCount());

                break;

            default:
                break;

        }
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {

        //绘制背景图片
        if (mDrawingBitmap != null) {
            canvas.drawBitmap(mDrawingBitmap, mDrawingImageMatrix, null);
        }

        //绘制画布图片（背景透明）
        if (mDrawingCanvasBitmap != null) {
            canvas.drawBitmap(mDrawingCanvasBitmap, 0, 0, null);
        }

        //绘制新添加的DrawingView
        for (DrawingView drawingView : mDrawingViewList) {
            if (drawingView.drawingBitmap != null) {
                Matrix matrix = new Matrix(drawingView.drawingMatrix);
                matrix.postConcat(mDrawingImageMatrix);
                canvas.drawBitmap(drawingView.drawingBitmap, matrix, null);
                if (drawingView.drawRectangleFlag) {
                    //利用矩阵算出矩形框绘制的位置
                    mDrawingImageMatrix.getValues(mMatrixValues);
                    Rect rect = drawingView.region.getBounds();
                    float left = rect.left * mMatrixValues[0] + rect.top * mMatrixValues[1] + mMatrixValues[2];
                    float top = rect.left * mMatrixValues[3] + rect.top * mMatrixValues[4] + mMatrixValues[5];
                    //利用逆转矩阵反算出绘制图片在原始合成图的右下角坐标
                    float right = rect.right * mMatrixValues[0] + rect.bottom * mMatrixValues[1] + mMatrixValues[2];
                    float bottom = rect.right * mMatrixValues[3] + rect.bottom * mMatrixValues[4] + mMatrixValues[5];

                    float drawLeft = left - 30;
                    float drawTop = top - 30;
                    float drawRight = right + 30;
                    float drawBottom = bottom + 30;

                    canvas.drawLine(drawLeft, drawTop, drawRight, drawTop, mDrawingRectanglePaint);
                    canvas.drawLine(drawRight, drawTop, drawRight, drawBottom, mDrawingRectanglePaint);
                    canvas.drawLine(drawRight, drawBottom, drawLeft, drawBottom, mDrawingRectanglePaint);
                    canvas.drawLine(drawLeft, drawBottom, drawLeft, drawTop, mDrawingRectanglePaint);

                    //绘制删除图标
                    canvas.drawBitmap(mDeleteIconBitmap, drawLeft - 20, drawTop - 20, null);
                }
            }
        }
    }

    /**
     * 保存合成图
     */
    public Bitmap saveCompositeBitmap(){
        float compositeLeft = 0;
        float compositeTop = 0;
        float compositeRight = 0;
        float compositeBottom = 0;
        for (DrawingView drawingView : mDrawingViewList){
            Region region = drawingView.region;
            compositeLeft = Math.min(region.getBounds().left, 0);
            compositeTop = Math.min(region.getBounds().top, 0);
            compositeRight = Math.max(region.getBounds().right, mOriginalImageWidth);
            compositeBottom = Math.max(region.getBounds().bottom, mOriginalImageHeight);
        }
        int compositeBitmapWidth = (int)(compositeRight - compositeLeft);
        int compositeBitmapHeight = (int)(compositeBottom - compositeTop);
        Bitmap compositeBitmap = Bitmap.createBitmap(compositeBitmapWidth, compositeBitmapHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(compositeBitmap);

        //绘制背景图片
        canvas.drawBitmap(mDrawingBitmap, -compositeLeft, -compositeTop, null);

        //绘制DrawingView
        for (DrawingView drawingView : mDrawingViewList){
            if (drawingView.drawingBitmap != null){
                canvas.drawBitmap(drawingView.drawingBitmap, drawingView.region.getBounds().left - compositeLeft, drawingView.region.getBounds().top - compositeTop, null);
            }
        }

        canvas.save(Canvas.ALL_SAVE_FLAG);
        // 存储新合成的图片
        canvas.restore();
        return compositeBitmap;
    }

    /**
     * 用于绘制直线的起始位置坐标X
     */
    private float startX;

    /**
     * 用于绘制直线的起始位置坐标Y
     */
    private float startY;

    /**
     * 用于绘制直线的终点位置坐标X
     */
    private float endX;

    /**
     * 用于绘制直线的终点位置坐标X
     */
    private float endY;

    /**
     * 从拖动或者缩放跳转到绘制状态的标志
     */
    private boolean isFromDragScaleToDrawing;

    /**
     * 绘制图片对象列表
     */
    private List<DrawingView> mDrawingViewList = new ArrayList<>();

    private BitmapRegionDecoder mDecoder;

    private static final BitmapFactory.Options mOptions = new BitmapFactory.Options();

    static {
        mOptions.inPreferredConfig = Bitmap.Config.RGB_565;
    }

    /**
     * 待显示图片块的矩形范围
     */
    private Rect mDrawingRect = new Rect();

    /**
     * 使用Matrix进行图片的缩放
     *
     * @param bitmap 原始的Bitmap
     * @param scaleW 目标缩放比例
     * @param scaleH 目标缩放比例
     * @return 缩放后的Bitmap
     */
    public static Bitmap scaleBitmapByMatrix(Bitmap bitmap, float scaleW, float scaleH) {
        if (bitmap == null)
            return null;
        int w = bitmap.getWidth();   //？？？会出现w=0，h=0的情况
        int h = bitmap.getHeight();
        Matrix matrix = new Matrix();
        matrix.postScale(scaleW, scaleH); // 长和宽放大缩小的比例
        return Bitmap.createBitmap(bitmap, 0, 0, w, h, matrix, true);
    }

    /**
     * 将Bitmap转换成InputStream
     */
    public static InputStream bitmap2InputStream(Bitmap bm) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bm.compress(Bitmap.CompressFormat.PNG, 100, baos); //注意：压缩成JPEG的话，默认背景颜色变成黑色。
        InputStream is = new ByteArrayInputStream(baos.toByteArray());
        return is;
    }

    /**
     * 将InputStream转换成Bitmap
     */
    public static Bitmap inputStream2Bitmap(InputStream is) {
        return BitmapFactory.decodeStream(is);
    }

    /**
     * 设置图片的缩放比例
     *
     * @param deltaScale
     * @param focusX
     * @param focusY
     */
    private void scaleImage(double deltaScale, float focusX, float focusY) {
        fixScaleTranslate();
        Log.i(TAG, "focusX: " + focusX + ";focusY: " + focusY);
        float[] imageMatrix = new float[9];
        mDrawingImageMatrix.getValues(imageMatrix);
        Log.i(TAG, "scaleImage before scale scaleX:" + imageMatrix[Matrix.MSCALE_X] + ", scaleY:" + imageMatrix[Matrix.MSCALE_Y] + ",transX:" + imageMatrix[Matrix.MTRANS_X] + ",transY:" + imageMatrix[Matrix.MTRANS_Y]);
        if (imageMatrix[Matrix.MSCALE_X] * deltaScale > mMaxScale) {
//            mImageMatrix.setScale(mMaxScale, mMaxScale, focusX, focusY);
            return;
        } else if (imageMatrix[Matrix.MSCALE_X] * deltaScale < mMinScale) {
//            mImageMatrix.setScale(mMinScale, mMinScale, focusX, focusY);
            return;
        } else {
            mDrawingImageMatrix.postScale((float) deltaScale, (float) deltaScale, focusX, focusY);
        }
        mDrawingImageMatrix.getValues(imageMatrix);
        Log.i(TAG, "scaleImage after scale scaleX:" + imageMatrix[Matrix.MSCALE_X] + ", scaleY:" + imageMatrix[Matrix.MSCALE_Y] + ",transX:" + imageMatrix[Matrix.MTRANS_X] + ",transY:" + imageMatrix[Matrix.MTRANS_Y]);
        mCurrentScale = imageMatrix[Matrix.MSCALE_X];
        Log.i(TAG, "scaleImage scaleX:" + imageMatrix[Matrix.MSCALE_X] + ",scaleY:" + imageMatrix[Matrix.MSCALE_Y]);
        fixScaleTranslate();
        invalidate();
    }

    /**
     * 算出两点之间的距离
     *
     * @return
     */
    private float getPointDistance(PointF startPoint, PointF endPoint) {
        float distance;
        distance = (float) Math.sqrt(Math.pow(endPoint.x - startPoint.x, 2) + Math.pow(endPoint.y - startPoint.y, 2));
        return distance;
    }

    private PointF startPoint0 = new PointF();

    private PointF endPoint0 = new PointF();

    private PointF startPoint1 = new PointF();

    private PointF endPoint1 = new PointF();

    /**
     * 绘图Canvas对象
     */
    private Canvas mDrawingCanvas;

    /**
     * 绘图Canvas的Bitmap对象
     */
    private Bitmap mDrawingCanvasBitmap;

    /**
     * 生成绘图Canvas的Bitmap
     *
     * @return
     */
    private Bitmap getDrawingCanvasBitmap() {
        Bitmap canvasBitmap = Bitmap.createBitmap(mViewWidth, mViewHeight, Bitmap.Config.ARGB_8888);
        mDrawingCanvas = new Canvas(canvasBitmap);
        mDrawingCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        return canvasBitmap;
    }

    /**
     * 修复缩放后的图片的位置
     */
    private void fixScaleTranslate() {
        fixTrans();
        mDrawingImageMatrix.getValues(mMatrixValues);
        Log.i(TAG, "fixScaleTranslate trans_x:" + mMatrixValues[Matrix.MTRANS_X] + ";trans_y:" + mMatrixValues[Matrix.MTRANS_Y]);
        //图片大小小于ImageView时，居中显示缩放后的图片
        if (getImageWidth() < mViewWidth) {
            mMatrixValues[Matrix.MTRANS_X] = (mViewWidth - getImageWidth()) / 2;
        }
        if (getImageHeight() < mViewHeight) {
            Log.i(TAG, "fixScaleTranslate getImageHeight() < mViewHeight mViewHeight:" + mViewHeight + ",ImageHeight:" + getImageHeight());
            mMatrixValues[Matrix.MTRANS_Y] = (mViewHeight - getImageHeight()) / 2;
            Log.i(TAG, "fixScaleTranslate MTRANS_Y:" + mMatrixValues[Matrix.MTRANS_Y]);
        }
        mDrawingImageMatrix.setValues(mMatrixValues);
    }

    /**
     * 移动缩放后的图片，保持图片缩放后还是占满ImageView（防止图片缩放后，位置偏向一边，使一边留白）
     */
    private void fixTrans() {
        mDrawingImageMatrix.getValues(mMatrixValues);
        float transX = mMatrixValues[Matrix.MTRANS_X];
        float transY = mMatrixValues[Matrix.MTRANS_Y];

        float fixTransX = getFixTrans(transX, mViewWidth, getImageWidth());
        float fixTransY = getFixTrans(transY, mViewHeight, getImageHeight());

        if (fixTransX != 0 || fixTransY != 0) {
            mDrawingImageMatrix.postTranslate(fixTransX, fixTransY);
        }
    }

    /**
     * 得到缩放后的图片需要修复的位移
     *
     * @param trans       当前图片的位移大小
     * @param viewSize    ImageView的尺寸
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
     * 获取当前图片的宽度
     *
     * @return
     */
    private float getImageWidth() {
        return mCurrentScale * mOriginalImageWidth;
    }

    /**
     * 获取当前图片的高度
     *
     * @return
     */
    private float getImageHeight() {
        return mCurrentScale * mOriginalImageHeight;
    }
}
