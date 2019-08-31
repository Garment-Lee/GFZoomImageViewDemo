package com.ligf.gfzoomimageviewdemo.widget;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Region;

/**
 * 绘制图片对象
 * @author Garment
 */
public class DrawingView {

    /**
     * 用于绘制的图片对象
     */
    public Bitmap drawingBitmap;

    /**
     * DrawingView绘制矩阵（相对于背景图片的位置矩阵）
     */
    public Matrix drawingMatrix = new Matrix();

    /**
     * 绘制图片所占的区域（相对于原始大小的背景图）
     */
    public Region region;

    /**
     * 绘制图片点击相应区域
     */
    public Region responseRegion;

    /**
     * 是否需要绘制矩形框，true：绘制，false：不绘制
     */
    public boolean drawRectangleFlag;

}
