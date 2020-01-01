package com.screen;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.jar.Attributes;

import cn.forward.androids.utils.ImageUtils;
import cn.forward.androids.utils.Util;
import cn.hzw.doodle.DoodleColor;
import cn.hzw.doodle.DoodleItemBase;
import cn.hzw.doodle.DoodlePen;
import cn.hzw.doodle.DoodleShape;
import cn.hzw.doodle.IDoodleListener;
import cn.hzw.doodle.core.IDoodle;
import cn.hzw.doodle.core.IDoodleColor;
import cn.hzw.doodle.core.IDoodleItem;
import cn.hzw.doodle.core.IDoodlePen;
import cn.hzw.doodle.core.IDoodleShape;
import cn.hzw.doodle.core.IDoodleTouchDetector;

import static cn.hzw.doodle.util.DrawUtil.drawCircle;
import static cn.hzw.doodle.util.DrawUtil.drawRect;
import static cn.hzw.doodle.util.DrawUtil.rotatePoint;

/**
 * 涂鸦框架
 * Created by huangziwei on 2016/9/3.
 */
public class DoodleView extends View implements IDoodle {

    public final static float MAX_SCALE = 5f; // 最大缩放倍数
    public final static float MIN_SCALE = 0.25f; // 最小缩放倍数
    public final static int DEFAULT_SIZE = 6; // 默认画笔大小

    public static final int ERROR_INIT = -1;
    public static final int ERROR_SAVE = -2;

    private IDoodleListener mDoodleListener;

    private Bitmap mBitmap; // 当前涂鸦的原图（旋转后）

    private float mCenterScale; // 图片适应屏幕时的缩放倍数
    private int mCenterHeight, mCenterWidth;// 图片适应屏幕时的大小（View窗口坐标系上的大小）
    private float mCentreTranX, mCentreTranY;// 图片在适应屏幕时，位于居中位置的偏移（View窗口坐标系上的偏移）

    private float mRotateScale = 1;  // 在旋转后适应屏幕时的缩放倍数
    private float mRotateTranX, mRotateTranY; // 旋转后适应屏幕居中时的偏移

    private float mScale = 1; // 在适应屏幕时的缩放基础上的缩放倍数 （ 图片真实的缩放倍数为 mCenterScale*mScale ）
    private float mTransX = 0, mTransY = 0; // 图片在适应屏幕且处于居中位置的基础上的偏移量（ 图片真实偏移量为mCentreTranX + mTransX，View窗口坐标系上的偏移）
    private float mMinScale = MIN_SCALE; // 最小缩放倍数
    private float mMaxScale = MAX_SCALE; // 最大缩放倍数

    private float mSize;
    private IDoodleColor mColor; // 画笔底色

    private boolean isJustDrawOriginal; // 是否只绘制原图

    private boolean mIsDrawableOutside = false; // 触摸时，图片区域外是否绘制涂鸦轨迹
    private boolean mReady = false;

    // 保存涂鸦操作，便于撤销
    private CopyOnWriteArrayList<IDoodleItem> mItemStack = new CopyOnWriteArrayList<IDoodleItem>();

    private IDoodlePen mPen;
    private IDoodleShape mShape;

    private float mTouchX, mTouchY;
    private boolean mEnableZoomer = false; // 放大镜功能
    private boolean mEnableOverview = true; // 全图预览功能，建立在放大镜功能开启的前提下
    private float mLastZoomerY;
    private float mZoomerRadius;
    private Path mZoomerPath;
    private float mZoomerScale = 0; // 放大镜的倍数
    private Paint mZooomerPaint, mZoomerTouchPaint;
    private int mZoomerHorizonX; // 放大器的位置的x坐标，使其水平居中
    private boolean mIsScrollingDoodle = false; // 是否正在滑动，只要用于标志触摸时才显示放大镜

    private float mDoodleSizeUnit = 1; // 长度单位，不同大小的图片的长度单位不一样。该单位的意义同dp的作用类似，独立于图片之外的单位长度
    private int mDoodleRotateDegree = 0; // 相对于初始图片旋转的角度

    // 手势相关
    private IDoodleTouchDetector mDefaultTouchDetector;
    private Map<IDoodlePen, IDoodleTouchDetector> mTouchDetectorMap = new HashMap<>();

    private DoodleViewInner mInner;
    private RectF mDoodleBound = new RectF();
    private PointF mTempPoint = new PointF();

    private boolean mIsEditMode = false; //是否是编辑模式，可移动缩放涂鸦

    public DoodleView(Context context){
        this(context,null);
    }
    public DoodleView(Context context, AttributeSet attrs){
        super(context,attrs);
    }

    public DoodleView(Context context, Bitmap bitmap, IDoodleListener listener) {
        this(context, bitmap, listener, null);
    }

    /**
     * @param context
     * @param bitmap
     * @param listener
     * @param defaultDetector 默认手势识别器
     */
    public DoodleView(Context context, Bitmap bitmap, IDoodleListener listener, IDoodleTouchDetector defaultDetector) {
        super(context);

        // 关闭硬件加速，某些绘图操作不支持硬件加速
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        mBitmap = bitmap;
        mDoodleListener = listener;
        if (mDoodleListener == null) {
            throw new RuntimeException("IDoodleListener is null!!!");
        }
        if (mBitmap == null) {
            throw new RuntimeException("Bitmap is null!!!");
        }

        mScale = 1f;
        mColor = new DoodleColor(Color.RED);

        mPen = DoodlePen.BRUSH;
        mShape = DoodleShape.HAND_WRITE;

        mZooomerPaint = new Paint();
        mZooomerPaint.setColor(0xaaffffff);
        mZooomerPaint.setStyle(Paint.Style.STROKE);
        mZooomerPaint.setAntiAlias(true);
        mZooomerPaint.setStrokeJoin(Paint.Join.ROUND);
        mZooomerPaint.setStrokeCap(Paint.Cap.ROUND);// 圆滑
        mZooomerPaint.setStrokeWidth(Util.dp2px(getContext(), 10));

        mZoomerTouchPaint = new Paint();
        mZoomerTouchPaint.setStyle(Paint.Style.STROKE);
        mZoomerTouchPaint.setAntiAlias(true);
        mZoomerTouchPaint.setStrokeJoin(Paint.Join.ROUND);
        mZoomerTouchPaint.setStrokeCap(Paint.Cap.ROUND);// 圆滑

        mDefaultTouchDetector = defaultDetector;

        mInner = new DoodleViewInner();
    }


    public void setLaunchListener(Context context, Bitmap bitmap, IDoodleListener listener, IDoodleTouchDetector defaultDetector){
        // 关闭硬件加速，某些绘图操作不支持硬件加速
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        mBitmap = bitmap;
        mDoodleListener = listener;
        if (mDoodleListener == null) {
            throw new RuntimeException("IDoodleListener is null!!!");
        }
        if (mBitmap == null) {
            throw new RuntimeException("Bitmap is null!!!");
        }

        mScale = 1f;
        mColor = new DoodleColor(Color.RED);

        mPen = DoodlePen.BRUSH;
        mShape = DoodleShape.HAND_WRITE;

        mZooomerPaint = new Paint();
        mZooomerPaint.setColor(0xaaffffff);
        mZooomerPaint.setStyle(Paint.Style.STROKE);
        mZooomerPaint.setAntiAlias(true);
        mZooomerPaint.setStrokeJoin(Paint.Join.ROUND);
        mZooomerPaint.setStrokeCap(Paint.Cap.ROUND);// 圆滑
        mZooomerPaint.setStrokeWidth(Util.dp2px(getContext(), 10));

        mZoomerTouchPaint = new Paint();
        mZoomerTouchPaint.setStyle(Paint.Style.STROKE);
        mZoomerTouchPaint.setAntiAlias(true);
        mZoomerTouchPaint.setStrokeJoin(Paint.Join.ROUND);
        mZoomerTouchPaint.setStrokeCap(Paint.Cap.ROUND);// 圆滑

        mDefaultTouchDetector = defaultDetector;

        mInner = new DoodleViewInner();

    }


    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        initDoodleBitmap();
        if (!mReady) {
            mDoodleListener.onReady(this);
            mReady = true;
        }
    }

    private Matrix mTouchEventMatrix = new Matrix();
    private OnTouchListener mOnTouchListener;

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (mOnTouchListener != null) {
            if (mOnTouchListener.onTouch(this, event)) {
                return true;
            }
        }
        mTouchX = event.getX();
        mTouchY = event.getY();

        // 把事件转发给innerView，避免在区域外不可点击
        MotionEvent transformedEvent = MotionEvent.obtain(event);
//        final float offsetX = mInner.getScrollX() - mInner.getLeft();
//        final float offsetY = mInner.getScrollY() - mInner.getTop();
//        transformedEvent.offsetLocation(offsetX, offsetY);
        mTouchEventMatrix.reset();
        mTouchEventMatrix.setRotate(-mDoodleRotateDegree, getWidth() / 2, getHeight() / 2);
        transformedEvent.transform(mTouchEventMatrix);
        boolean handled = mInner.onTouchEvent(transformedEvent);
        transformedEvent.recycle();

        return handled;
    }

    @Override
    public void setOnTouchListener(OnTouchListener l) {
        mOnTouchListener = l;
        super.setOnTouchListener(l);
    }

    private void initDoodleBitmap() {// 不用resize preview
        int w = mBitmap.getWidth();
        int h = mBitmap.getHeight();
        float nw = w * 1f / getWidth();
        float nh = h * 1f / getHeight();
        if (nw > nh) {
            mCenterScale = 1 / nw;
            mCenterWidth = getWidth();
            mCenterHeight = (int) (h * mCenterScale);
        } else {
            mCenterScale = 1 / nh;
            mCenterWidth = (int) (w * mCenterScale);
            mCenterHeight = getHeight();
        }
        // 使图片居中
        mCentreTranX = (getWidth() - mCenterWidth) / 2f;
        mCentreTranY = (getHeight() - mCenterHeight) / 2f;

        mZoomerRadius = Math.min(getWidth(), getHeight()) / 4;
        mZoomerPath = new Path();
        mZoomerPath.addCircle(mZoomerRadius, mZoomerRadius, mZoomerRadius, Path.Direction.CCW);
        mZoomerHorizonX = (int) (Math.min(getWidth(), getHeight()) / 2 - mZoomerRadius);

        mDoodleSizeUnit = Util.dp2px(getContext(), 1) / mCenterScale;

        if (!mReady) { // 只有初始化时才需要设置画笔大小
            mSize = DEFAULT_SIZE * mDoodleSizeUnit;
        }
        // 居中适应屏幕
        mTransX = mTransY = 0;
        mScale = 1;

        refresh();
    }

    /**
     * 获取当前图片在View坐标系中的矩型区域
     *
     * @return
     */
    public RectF getDoodleBound() {
        float width = mCenterWidth * mRotateScale * mScale;
        float height = mCenterHeight * mRotateScale * mScale;
        if (mDoodleRotateDegree % 90 == 0) { // 对0,90,180，270度旋转做简化计算
            if (mDoodleRotateDegree == 0) {
                mTempPoint.x = toTouchX(0);
                mTempPoint.y = toTouchY(0);
            } else if (mDoodleRotateDegree == 90) {
                mTempPoint.x = toTouchX(0);
                mTempPoint.y = toTouchY(mBitmap.getHeight());
                float t = width;
                width = height;
                height = t;
            } else if (mDoodleRotateDegree == 180) {
                mTempPoint.x = toTouchX(mBitmap.getWidth());
                mTempPoint.y = toTouchY(mBitmap.getHeight());
            } else if (mDoodleRotateDegree == 270) {
                mTempPoint.x = toTouchX(mBitmap.getWidth());
                mTempPoint.y = toTouchY(0);
                float t = width;
                width = height;
                height = t;
            }
            rotatePoint(mTempPoint, mDoodleRotateDegree, mTempPoint.x, mTempPoint.y, getWidth() / 2, getHeight() / 2);
            mDoodleBound.set(mTempPoint.x, mTempPoint.y, mTempPoint.x + width, mTempPoint.y + height);
        } else {
            // 转换成屏幕坐标
            // 左上
            float ltX = toTouchX(0);
            float ltY = toTouchY(0);
            //右下
            float rbX = toTouchX(mBitmap.getWidth());
            float rbY = toTouchY(mBitmap.getHeight());
            // 左下
            float lbX = toTouchX(0);
            float lbY = toTouchY(mBitmap.getHeight());
            //右上
            float rtX = toTouchX(mBitmap.getWidth());
            float rtY = toTouchY(0);

            //转换到View坐标系
            rotatePoint(mTempPoint, mDoodleRotateDegree, ltX, ltY, getWidth() / 2, getHeight() / 2);
            ltX = mTempPoint.x;
            ltY = mTempPoint.y;
            rotatePoint(mTempPoint, mDoodleRotateDegree, rbX, rbY, getWidth() / 2, getHeight() / 2);
            rbX = mTempPoint.x;
            rbY = mTempPoint.y;
            rotatePoint(mTempPoint, mDoodleRotateDegree, lbX, lbY, getWidth() / 2, getHeight() / 2);
            lbX = mTempPoint.x;
            lbY = mTempPoint.y;
            rotatePoint(mTempPoint, mDoodleRotateDegree, rtX, rtY, getWidth() / 2, getHeight() / 2);
            rtX = mTempPoint.x;
            rtY = mTempPoint.y;

            mDoodleBound.left = Math.min(Math.min(ltX, rbX), Math.min(lbX, rtX));
            mDoodleBound.top = Math.min(Math.min(ltY, rbY), Math.min(lbY, rtY));
            mDoodleBound.right = Math.max(Math.max(ltX, rbX), Math.max(lbX, rtX));
            mDoodleBound.bottom = Math.max(Math.max(ltY, rbY), Math.max(lbY, rtY));
        }
        return mDoodleBound;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (mBitmap.isRecycled()) {
            return;
        }

        // draw inner
        canvas.save();
        canvas.rotate(mDoodleRotateDegree, getWidth() / 2, getHeight() / 2);
        mInner.onDraw(canvas);
        canvas.restore();

        /*// test
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setColor(Color.BLUE);
        mPaint.setStrokeWidth(20);
        canvas.drawRect(getDoodleBound(), mPaint);*/

        if (mIsScrollingDoodle && mEnableZoomer && mZoomerScale > 0) { //启用放大镜
            canvas.save(); // ***

            float unitSize = getUnitSize();
            if (mTouchY <= mZoomerRadius * 2) { // 在放大镜的范围内， 把放大镜放在底部
                mLastZoomerY = getHeight() - mZoomerRadius * 2;
            } else if (mTouchY >= getHeight() - mZoomerRadius * 2) {
                mLastZoomerY = 0;
            }
            canvas.translate(mZoomerHorizonX, mLastZoomerY);
            canvas.clipPath(mZoomerPath);
            canvas.drawColor(0xff000000);

            canvas.save();
            float scale = mZoomerScale / mScale; // 除以mScale，无论当前图片缩放多少，都产生图片在居中状态下缩放mAmplifierScale倍的效果
            canvas.scale(scale, scale);
            canvas.translate(-mTouchX + mZoomerRadius / scale, -mTouchY + mZoomerRadius / scale);
            // draw inner
            canvas.rotate(mDoodleRotateDegree, getWidth() / 2, getHeight() / 2);
            mInner.onDraw(canvas);
            // 触摸点
            float left = getAllTranX();
            float top = getAllTranY();
            // 画布和图片共用一个坐标系，只需要处理屏幕坐标系到图片（画布）坐标系的映射关系
            canvas.translate(left, top); // 偏移画布
            scale = getAllScale();
            canvas.scale(scale, scale); // 缩放画布
            mZoomerTouchPaint.setStrokeWidth(unitSize / 2);
            float radius = mSize / 2 - unitSize / 2;
            float radius2 = radius - unitSize / 2;
            if (radius <= 1) {
                radius = 1;
                radius2 = radius / 2;
                mZoomerTouchPaint.setStrokeWidth(mSize);
            }
            mZoomerTouchPaint.setColor(0xaa000000);
            drawCircle(canvas, toX(mTouchX), toY(mTouchY), radius, mZoomerTouchPaint);
            mZoomerTouchPaint.setColor(0xaaffffff);
            drawCircle(canvas, toX(mTouchX), toY(mTouchY), radius2, mZoomerTouchPaint);
            canvas.restore();

            // 画放大器的边框
            drawCircle(canvas, mZoomerRadius, mZoomerRadius, mZoomerRadius, mZooomerPaint);

            canvas.restore(); // ***

            // overview
            canvas.save();
            canvas.translate(mZoomerHorizonX, mLastZoomerY);
            scale = (mZoomerRadius / 2) / getWidth();
            canvas.scale(scale, scale);
            float strokeWidth = 1 / scale;
            canvas.clipRect(-strokeWidth, -strokeWidth, getWidth() + strokeWidth, getHeight() + strokeWidth);
            canvas.drawColor(0x88888888);
            canvas.save();
            canvas.rotate(mDoodleRotateDegree, getWidth() / 2, getHeight() / 2);
            float tempScale = mScale;
            float tempTransX = mTransX;
            float tempTransY = mTransY;
            mScale = 1;
            mTransX = mTransY = 0;
            mInner.onDraw(canvas);
            mScale = tempScale;
            mTransX = tempTransX;
            mTransY = tempTransY;
            canvas.restore();
            mZoomerTouchPaint.setStrokeWidth(strokeWidth);
            mZoomerTouchPaint.setColor(0xaa000000);
            drawRect(canvas, 0, 0, getWidth(), getHeight(), mZoomerTouchPaint);
            mZoomerTouchPaint.setColor(0xaaffffff);
            drawRect(canvas, strokeWidth, strokeWidth, getWidth() - strokeWidth, getHeight() - strokeWidth, mZoomerTouchPaint);
            canvas.restore();
        }

    }

    private void doDraw(Canvas canvas) {
        float left = getAllTranX();
        float top = getAllTranY();

        // 画布和图片共用一个坐标系，只需要处理屏幕坐标系到图片（画布）坐标系的映射关系
        canvas.translate(left, top); // 偏移画布
        float scale = getAllScale();
        canvas.scale(scale, scale); // 缩放画布

        if (isJustDrawOriginal) { // 只绘制原图
            canvas.drawBitmap(mBitmap, 0, 0, null);
            return;
        }
        // 绘制涂鸦后的图片
        canvas.drawBitmap(mBitmap, 0, 0, null);

        boolean canvasClipped = false;
        canvas.save(); // 1
        if (!mIsDrawableOutside) { // 裁剪绘制区域为图片区域
            canvasClipped = true;
            canvas.clipRect(0, 0, mBitmap.getWidth(), mBitmap.getHeight());
        }
        for (IDoodleItem item : mItemStack) {
            if (!item.isNeedClipOutside()) { // 1.不需要裁剪
                if (canvasClipped) {
                    canvas.restore();
                }

                item.draw(canvas);

                if (canvasClipped) { // 2.恢复裁剪
                    canvas.save();
                    canvas.clipRect(0, 0, mBitmap.getWidth(), mBitmap.getHeight());
                }
            } else {
                item.draw(canvas);
            }
        }

        // draw at the top
        for (IDoodleItem item : mItemStack) {
            if (!item.isNeedClipOutside()) { // 1.不需要裁剪
                if (canvasClipped) {
                    canvas.restore();
                }
                item.drawAtTheTop(canvas);

                if (canvasClipped) { // 2.恢复裁剪
                    canvas.save();
                    canvas.clipRect(0, 0, mBitmap.getWidth(), mBitmap.getHeight());
                }
            } else {
                item.drawAtTheTop(canvas);
            }
        }

        canvas.restore();

        if (mPen != null) {
            mPen.drawHelpers(canvas, this);
        }
        if (mShape != null) {
            mShape.drawHelpers(canvas, this);
        }
    }

    public float getAllScale() {
        return mCenterScale * mRotateScale * mScale;
    }

    public float getAllTranX() {
        return mCentreTranX + mRotateTranX + mTransX;
    }

    public float getAllTranY() {
        return mCentreTranY + mRotateTranY + mTransY;
    }

    /**
     * 将屏幕触摸坐标x转换成在图片中的坐标
     */
    public final float toX(float touchX) {
        return (touchX - getAllTranX()) / getAllScale();
    }

    /**
     * 将屏幕触摸坐标y转换成在图片中的坐标
     */
    public final float toY(float touchY) {
        return (touchY - getAllTranY()) / getAllScale();
    }

    /**
     * 将图片坐标x转换成屏幕触摸坐标
     */
    public final float toTouchX(float x) {
        return x * getAllScale() + getAllTranX();
    }

    /**
     * 将图片坐标y转换成屏幕触摸坐标
     */
    public final float toTouchY(float y) {
        return y * getAllScale() + getAllTranY();
    }

    /**
     * 坐标换算
     * （公式由toX()中的公式推算出）
     *
     * @param touchX  触摸坐标
     * @param doodleX 在涂鸦图片中的坐标
     * @return 偏移量
     */
    public final float toTransX(float touchX, float doodleX) {
        return -doodleX * getAllScale() + touchX - mCentreTranX - mRotateTranX;
    }

    public final float toTransY(float touchY, float doodleY) {
        return -doodleY * getAllScale() + touchY - mCentreTranY - mRotateTranY;
    }

    /**
     * 根据画笔绑定手势识别器
     *
     * @param pen
     * @param detector
     */
    public void bindTouchDetector(IDoodlePen pen, IDoodleTouchDetector detector) {
        if (pen == null) {
            return;
        }
        mTouchDetectorMap.put(pen, detector);
    }

    /**
     * 获取画笔绑定的手势识别器
     *
     * @param pen
     */
    public IDoodleTouchDetector getDefaultTouchDetector(IDoodlePen pen) {
        return mTouchDetectorMap.get(pen);
    }

    /**
     * 移除指定画笔的手势识别器
     *
     * @param pen
     */
    public void removeTouchDetector(IDoodlePen pen) {
        if (pen == null) {
            return;
        }
        mTouchDetectorMap.remove(pen);
    }

    /**
     * 设置默认手势识别器
     *
     * @param touchGestureDetector
     */
    public void setDefaultTouchDetector(IDoodleTouchDetector touchGestureDetector) {
        mDefaultTouchDetector = touchGestureDetector;
    }

    /**
     * 默认手势识别器
     *
     * @return
     */
    public IDoodleTouchDetector getDefaultTouchDetector() {
        return mDefaultTouchDetector;
    }

    // ========================= api ================================

    @Override
    public void refresh() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            invalidate();
        } else {
            postInvalidate();
        }
    }

    @Override
    public int getDoodleRotation() {
        return mDoodleRotateDegree;
    }

    /**
     * 相对于初始图片旋转的角度
     *
     * @param degree positive degree means rotate right, negative degree means rotate left
     */

    @Override
    public void setDoodleRotation(int degree) {
        mDoodleRotateDegree = degree;
        mDoodleRotateDegree = mDoodleRotateDegree % 360;
        if (mDoodleRotateDegree < 0) {
            mDoodleRotateDegree = 360 + mDoodleRotateDegree;
        }

        // 居中
        RectF rectF = getDoodleBound();
        int w = (int) (rectF.width() / getAllScale());
        int h = (int) (rectF.height() / getAllScale());
        float nw = w * 1f / getWidth();
        float nh = h * 1f / getHeight();
        float scale;
        float tx, ty;
        if (nw > nh) {
            scale = 1 / nw;
        } else {
            scale = 1 / nh;
        }

        int pivotX = mBitmap.getWidth() / 2;
        int pivotY = mBitmap.getHeight() / 2;

        mTransX = mTransY = 0;
        mRotateTranX = mRotateTranY = 0;
        this.mScale = 1;
        mRotateScale = 1;
        float touchX = toTouchX(pivotX);
        float touchY = toTouchY(pivotY);
        mRotateScale = scale / mCenterScale;

        // 缩放后，偏移图片，以产生围绕某个点缩放的效果
        tx = toTransX(touchX, pivotX);
        ty = toTransY(touchY, pivotY);

        mRotateTranX = tx;
        mRotateTranY = ty;

        refresh();
    }

    /**
     * 保存, 回调DoodleListener.onSaved()的线程和调用save()的线程相同
     */
    @Override
    public void save() {
        Bitmap savedBitmap = mBitmap.copy(mBitmap.getConfig(), true);
        Canvas canvas = new Canvas(savedBitmap);
        for (IDoodleItem item : mItemStack) {
            if (item instanceof DoodleItemBase) {
                item.draw(canvas);
            }
        }
        savedBitmap = ImageUtils.rotate(savedBitmap, mDoodleRotateDegree, true);

        mDoodleListener.onSaved(this, savedBitmap, new Runnable() {
            @Override
            public void run() {
                refresh();
            }
        });
    }

    /**
     * 清屏
     */
    @Override
    public void clear() {
        for (int i = 0; i < mItemStack.size(); i++) {
            IDoodleItem item = mItemStack.remove(i);
            item.onRemove();
        }
        mItemStack.clear();
        refresh();
    }

    @Override
    public boolean undo(int step) {
        if (mItemStack.size() > 0) {
            step = Math.min(mItemStack.size(), step);
            IDoodleItem item = mItemStack.get(mItemStack.size() - step);
            removeItem(item);
            return true;
        }
        return false;
    }

    /**
     * 撤销
     */
    @Override
    public boolean undo() {
        return undo(1);
    }

    /**
     * 只绘制原图
     *
     * @param justDrawOriginal
     */
    @Override
    public void setShowOriginal(boolean justDrawOriginal) {
        isJustDrawOriginal = justDrawOriginal;
        refresh();
    }

    @Override
    public boolean isShowOriginal() {
        return isJustDrawOriginal;
    }

    /**
     * 设置画笔底色
     *
     * @param color
     */
    @Override
    public void setColor(IDoodleColor color) {
        mColor = color;
        refresh();
    }

    @Override
    public IDoodleColor getColor() {
        return mColor;
    }

    /**
     * 围绕某个点缩放
     * 图片真实的缩放倍数为 mCenterScale*mScale
     *
     * @param scale
     * @param pivotX 缩放的中心点
     * @param pivotY
     */
    @Override
    public void setDoodleScale(float scale, float pivotX, float pivotY) {
        if (scale < mMinScale) {
            scale = mMinScale;
        } else if (scale > mMaxScale) {
            scale = mMaxScale;
        }

        float touchX = toTouchX(pivotX);
        float touchY = toTouchY(pivotY);
        this.mScale = scale;

        // 缩放后，偏移图片，以产生围绕某个点缩放的效果
        mTransX = toTransX(touchX, pivotX);
        mTransY = toTransY(touchY, pivotY);

        refresh();
    }

    @Override
    public float getDoodleScale() {
        return mScale;
    }

    /**
     * 设置画笔
     *
     * @param pen
     */
    @Override
    public void setPen(IDoodlePen pen) {
        if (pen == null) {
            throw new RuntimeException("Pen can't be null");
        }
        IDoodlePen old = mPen;
        mPen = pen;
        refresh();
    }

    @Override
    public IDoodlePen getPen() {
        return mPen;
    }

    /**
     * 设置画笔形状
     *
     * @param shape
     */
    @Override
    public void setShape(IDoodleShape shape) {
        if (shape == null) {
            throw new RuntimeException("Shape can't be null");
        }
        mShape = shape;
        refresh();
    }

    @Override
    public IDoodleShape getShape() {
        return mShape;
    }

    @Override
    public void setDoodleTranslation(float transX, float transY) {
        mTransX = transX;
        mTransY = transY;
        refresh();
    }

    /**
     * 设置图片G偏移
     *
     * @param transX
     */
    @Override
    public void setDoodleTranslationX(float transX) {
        this.mTransX = transX;
        refresh();
    }

    @Override
    public float getDoodleTranslationX() {
        return mTransX;
    }

    @Override
    public void setDoodleTranslationY(float transY) {
        this.mTransY = transY;
        refresh();
    }

    @Override
    public float getDoodleTranslationY() {
        return mTransY;
    }


    @Override
    public void setSize(float paintSize) {
        mSize = paintSize;
        refresh();
    }

    @Override
    public float getSize() {
        return mSize;
    }

    /**
     * 触摸时，图片区域外是否绘制涂鸦轨迹
     *
     * @param isDrawableOutside
     */
    @Override
    public void setIsDrawableOutside(boolean isDrawableOutside) {
        mIsDrawableOutside = isDrawableOutside;
    }

    /**
     * 触摸时，图片区域外是否绘制涂鸦轨迹
     */
    @Override
    public boolean isDrawableOutside() {
        return mIsDrawableOutside;
    }

    /**
     * 设置放大镜的倍数，当小于等于0时表示不使用放大器功能
     *
     * @param scale
     */
    @Override
    public void setZoomerScale(float scale) {
        mZoomerScale = scale;
        refresh();
    }

    @Override
    public float getZoomerScale() {
        return mZoomerScale;
    }

    /**
     * 设置是否开启放大镜
     *
     * @param enable
     */
    public void enableZoomer(boolean enable) {
        mEnableZoomer = enable;
    }

    /**
     * 是否开启放大镜
     */
    public boolean isEnableZoomer() {
        return mEnableZoomer;
    }

    /**
     * 设置是否开启全图预览功能，开启后可以在放大镜功能下显示全图涂鸦
     *
     * @param enableOverview
     */
    public void enableOverview(boolean enableOverview) {
        mEnableOverview = enableOverview;
    }

    /**
     * 是否开启全图预览功能
     *
     * @return
     */
    public boolean isEnableOverview() {
        return mEnableOverview;
    }

    /**
     * 是否正在滚动涂鸦，只要用于标志触摸时才显示放大镜
     *
     * @return
     */
    public boolean isScrollingDoodle() {
        return mIsScrollingDoodle;
    }

    public void setScrollingDoodle(boolean scrollingDoodle) {
        mIsScrollingDoodle = scrollingDoodle;
        refresh();
    }

    @Override
    public void topItem(IDoodleItem item) {
        if (item == null) {
            throw new RuntimeException("item is null");
        }

        mItemStack.remove(item);
        mItemStack.add(item);
        refresh();
    }

    @Override
    public void bottomItem(IDoodleItem item) {
        if (item == null) {
            throw new RuntimeException("item is null");
        }

        mItemStack.remove(item);
        mItemStack.add(0, item);
        refresh();
    }

    @Override
    public void setDoodleMinScale(float minScale) {
        mMinScale = minScale;
        setDoodleScale(mScale, 0, 0);
    }

    @Override
    public float getDoodleMinScale() {
        return mMinScale;
    }

    @Override
    public void setDoodleMaxScale(float maxScale) {
        mMaxScale = maxScale;
        setDoodleScale(mScale, 0, 0);
    }

    @Override
    public float getDoodleMaxScale() {
        return mMaxScale;
    }

    @Override
    public float getUnitSize() {
        return mDoodleSizeUnit;
    }

    @Override
    public void addItem(IDoodleItem item) {
        if (item == null) {
            throw new RuntimeException("item is null");
        }

        if (this != item.getDoodle()) {
            throw new RuntimeException("the object Doodle is illegal");
        }
        if (mItemStack.contains(item)) {
            throw new RuntimeException("the item has been added");
        }
        mItemStack.add(item);
        item.onAdd();

        refresh();
    }

    @Override
    public void removeItem(IDoodleItem doodleItem) {
        if (!mItemStack.remove(doodleItem)) {
            return;
        }
        doodleItem.onRemove();

        refresh();
    }

    @Override
    public List<IDoodleItem> getAllItem() {
        return mItemStack;
    }

    @Override
    public Bitmap getBitmap() {
        return mBitmap;
    }

    @Override
    public Bitmap getDoodleBitmap() {
        return mBitmap;
    }

    public int getCenterWidth() {
        return mCenterWidth;
    }

    public int getCenterHeight() {
        return mCenterHeight;
    }

    public float getCenterScale() {
        return mCenterScale;
    }

    public float getCentreTranX() {
        return mCentreTranX;
    }

    public float getCentreTranY() {
        return mCentreTranY;
    }

    public float getRotateScale() {
        return mRotateScale;
    }

    public float getRotateTranX() {
        return mRotateTranX;
    }

    public float getRotateTranY() {
        return mRotateTranY;
    }

    /**
     * 是否为编辑模式
     *
     * @return
     */
    public boolean isEditMode() {
        return mIsEditMode;
    }

    public void setEditMode(boolean editMode) {
        mIsEditMode = editMode;
        refresh();
    }

    private class DoodleViewInner {
        public boolean onTouchEvent(MotionEvent event) {
            // 綁定的识别器
            IDoodleTouchDetector detector = mTouchDetectorMap.get(mPen);
            if (detector != null) {
                return detector.onTouchEvent(event);
            }
            // 默认识别器
            if (mDefaultTouchDetector != null) {
                return mDefaultTouchDetector.onTouchEvent(event);
            }
            return false;
        }

        protected void onDraw(Canvas canvas) {
            canvas.save();
            doDraw(canvas);
            canvas.restore();
        }
    }
}
