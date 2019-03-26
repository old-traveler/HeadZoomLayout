package com.hyc.headzoomlayout;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.support.annotation.IdRes;
import android.support.v4.view.NestedScrollingChild;
import android.support.v4.view.NestedScrollingChildHelper;
import android.support.v4.view.NestedScrollingParent;
import android.support.v4.view.NestedScrollingParentHelper;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ImageView;
import java.util.ArrayList;
import java.util.List;

/**
 * 作者：贺宇成
 * 时间：2019.3.20
 * 描述：可放大头部背景图的布局
 * 支持RecyclerView、ListView、ScrollView、NestScrollView、LinearLayout等
 * 定义布局时需要声明头部视图的id{@link HeadZoomLayout#headViewId}，
 * 同时需要将头部中的背景图片（ImageView对象）scaleType设置为centerCrop
 * {@link ImageView#setScaleType(ImageView.ScaleType)}。
 * 可设置最大下拉距离{@link HeadZoomLayout#mTotalDragDistance}
 * 可设置头部视图最大拉伸比例{@link HeadZoomLayout#maxZoomRatio}
 * 可设置是否放大头部{@link HeadZoomLayout#setZoomEnable(boolean)}
 * 可设置放大头部监听{@link HeadZoomLayout#addOnHeadZoomListener(OnHeadZoomListener)}
 * 可设置下拉加速度变化比例，值越大加速度减少越快{@link HeadZoomLayout#setDragAccelerationRatio(float)}
 */
public class HeadZoomLayout extends ViewGroup implements NestedScrollingParent,
    NestedScrollingChild {

  public static final int INVALID_POINTER = -1;
  /**
   * 头部View的id（必填）
   */
  @IdRes
  private int headViewId = 0;
  private View headView;
  private View childView;
  private float headViewHeight;
  private float mInitialDownY;
  private int mTouchSlop;
  private float mTotalDragDistance;
  private float maxZoomRatio;
  private int mActivePointerId = INVALID_POINTER;
  private ValueAnimator recoverAnimator;
  private NestedScrollingChildHelper mNestedScrollingChildHelper;
  private NestedScrollingParentHelper mNestedScrollingParentHelper;
  private boolean mReturningToStart;
  private float mTotalUnconsumed;
  private boolean mNestedScrollInProgress;
  private final int[] mParentScrollConsumed;
  private final int[] mParentOffsetInWindow;
  private boolean mIsBeingDragged;
  private float mInitialMotionY;
  private float mInitialDownX;
  private float dragAccelerationRatio;
  private List<OnHeadZoomListener> onHeadZoomListeners;
  private boolean isHorizontalMove = false;
  private boolean isVerticalMove = false;
  private float dragDistance = 0;

  public HeadZoomLayout(Context context) {
    this(context, null);
  }

  public HeadZoomLayout(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public HeadZoomLayout(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    mNestedScrollingChildHelper = new NestedScrollingChildHelper(this);
    mNestedScrollingParentHelper = new NestedScrollingParentHelper(this);
    this.mParentScrollConsumed = new int[2];
    this.mParentOffsetInWindow = new int[2];
    initAttrs(context.obtainStyledAttributes(attrs, R.styleable.HeadZoomLayout));
  }

  /**
   * 初始化布局属性
   */
  private void initAttrs(TypedArray typedArray) {
    if (typedArray == null) {
      throw new RuntimeException("headViewId can not be null");
    }
    maxZoomRatio = typedArray.getFloat(R.styleable.HeadZoomLayout_maxZoomRatio, 1.0f);
    headViewId = typedArray.getResourceId(R.styleable.HeadZoomLayout_headViewId, 0);
    this.setEnabled(typedArray.getBoolean(R.styleable.HeadZoomLayout_zoomEnable, true));
    mTotalDragDistance = typedArray.getFloat(R.styleable.HeadZoomLayout_maxDragDistance, 1000f);
    dragAccelerationRatio =
        typedArray.getFloat(R.styleable.HeadZoomLayout_dragAccelerationRatio, 3.0f);
    typedArray.recycle();
  }

  /**
   * 兼容ScrollView等，从子View中请求拦截触摸事件
   */
  @Override
  public void requestDisallowInterceptTouchEvent(boolean b) {
    if ((Build.VERSION.SDK_INT >= 21 || !(this.childView instanceof AbsListView)) && (this.childView
        == null || ViewCompat
        .isNestedScrollingEnabled(this.childView))) {
      super.requestDisallowInterceptTouchEvent(b);
    }
  }

  /**
   * 记录子View将滑动事件交给父View的时Y轴的值
   * 为了防止mInitialMotionY出现误差导致放大异常
   */
  private float lastY;
  /**
   * 是否给子View传递过down事件
   */
  private boolean isDownToChildView = false;

  /**
   * 父布局拦截子View的事件导致子View无法继续继续获取TouchEvent
   * 重写事件分发，手动分发TouchEvent
   */
  @Override
  public boolean dispatchTouchEvent(MotionEvent ev) {
    if (ev.getActionMasked() == MotionEvent.ACTION_MOVE
        && this.isEnabled()
        && this.mReturningToStart
        && isGetTouchEventFromChild(ev)) {
      //更新滑动初始值
      mInitialMotionY = lastY;
      mReturningToStart = false;
    }

    if (getZoomDistance() == 0 && (ev.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN
        || ev.getActionMasked() == MotionEvent.ACTION_POINTER_UP)) {
      try{
        childView.dispatchTouchEvent(ev);
      }catch (IllegalArgumentException e){
        e.printStackTrace();
      }
    }

    if (!super.dispatchTouchEvent(ev)) {
      this.ensureTarget();
      //当下滑时将事件交给子View处理
      if (ev.getActionMasked() == MotionEvent.ACTION_MOVE
          && this.isEnabled()
          && this.mReturningToStart) {
        //给ChildView传递一个down事件（在ScrollView中需要down事件来初始化滑动状态）
        if (!isDownToChildView) {
          ev.setAction(MotionEvent.ACTION_DOWN);
          isDownToChildView = true;
        }
        try {
          lastY = ev.getY(ev.findPointerIndex(ev.getPointerId(mActivePointerId)));
        } catch (IllegalArgumentException e) {
          e.printStackTrace();
          //子View中切换了手指
          if (ev.getY() >= 0){
            lastY = ev.getY();
          }
        }
        return childView.dispatchTouchEvent(ev);
      } else {
        return false;
      }
    } else {
      return true;
    }
  }

  @Override
  public boolean onInterceptTouchEvent(MotionEvent ev) {
    this.ensureTarget();
    this.initHeadView();
    int action = ev.getActionMasked();
    if (this.mReturningToStart && action == MotionEvent.ACTION_DOWN) {
      this.mReturningToStart = false;
    }

    if (this.isDownToChildView && action == MotionEvent.ACTION_DOWN) {
      //复位给ChildView传递down事件标志
      isDownToChildView = false;
    }

    if (action == MotionEvent.ACTION_DOWN){
      this.isHorizontalMove = false;
      this.isVerticalMove = false;
    }

    if (this.isEnabled()
        && !this.isHorizontalMove
        && !this.mReturningToStart
        && this.isChildScrollToTop()
        && !this.mNestedScrollInProgress) {
      int pointerIndex;
      switch (action) {
        case MotionEvent.ACTION_DOWN:
          //记录活跃手指的Id
          this.mActivePointerId = ev.getPointerId(0);
          this.mIsBeingDragged = false;
          pointerIndex = ev.findPointerIndex(this.mActivePointerId);
          if (pointerIndex < 0) {
            return false;
          }

          this.mInitialDownY = ev.getY(pointerIndex);
          this.mInitialDownX = ev.getX(pointerIndex);
          if (getZoomDistance() > 0) {
            return true;
          }
          break;
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL:
          this.mIsBeingDragged = false;
          this.mActivePointerId = INVALID_POINTER;
          this.mReturningToStart = false;
          this.isHorizontalMove = false;
          break;
        case MotionEvent.ACTION_MOVE:
          if (this.mActivePointerId == INVALID_POINTER) {
            return false;
          }

          pointerIndex = ev.findPointerIndex(this.mActivePointerId);
          if (pointerIndex < 0) {
            return false;
          }

          float y = ev.getY(pointerIndex);
          float x = ev.getX(pointerIndex);
          float distanceY = Math.abs(y - mInitialDownY);
          float distanceX = Math.abs(x - mInitialDownX);
          this.startDragging(y);
          if (distanceY > mTouchSlop){
            isVerticalMove = true;
          }
          if (!this.mIsBeingDragged
              && !this.isVerticalMove
              && distanceX > mTouchSlop
              && distanceX > distanceY) {
            //当前为横向滑动时，不拦截事件，兼容横向滑动控件
            isHorizontalMove = true;
            return false;
          }

          if (distanceY < 0 && getZoomDistance() > 0) {
            return true;
          }
          break;
        case MotionEvent.ACTION_POINTER_UP:
          this.onSecondaryPointerUp(ev);
        default:
          break;
      }
      return this.mIsBeingDragged;
    } else {
      return false;
    }
  }

  /**
   * 是否从子View获取TouchEvent处理权限
   */
  private boolean isGetTouchEventFromChild(MotionEvent ev) {
    if (this.mActivePointerId == INVALID_POINTER) {
      return false;
    }

    int pointerIndex = ev.findPointerIndex(this.mActivePointerId);
    if (pointerIndex < 0) {
      return false;
    }
    float y = ev.getY(pointerIndex);
    float yDiff = y - this.lastY;
    return isChildScrollToTop() && yDiff > 0;
  }

  /**
   * 开始拖拽
   */
  private void startDragging(float y) {
    float yDiff = y - this.mInitialDownY;
    if (yDiff > (float) this.mTouchSlop && !this.mIsBeingDragged) {
      if (recoverAnimator != null && recoverAnimator.isRunning()) {
        recoverAnimator.cancel();
      }
      //记录拖拽起始点，并更新拖拽标识
      this.mInitialMotionY = y;
      this.mIsBeingDragged = true;
      dragDistance = 0.0F;
    }
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    final int width = getMeasuredWidth();
    final int height = getMeasuredHeight();
    if (getChildCount() == 0) {
      return;
    }
    if (childView == null) {
      ensureTarget();
    }
    if (childView == null) {
      return;
    }
    final View child = childView;
    final int childLeft = getPaddingLeft();
    final int childTop = getPaddingTop();
    final int childWidth = width - getPaddingLeft() - getPaddingRight();
    final int childHeight = (height - getPaddingTop() - getPaddingBottom());
    child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    this.ensureTarget();
    //测量子View
    this.childView.measure(MeasureSpec.makeMeasureSpec(
        this.getMeasuredWidth() - this.getPaddingLeft() - this.getPaddingRight(),
        MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(
        this.getMeasuredHeight() - this.getPaddingTop() - this.getPaddingBottom(),
        MeasureSpec.EXACTLY));
  }

  @Override
  public boolean performClick() {
    return super.performClick();
  }

  @Override
  public boolean onTouchEvent(MotionEvent ev) {
    int action = ev.getActionMasked();
    this.initHeadView();
    if (this.mReturningToStart && action == MotionEvent.ACTION_DOWN) {
      this.mReturningToStart = false;
    }
    if (this.isDownToChildView && action == MotionEvent.ACTION_DOWN) {
      this.isDownToChildView = false;
    }

    if (action == MotionEvent.ACTION_DOWN){
      this.isHorizontalMove = false;
      this.isVerticalMove = false;
    }

    if (this.isEnabled()
        && !this.isHorizontalMove
        && !this.mReturningToStart
        && isChildScrollToTop()
        && !this.mNestedScrollInProgress) {
      float y;
      float overscrollTop;
      int pointerIndex;
      switch (action) {
        case MotionEvent.ACTION_DOWN:
          this.mActivePointerId = ev.getPointerId(0);
          this.mIsBeingDragged = false;
          break;
        case MotionEvent.ACTION_CANCEL:
        case MotionEvent.ACTION_UP:
          if (action == MotionEvent.ACTION_UP){
            performClick();
          }

          this.mIsBeingDragged = false;
          this.mReturningToStart = false;
          //回弹头部
          this.recoveryHeadView();


          this.mActivePointerId = INVALID_POINTER;
          return false;
        case MotionEvent.ACTION_MOVE:
          pointerIndex = ev.findPointerIndex(this.mActivePointerId);
          if (pointerIndex < 0) {
            return false;
          }

          y = ev.getY(pointerIndex);
          this.startDragging(y);
          if (this.mIsBeingDragged) {
            overscrollTop = (y - this.mInitialMotionY);
            this.mInitialMotionY = y;
            if (overscrollTop > 0){
              overscrollTop *= 0.6f;
            }
            //放大头部
            this.dragDistance += overscrollTop;
            if (this.dragDistance > mTotalDragDistance){
              this.dragDistance = mTotalDragDistance;
            }
            if (this.dragDistance < 0.0F && getZoomDistance() == 0) {
              //头图已经复原，且向下滑动，交给子View处理
              this.dragDistance = 0.0F;
              this.isDownToChildView = false;
              mReturningToStart = true;
              return false;
            }

            this.zoomChildView(this.dragDistance);
          }
          break;
        case MotionEvent.ACTION_OUTSIDE:
        default:
          break;
        case MotionEvent.ACTION_POINTER_DOWN:
          pointerIndex = ev.getActionIndex();
          if (pointerIndex < 0) {
            return false;
          }
          if (!mIsBeingDragged){
            this.mInitialDownY = ev.getY(pointerIndex);
            this.mInitialDownX = ev.getX(pointerIndex);
          }
          this.mActivePointerId = ev.getPointerId(pointerIndex);
          if (mIsBeingDragged){
            mInitialMotionY = ev.getY(pointerIndex);
          }
          break;
        case MotionEvent.ACTION_POINTER_UP:
          this.onSecondaryPointerUp(ev);
      }
      return true;
    } else {
      return false;
    }
  }

  /**
   * 回弹头部视图
   */
  private void recoveryHeadView() {
    float distance = getZoomDistance();
    if (distance == 0) {
      return;
    }
    //开启回弹动画
    recoverAnimator = ObjectAnimator.ofFloat(distance, 0.0F)
        .setDuration((long) (300L * distance / maxZoomRatio / headViewHeight));
    recoverAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
      @Override
      public void onAnimationUpdate(ValueAnimator animation) {
        float distance = (float) animation.getAnimatedValue();
        zoomHeadView(distance);
        dispatchHeadZoomEvent(true, distance);
      }
    });
    recoverAnimator.start();
  }

  private void zoomChildView(float overscrollTop) {
    //根据拖拽距离，通过阻尼函数计算真实的放大距离
    if (overscrollTop > mTotalDragDistance) overscrollTop = mTotalDragDistance;
    if (overscrollTop < 0) overscrollTop = 0;
    float percent =
        (float) (1 - Math.pow((1 - overscrollTop / mTotalDragDistance), dragAccelerationRatio));
    float pullDistance = overscrollTop;
    overscrollTop = percent * (maxZoomRatio * headViewHeight);
    //放大头图
    zoomHeadView(overscrollTop);
    dispatchHeadZoomEvent(false, overscrollTop);
  }

  /**
   * 分发头图放大事件
   */
  private void dispatchHeadZoomEvent(boolean isRecovering, float zoomDistance) {
    if (onHeadZoomListeners != null && onHeadZoomListeners.size() > 0) {
      for (OnHeadZoomListener onHeadZoomListener : onHeadZoomListeners) {
        if (onHeadZoomListener != null) {
          onHeadZoomListener.onHeadZoom(isRecovering, zoomDistance);
        }
      }
    }
  }

  /**
   * 放大头部视图
   */
  private void zoomHeadView(float distance) {
    ViewGroup.LayoutParams layoutParams = headView.getLayoutParams();
    layoutParams.width = headView.getMeasuredWidth();
    layoutParams.height = (int) (headViewHeight + distance);
    headView.setLayoutParams(layoutParams);
  }

  /**
   * 获取头部放大距离
   */
  public float getZoomDistance() {
    if (headView == null) {
      return 0;
    }
    return headView.getMeasuredHeight() - headViewHeight;
  }

  private void onSecondaryPointerUp(MotionEvent ev) {
    final int pointerIndex = ev.getActionIndex();
    final int pointerId = ev.getPointerId(pointerIndex);
    if (pointerId == mActivePointerId) {
      final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
      mActivePointerId = ev.getPointerId(newPointerIndex);
      mInitialMotionY = ev.getY(newPointerIndex);
      if (!mIsBeingDragged){
        mInitialDownY = mInitialMotionY;
      }
    }
  }

  /**
   * 获取布局下的子View是否滑动到顶部
   *
   * @return 是否滑动到顶部
   */
  private boolean isChildScrollToTop() {
    return !ViewCompat.canScrollVertically(childView, -1);
  }

  private void ensureTarget() {
    if (childView == null) {
      childView = getChildAt(0);
    }
  }

  /**
   * 初始化头部视图
   */
  private void initHeadView() {
    if (headView == null) {
      headView = findViewById(headViewId);
      if (headView != null) {
        headViewHeight = headView.getMeasuredHeight();
      } else {
        throw new RuntimeException("not find headView by headViewId");
      }
    }
  }

  @Override
  public void setNestedScrollingEnabled(boolean enabled) {
    this.mNestedScrollingChildHelper.setNestedScrollingEnabled(enabled);
  }

  @Override
  public boolean isNestedScrollingEnabled() {
    return this.mNestedScrollingChildHelper.isNestedScrollingEnabled();
  }

  @Override
  public boolean startNestedScroll(int axes) {
    return this.mNestedScrollingChildHelper.startNestedScroll(axes);
  }

  @Override
  public void stopNestedScroll() {
    this.mNestedScrollingChildHelper.stopNestedScroll();
  }

  @Override
  public boolean hasNestedScrollingParent() {
    return this.mNestedScrollingChildHelper.hasNestedScrollingParent();
  }

  @Override
  public boolean dispatchNestedScroll(int dxConsumed, int dyConsumed, int dxUnconsumed,
      int dyUnconsumed, int[] offsetInWindow) {
    return this.mNestedScrollingChildHelper.dispatchNestedScroll(dxConsumed, dyConsumed,
        dxUnconsumed, dyUnconsumed, offsetInWindow);
  }

  @Override
  public boolean dispatchNestedPreScroll(int dx, int dy, int[] consumed, int[] offsetInWindow) {
    return this.mNestedScrollingChildHelper.dispatchNestedPreScroll(dx, dy, consumed,
        offsetInWindow);
  }

  public boolean onNestedPreFling(View target, float velocityX, float velocityY) {
    //当头部视图未完成恢复时拦截子View的Fling事件，防止Fling结束时头部未还原导致ScrollListener中的scrollY计算错误
    if (getZoomDistance() > 0) {
      return true;
    }
    return this.dispatchNestedPreFling(velocityX, velocityY);
  }

  public boolean onNestedFling(View target, float velocityX, float velocityY,
      boolean consumed) {
    return this.dispatchNestedFling(velocityX, velocityY, consumed);
  }

  public boolean dispatchNestedFling(float velocityX, float velocityY, boolean consumed) {
    return this.mNestedScrollingChildHelper.dispatchNestedFling(velocityX, velocityY, consumed);
  }

  public boolean dispatchNestedPreFling(float velocityX, float velocityY) {
    return this.mNestedScrollingChildHelper.dispatchNestedPreFling(velocityX, velocityY);
  }

  @Override
  public boolean onStartNestedScroll(View child, View target,
      int nestedScrollAxes) {
    return this.isEnabled()
        && !this.mReturningToStart
        && (nestedScrollAxes & 2) != 0;
  }

  @Override
  public void onNestedScrollAccepted(View child, View target, int axes) {
    this.mNestedScrollingParentHelper.onNestedScrollAccepted(child, target, axes);
    this.startNestedScroll(axes & 2);
    this.mTotalUnconsumed = 0.0F;
    this.mNestedScrollInProgress = true;
  }

  @Override
  public void onStopNestedScroll(View target) {
    this.mNestedScrollingParentHelper.onStopNestedScroll(target);
    this.mNestedScrollInProgress = false;
    if (this.mTotalUnconsumed > 0.0F) {
      //停止嵌套滑动时，恢复头部视图
      this.recoveryHeadView();
    }
    this.mTotalUnconsumed = 0.0F;
    this.stopNestedScroll();
  }

  @Override
  public void onNestedScroll(View target, int dxConsumed, int dyConsumed, int dxUnconsumed,
      int dyUnconsumed) {
    this.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed,
        this.mParentOffsetInWindow);
    int dy = dyUnconsumed + this.mParentOffsetInWindow[1];
    if (dy < 0 && this.isChildScrollToTop()) {
      //嵌套滑动时放大头部，mTotalUnconsumed为下拉距离
      dy *= 0.6f;
      this.mTotalUnconsumed += (float) Math.abs(dy);
      if (this.mTotalUnconsumed > mTotalDragDistance){
        this.mTotalUnconsumed = mTotalDragDistance;
      }
      this.zoomChildView(this.mTotalUnconsumed);
    }
  }

  @Override
  public void onNestedPreScroll(View target, int dx, int dy, int[] consumed) {
    if (dy >= 0 && this.mTotalUnconsumed > 0.0F) {
      if ((float) dy > this.mTotalUnconsumed) {
        consumed[1] = dy - (int) this.mTotalUnconsumed;
        this.mTotalUnconsumed = 0.0F;
      } else {
        this.mTotalUnconsumed -= (float) dy;
        consumed[1] = dy;
      }
      this.zoomChildView(this.mTotalUnconsumed);
    }

    int[] parentConsumed = this.mParentScrollConsumed;
    if (this.dispatchNestedPreScroll(dx - consumed[0], dy - consumed[1], parentConsumed,
        null)) {
      consumed[0] += parentConsumed[0];
      consumed[1] += parentConsumed[1];
    }
  }

  @Override
  public int getNestedScrollAxes() {
    return this.mNestedScrollingParentHelper.getNestedScrollAxes();
  }

  /**
   * 设置可拖拽的最大距离
   *
   * @param mTotalDragDistance {@link HeadZoomLayout#mTotalDragDistance}
   */
  public void setTotalDragDistance(float mTotalDragDistance) {
    this.mTotalDragDistance = mTotalDragDistance;
  }

  /**
   * 设置拖拽加速度变化比例
   *
   * @param dragAccelerationRatio {@link HeadZoomLayout#dragAccelerationRatio}
   */
  public void setDragAccelerationRatio(float dragAccelerationRatio) {
    this.dragAccelerationRatio = dragAccelerationRatio;
  }

  /**
   * 设置头部最大放大比例
   *
   * @param maxZoomRatio {@link HeadZoomLayout#maxZoomRatio}
   */
  public void setMaxZoomRatio(float maxZoomRatio) {
    this.maxZoomRatio = maxZoomRatio;
  }

  /**
   * 获取是否可缩放头部视图
   *
   * @return {@link HeadZoomLayout#isEnabled()}
   */
  public boolean isZoomEnable() {
    return this.isEnabled();
  }

  /**
   * 设置是否可缩放头部视图
   *
   * @param zoomEnable {@link HeadZoomLayout#setEnabled(boolean)}
   */
  public void setZoomEnable(boolean zoomEnable) {
    setEnabled(zoomEnable);
  }

  /**
   * 添加头部放大监听
   *
   * @param onHeadZoomListener {@link HeadZoomLayout.OnHeadZoomListener}
   */
  public void addOnHeadZoomListener(
      OnHeadZoomListener onHeadZoomListener) {
    if (onHeadZoomListeners == null) {
      onHeadZoomListeners = new ArrayList<>();
    }
    onHeadZoomListeners.add(onHeadZoomListener);
  }

  /**
   * 移除已经添加的监听
   *
   * @param onHeadZoomListener {@link HeadZoomLayout.OnHeadZoomListener}
   */
  public void removeOnHeadZoomListener(OnHeadZoomListener onHeadZoomListener) {
    if (onHeadZoomListeners != null) {
      onHeadZoomListeners.remove(onHeadZoomListener);
    }
  }

  public interface OnHeadZoomListener {
    /**
     * 头部背景图方法监听
     *
     * @param isRecovering 是否在执行回弹动画
     * @param zoomDistance 真实放大的距离
     */
    void onHeadZoom(boolean isRecovering, float zoomDistance);
  }
}
