package org.askerov.dynamicgrid;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TypeEvaluator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.ListAdapter;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.askerov.dynamicgid.R;

/**
 * Author: alex askerov
 * Date: 9/6/13
 * Time: 12:31 PM
 */
public class DynamicGridView extends GridView {

    private View mOverhoveredView;

    public interface ItemHoverListener {
        public void onItemHoverStart(View mobileView, View targetView);
        public void onItemHoverStop();
    }

    private ItemHoverListener itemHoverListener;
    private static final int INVALID_ID = AbstractDynamicGridAdapter.INVALID_ID;

    private static final int MOVE_DURATION = 300;
    private static final int SMOOTH_SCROLL_AMOUNT_AT_EDGE = 8;
    private static final String TAG = "DynamicGridView";


    private BitmapDrawable mHoverCell;
    protected Rect mHoverCellCurrentBounds;
    private Rect mHoverCellOriginalBounds;

    private int mTotalOffsetY = 0;
    private int mTotalOffsetX = 0;

    private int mDownX = -1;
    private int mDownY = -1;
    private int mLastEventY = -1;
    private int mLastEventX = -1;

    private List<Long> idList = new ArrayList<Long>();

    private long mMobileItemId = INVALID_ID;
    private boolean IS_REORDER = true;
    private boolean mCellIsMobile = false;
    private int mActivePointerId = INVALID_ID;

    private boolean mIsMobileScrolling;
    private int mSmoothScrollAmountAtEdge = 0;
    private boolean mIsWaitingForScrollFinish = false;
    private int mScrollState = OnScrollListener.SCROLL_STATE_IDLE;

    private boolean mIsEditMode = false;
    private List<ObjectAnimator> mWobbleAnimators = new LinkedList<ObjectAnimator>();
    private OnDropListener mDropListener;
    private boolean mHoverAnimation;
    private boolean mReorderAnimation;
    private boolean mWobbleInEditMode = true;

    private OnItemLongClickListener mUserLongClickListener;
    private OnItemLongClickListener mLocalLongClickListener = new OnItemLongClickListener() {
        public boolean onItemLongClick(AdapterView<?> arg0, View arg1, int pos, long id) {
            if (!isEnabled() || isEditMode())
                return false;
            mTotalOffsetY = 0;
            mTotalOffsetX = 0;

            int position = pointToPosition(mDownX, mDownY);
            int itemNum = position - getFirstVisiblePosition();

            View selectedView = getChildAt(itemNum);
            mMobileItemId = getAdapter().getItemId(position);
            mHoverCell = getAndAddHoverView(selectedView);
            if (isPostHoneycomb() && selectedView != null)
                selectedView.setVisibility(View.INVISIBLE);

            mCellIsMobile = true;

            updateNeighborViewsForId(mMobileItemId);

            if (isPostHoneycomb() && mWobbleInEditMode)
                startWobbleAnimation();

            if (mUserLongClickListener != null)
                mUserLongClickListener.onItemLongClick(arg0, arg1, pos, id);

            mIsEditMode = true;

            return true;
        }
    };

    private OnItemClickListener mUserItemClickListener;
    private OnItemClickListener mLocalItemClickListener = new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            if (!isEditMode() && isEnabled() && mUserItemClickListener != null) {
                mUserItemClickListener.onItemClick(parent, view, position, id);
            }else {
                Log.i(TAG, "Item single clicked");

            }
        }
    };
    private long startTime;
    private long endTime;
    private long lastMobileItemId;

    public DynamicGridView(Context context) {
        super(context);
        init(context);
    }

    public DynamicGridView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public DynamicGridView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    public void setOnDropListener(OnDropListener dropListener) {
        this.mDropListener = dropListener;
    }

    public void startEditMode() {
        mIsEditMode = true;
        if (isPostHoneycomb() && mWobbleInEditMode)
            startWobbleAnimation();
    }

    public void stopEditMode() {
        mIsEditMode = false;
        if (isPostHoneycomb() && mWobbleInEditMode)
            stopWobble(true);
        if(itemHoverListener != null){
        	itemHoverListener.onItemHoverStop();
        }
    }

    public boolean isEditMode() {
        return mIsEditMode;
    }

    public boolean isWobbleInEditMode() {
        return mWobbleInEditMode;
    }

    public void setWobbleInEditMode(boolean wobbleInEditMode) {
        this.mWobbleInEditMode = wobbleInEditMode;
    }

    @Override
    public void setOnItemLongClickListener(final OnItemLongClickListener listener) {
        mUserLongClickListener = listener;
        super.setOnItemLongClickListener(mLocalLongClickListener);
    }

    @Override
    public void setOnItemClickListener(OnItemClickListener listener) {
        this.mUserItemClickListener = listener;
        super.setOnItemClickListener(mLocalItemClickListener);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void startWobbleAnimation() {
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v != null && Boolean.TRUE != v.getTag(R.id.dynamic_grid_wobble_tag)) {
                if (i % 2 == 0)
                    animateWobble(v);
                else
                    animateWobbleInverse(v);
                v.setTag(R.id.dynamic_grid_wobble_tag, true);
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void stopWobble(boolean resetRotation) {
        for (Animator wobbleAnimator : mWobbleAnimators) {
            wobbleAnimator.cancel();
        }
        mWobbleAnimators.clear();
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v != null) {
                if (resetRotation) v.setRotation(0);
                v.setTag(R.id.dynamic_grid_wobble_tag, false);
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void restartWobble() {
        stopWobble(false);
        startWobbleAnimation();
    }

    public void init(Context context) {
        setOnScrollListener(mScrollListener);
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        mSmoothScrollAmountAtEdge = (int) (SMOOTH_SCROLL_AMOUNT_AT_EDGE * metrics.density + 0.5f);

    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void animateWobble(View v) {
        ObjectAnimator animator = createBaseWobble(v);
        animator.setFloatValues(-2, 2);
        animator.start();
        mWobbleAnimators.add(animator);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void animateWobbleInverse(View v) {
        ObjectAnimator animator = createBaseWobble(v);
        animator.setFloatValues(2, -2);
        animator.start();
        mWobbleAnimators.add(animator);
    }


    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private ObjectAnimator createBaseWobble(View v) {
        ObjectAnimator animator = new ObjectAnimator();
        animator.setDuration(180);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setPropertyName("rotation");
        animator.setTarget(v);
        return animator;
    }


    private void reorderElements(int originalPosition, int targetPosition) {
        getAdapterInterface().reorderItems(originalPosition, targetPosition);
    }

    private int getColumnCount() {
        return getAdapterInterface().getColumnCount();
    }

    private AbstractDynamicGridAdapter getAdapterInterface() {
        return ((AbstractDynamicGridAdapter) getAdapter());
    }

    /**
     * Creates the hover cell with the appropriate bitmap and of appropriate
     * size. The hover cell's BitmapDrawable is drawn on top of the bitmap every
     * single time an invalidate call is made.
     */
    private BitmapDrawable getAndAddHoverView(View v) {

        int w = v.getWidth();
        int h = v.getHeight();
        int top = v.getTop();
        int left = v.getLeft();

        Bitmap b = getBitmapFromView(v);

        BitmapDrawable drawable = new BitmapDrawable(getResources(), b);

        mHoverCellOriginalBounds = new Rect(left, top, left + w, top + h);
        mHoverCellCurrentBounds = new Rect(mHoverCellOriginalBounds);

        drawable.setBounds(mHoverCellCurrentBounds);

        return drawable;
    }

    /**
     * Returns a bitmap showing a screenshot of the view passed in.
     */
    private Bitmap getBitmapFromView(View v) {
        Bitmap bitmap = Bitmap.createBitmap(v.getWidth(), v.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        v.draw(canvas);
        return bitmap;
    }


    private void updateNeighborViewsForId(long itemId) {
        int draggedPos = getPositionForID(itemId);
        for (int pos = getFirstVisiblePosition(); pos <= getLastVisiblePosition(); pos++) {
            if (draggedPos != pos) {
                idList.add(getId(pos));
            }
        }
    }

    /**
     * Retrieves the position in the grid corresponding to <code>itemId</code>
     */
    public int getPositionForID(long itemId) {
        View v = getViewForId(itemId);
        if (v == null) {
            return -1;
        } else {
            return getPositionForView(v);
        }
    }

    public View getViewForId(long itemId) {
        int firstVisiblePosition = getFirstVisiblePosition();
        AbstractDynamicGridAdapter adapter = ((AbstractDynamicGridAdapter) getAdapter());
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            int position = firstVisiblePosition + i;
            long id = adapter.getItemId(position);
            if (id == itemId) {
                return v;
            }
        }
        return null;
    }

    protected void setElementBounds(BitmapDrawable cell, Rect bounds) {
        cell.setBounds(bounds);
    }


    @Override
    public boolean onTouchEvent(MotionEvent event) {

        Boolean returnValue = null;
        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                startTime = event.getEventTime();
                mDownX = (int) event.getX();
                mDownY = (int) event.getY();
                mActivePointerId = event.getPointerId(0);

                if (mIsEditMode && isEnabled()) {
                    layoutChildren();

                    mTotalOffsetY = 0;
                    mTotalOffsetX = 0;
                    int position = pointToPosition(mDownX, mDownY);
                    Log.i(TAG, "ACTION_DOWN got item position ("+position+") by x:"+mDownX+"y:"+mDownY);
                    int itemNum = position - getFirstVisiblePosition();
                    View selectedView = getChildAt(itemNum);
                    if (selectedView == null) {
                        returnValue =  true;//return true so subsequent actions will be delivered!
                    } else {
                        mMobileItemId = getAdapter().getItemId(position);
                        mHoverCell = getAndAddHoverView(selectedView);
                        if (isPostHoneycomb())
                            selectedView.setVisibility(View.INVISIBLE);
                        mCellIsMobile = true;
                        updateNeighborViewsForId(mMobileItemId);
                    }


                } else if (!isEnabled()) {
                    returnValue =  false;
                }

                break;
            case MotionEvent.ACTION_MOVE:
                if (mActivePointerId == INVALID_ID) {
                    break;
                }

                int pointerIndex = event.findPointerIndex(mActivePointerId);

                mLastEventY = (int) event.getY(pointerIndex);
                mLastEventX = (int) event.getX(pointerIndex);
                int deltaY = mLastEventY - mDownY;
                int deltaX = mLastEventX - mDownX;

                if (mCellIsMobile) {
                    mHoverCellCurrentBounds.offsetTo(mHoverCellOriginalBounds.left + deltaX + mTotalOffsetX,
                            mHoverCellOriginalBounds.top + deltaY + mTotalOffsetY);
                    setElementBounds(mHoverCell, mHoverCellCurrentBounds);
                    invalidate();
                    if(IS_REORDER) handleCellSwitch();
                    if(itemHoverListener != null) handleCellHover();
                    mIsMobileScrolling = false;
                    handleMobileCellScroll();
                    returnValue =  false;

                    //set adapter clicked Item
                    if(mMobileItemId != lastMobileItemId){
                        lastMobileItemId = mMobileItemId;
                        Object item = ((AbstractDynamicGridAdapter) getAdapter()).getItemById(mMobileItemId);
                        ((BaseDynamicGridAdapter)getAdapter()).setClickedChild(item);
                    }
                }

                break;
            case MotionEvent.ACTION_UP:
                endTime = event.getEventTime();
                touchEventsEnded();
                if (mDropListener != null) {
                    mDropListener.onActionDrop();
                }


                //if user touched slightly (no long touch event) end editmode
                if(endTime - startTime > 30 && endTime - startTime <180){
                    //we have a less then 1000ms duration touch
                    startTime=0;
                    endTime=0;
                    stopEditMode();
                    Log.i(TAG,"STOP EDITMODE");

                }
                //user longtouched item: mark as editable!
                else if(endTime - startTime > 300){
                    Object item = ((AbstractDynamicGridAdapter) getAdapter()).getItemById(mMobileItemId);
                    ((BaseDynamicGridAdapter)getAdapter()).setClickedChild(item);
                }

                break;
            case MotionEvent.ACTION_CANCEL:
                endTime = event.getEventTime();
                touchEventsCancelled();
                if (mDropListener != null) {
                    mDropListener.onActionDrop();
                }
                break;
            case MotionEvent.ACTION_POINTER_UP:
                /* If a multitouch event took place and the original touch dictating
                 * the movement of the hover cell has ended, then the dragging event
                 * ends and the hover cell is animated to its corresponding position
                 * in the listview. */
                pointerIndex = (event.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >>
                        MotionEvent.ACTION_POINTER_INDEX_SHIFT;
                final int pointerId = event.getPointerId(pointerIndex);
                if (pointerId == mActivePointerId) {
                    touchEventsEnded();
                }
                break;
            default:
                break;
        }



        if(returnValue != null){
            return returnValue;
        }else {
            return super.onTouchEvent(event);
        }
    }

    private void handleCellHover() {
        View mobileView = getViewForId(mMobileItemId);
        if(mobileView == null){
            return; //get out: element doesnt exist anymore, maybe deleted
        };

        View targetView = getViewUnderMobileView();
        if(targetView == null){
            mOverhoveredView = null;
            itemHoverListener.onItemHoverStop();
        }else {
            if(mOverhoveredView != targetView){
                if(mOverhoveredView != null){
                    itemHoverListener.onItemHoverStop();
                }

                mOverhoveredView = targetView;
                itemHoverListener.onItemHoverStart(mobileView,targetView);
            }
        }

    }

    private void handleMobileCellScroll() {
        mIsMobileScrolling = handleMobileCellScroll(mHoverCellCurrentBounds);
    }

    public boolean handleMobileCellScroll(Rect r) {
        int offset = computeVerticalScrollOffset();
        int height = getHeight();
        int extent = computeVerticalScrollExtent();
        int range = computeVerticalScrollRange();
        int hoverViewTop = r.top;
        int hoverHeight = r.height();

        if (hoverViewTop <= 0 && offset > 0) {
            smoothScrollBy(-mSmoothScrollAmountAtEdge, 0);
            return true;
        }

        if (hoverViewTop + hoverHeight >= height && (offset + extent) < range) {
            smoothScrollBy(mSmoothScrollAmountAtEdge, 0);
            return true;
        }

        return false;
    }

    @Override
    public void setAdapter(ListAdapter adapter) {
        super.setAdapter(adapter);
    }

    protected void touchEventsEnded() {
        final View mobileView = getViewForId(mMobileItemId);
        if (mCellIsMobile || mIsWaitingForScrollFinish) {
            mCellIsMobile = false;
            mIsWaitingForScrollFinish = false;
            mIsMobileScrolling = false;
            mActivePointerId = INVALID_ID;

            // If the autoscroller has not completed scrolling, we need to wait for it to
            // finish in order to determine the final location of where the hover cell
            // should be animated to.
            if (mScrollState != OnScrollListener.SCROLL_STATE_IDLE) {
                mIsWaitingForScrollFinish = true;
                return;
            }

            if(IS_REORDER)mHoverCellCurrentBounds.offsetTo(mobileView.getLeft(), mobileView.getTop());
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.HONEYCOMB) {
                animateBounds(mobileView);
            } else {
                setElementBounds(mHoverCell, mHoverCellCurrentBounds);
                invalidate();
                reset(mobileView);
            }
           
        } else {
            touchEventsCancelled();
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void animateBounds(final View mobileView) {
        TypeEvaluator<Rect> sBoundEvaluator = new TypeEvaluator<Rect>() {
            public Rect evaluate(float fraction, Rect startValue, Rect endValue) {
                return new Rect(interpolate(startValue.left, endValue.left, fraction),
                        interpolate(startValue.top, endValue.top, fraction),
                        interpolate(startValue.right, endValue.right, fraction),
                        interpolate(startValue.bottom, endValue.bottom, fraction));
            }

            public int interpolate(int start, int end, float fraction) {
                return (int) (start + fraction * (end - start));
            }
        };


        ObjectAnimator hoverViewAnimator = ObjectAnimator.ofObject(mHoverCell, "bounds",
                sBoundEvaluator, mHoverCellCurrentBounds);
        hoverViewAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                invalidate();
            }
        });
        hoverViewAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                mHoverAnimation = true;
                updateEnableState();
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mHoverAnimation = false;
                updateEnableState();
                reset(mobileView);
            }
        });
        hoverViewAnimator.start();
    }

    private void reset(View mobileView) {
        idList.clear();
        mMobileItemId = INVALID_ID;
        if(mobileView!=null) mobileView.setVisibility(View.VISIBLE);
        mHoverCell = null;
        if (!mIsEditMode && isPostHoneycomb() && mWobbleInEditMode)
            stopWobble(true);
        if (mIsEditMode && isPostHoneycomb() && mWobbleInEditMode)
            restartWobble();
        invalidate();
    }

    private void updateEnableState() {
        setEnabled(!mHoverAnimation && !mReorderAnimation);
    }

    /**
     * Seems that GridView before HONEYCOMB not support stable id in proper way.
     * That cause bugs on view recycle if we will animate or change visibility state for items.
     *
     * @return
     */
    private boolean isPostHoneycomb() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB;
    }

    protected void touchEventsCancelled() {
        View mobileView = getViewForId(mMobileItemId);
        if (mCellIsMobile && mobileView!=null) {
            reset(mobileView);
        }
        mCellIsMobile = false;
        mIsMobileScrolling = false;
        mActivePointerId = INVALID_ID;
        if(itemHoverListener != null){
        	itemHoverListener.onItemHoverStop();
        }

    }

    /**
     * extracts the view which is currently under floating mobile view
     * @return targetview
     */
    private View getViewUnderMobileView(){
        View mobileView = getViewForId(mMobileItemId);
        if(mobileView == null){
            return null; //get out: element doesnt exist anymore, maybe deleted
        };
        final int deltaY = mLastEventY - mDownY;
        final int deltaX = mLastEventX - mDownX;
        final int deltaYTotal = mHoverCellOriginalBounds.centerY() + mTotalOffsetY + deltaY;
        final int deltaXTotal = mHoverCellOriginalBounds.centerX() + mTotalOffsetX + deltaX;
        View targetView = null;

        for (Long id : idList) {
            View view = getViewForId(id);
            if (view != null) {
                if(containsCoordinates(view, deltaXTotal, deltaYTotal)){
                        targetView = view;
                }
            }
        }
        Log.i(TAG, "targetview set="+targetView+" delta:"+deltaXTotal+"/"+deltaYTotal);
        return targetView;
    }

    private boolean containsCoordinates(View view, int x, int y){
        Rect rc1 = new Rect();
        view.getHitRect(rc1);
        if(rc1.contains(x, y)){
            return true;
        }else {
            return false;
        }
    }

    private void handleCellSwitch() {
    	final int deltaY = mLastEventY - mDownY;
        final int deltaX = mLastEventX - mDownX;
        final int deltaYTotal = mHoverCellOriginalBounds.centerY() + mTotalOffsetY + deltaY;
        final int deltaXTotal = mHoverCellOriginalBounds.centerX() + mTotalOffsetX + deltaX;
        View mobileView = getViewForId(mMobileItemId);
        View targetView = null;
        float vX = 0;
        float vY = 0;
        Point mobileColumnRowPair = getColumnAndRowForView(mobileView);
        for (Long id : idList) {
            View view = getViewForId(id);
            if (view != null) {
                Point targetColumnRowPair = getColumnAndRowForView(view);
                if ((aboveRight(targetColumnRowPair, mobileColumnRowPair)
                        && deltaYTotal < view.getBottom() && deltaXTotal > view.getLeft()
                        || aboveLeft(targetColumnRowPair, mobileColumnRowPair)
                        && deltaYTotal < view.getBottom() && deltaXTotal < view.getRight()
                        || belowRight(targetColumnRowPair, mobileColumnRowPair)
                        && deltaYTotal > view.getTop() && deltaXTotal > view.getLeft()
                        || belowLeft(targetColumnRowPair, mobileColumnRowPair)
                        && deltaYTotal > view.getTop() && deltaXTotal < view.getRight()
                        || above(targetColumnRowPair, mobileColumnRowPair) && deltaYTotal < view.getBottom())
                        || below(targetColumnRowPair, mobileColumnRowPair) && deltaYTotal > view.getTop()
                        || right(targetColumnRowPair, mobileColumnRowPair) && deltaXTotal > view.getLeft()
                        || left(targetColumnRowPair, mobileColumnRowPair) && deltaXTotal < view.getRight()) {
                    float xDiff = Math.abs(DynamicGridUtils.getViewX(view) - DynamicGridUtils.getViewX(mobileView));
                    float yDiff = Math.abs(DynamicGridUtils.getViewY(view) - DynamicGridUtils.getViewY(mobileView));
                    if (xDiff >= vX && yDiff >= vY) {
                        vX = xDiff;
                        vY = yDiff;
                        targetView = view;
                    }
                }
            }
        }
        if (targetView != null) {
            final int originalPosition = getPositionForView(mobileView);
            int targetPosition = getPositionForView(targetView);

            if (targetPosition == INVALID_POSITION) {
                updateNeighborViewsForId(mMobileItemId);
                return;
            }
            reorderElements(originalPosition, targetPosition);
            mDownY = mLastEventY;
            mDownX = mLastEventX;
            mobileView.setVisibility(View.VISIBLE);
            if (isPostHoneycomb()) {
                targetView.setVisibility(View.INVISIBLE);
            }
            updateNeighborViewsForId(mMobileItemId);
            final ViewTreeObserver observer = getViewTreeObserver();
            final int finalTargetPosition = targetPosition;
            if (isPostHoneycomb() && observer != null) {
                observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        observer.removeOnPreDrawListener(this);
                        mTotalOffsetY += deltaY;
                        mTotalOffsetX += deltaX;
                        animateReorder(originalPosition, finalTargetPosition);
                        return true;
                    }
                });
            } else {
                mTotalOffsetY += deltaY;
                mTotalOffsetX += deltaX;
            }
        }
    }
    private boolean belowLeft(Point targetColumnRowPair, Point mobileColumnRowPair) {
        return targetColumnRowPair.y > mobileColumnRowPair.y && targetColumnRowPair.x < mobileColumnRowPair.x;
    }

    private boolean belowRight(Point targetColumnRowPair, Point mobileColumnRowPair) {
        return targetColumnRowPair.y > mobileColumnRowPair.y && targetColumnRowPair.x > mobileColumnRowPair.x;
    }

    private boolean aboveLeft(Point targetColumnRowPair, Point mobileColumnRowPair) {
        return targetColumnRowPair.y < mobileColumnRowPair.y && targetColumnRowPair.x < mobileColumnRowPair.x;
    }

    private boolean aboveRight(Point targetColumnRowPair, Point mobileColumnRowPair) {
        return targetColumnRowPair.y < mobileColumnRowPair.y && targetColumnRowPair.x > mobileColumnRowPair.x;
    }

    private boolean above(Point targetColumnRowPair, Point mobileColumnRowPair) {
        return targetColumnRowPair.y < mobileColumnRowPair.y && targetColumnRowPair.x == mobileColumnRowPair.x;
    }

    private boolean below(Point targetColumnRowPair, Point mobileColumnRowPair) {
        return targetColumnRowPair.y > mobileColumnRowPair.y && targetColumnRowPair.x == mobileColumnRowPair.x;
    }

    private boolean right(Point targetColumnRowPair, Point mobileColumnRowPair) {
        return targetColumnRowPair.y == mobileColumnRowPair.y && targetColumnRowPair.x > mobileColumnRowPair.x;
    }

    private boolean left(Point targetColumnRowPair, Point mobileColumnRowPair) {
        return targetColumnRowPair.y == mobileColumnRowPair.y && targetColumnRowPair.x < mobileColumnRowPair.x;
    }

    private Point getColumnAndRowForView(View view) {
        int pos = getPositionForView(view);
        int columns = getColumnCount();
        int column = pos % columns;
        int row = pos / columns;
        return new Point(column, row);
    }

    private long getId(int position) {
        return getAdapter().getItemId(position);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void animateReorder(final int oldPosition, final int newPosition) {
        boolean isForward = newPosition > oldPosition;
        List<Animator> resultList = new LinkedList<Animator>();
        if (isForward) {
            for (int pos = Math.min(oldPosition, newPosition); pos < Math.max(oldPosition, newPosition); pos++) {
                View view = getViewForId(getId(pos));
                if ((pos + 1) % getColumnCount() == 0) {
                    resultList.add(createTranslationAnimations(view, -view.getWidth() * (getColumnCount() - 1), 0, view.getHeight(), 0));
                } else {
                    resultList.add(createTranslationAnimations(view, view.getWidth(), 0, 0, 0));
                }
            }
        } else {
            for (int pos = Math.max(oldPosition, newPosition); pos > Math.min(oldPosition, newPosition); pos--) {
                View view = getViewForId(getId(pos));
                if ((pos + getColumnCount()) % getColumnCount() == 0) {
                    resultList.add(createTranslationAnimations(view, view.getWidth() * (getColumnCount() - 1), 0, -view.getHeight(), 0));
                } else {
                    resultList.add(createTranslationAnimations(view, -view.getWidth(), 0, 0, 0));
                }
            }
        }

        AnimatorSet resultSet = new AnimatorSet();
        resultSet.playTogether(resultList);
        resultSet.setDuration(MOVE_DURATION);
        resultSet.setInterpolator(new AccelerateDecelerateInterpolator());
        resultSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                mReorderAnimation = true;
                updateEnableState();
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mReorderAnimation = false;
                updateEnableState();
            }
        });
        resultSet.start();
    }


    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private AnimatorSet createTranslationAnimations(View view, float startX, float endX, float startY, float endY) {
        ObjectAnimator animX = ObjectAnimator.ofFloat(view, "translationX", startX, endX);
        ObjectAnimator animY = ObjectAnimator.ofFloat(view, "translationY", startY, endY);
        AnimatorSet animSetXY = new AnimatorSet();
        animSetXY.playTogether(animX, animY);
        return animSetXY;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (mHoverCell != null) {
            mHoverCell.draw(canvas);
        }
    }

    /**
     * Interface provide callback for end of drag'n'drop event
     */
    public interface OnDropListener {
        /**
         * called when view been dropped
         */
        void onActionDrop();

    }


    /**
     * This scroll listener is added to the gridview in order to handle cell swapping
     * when the cell is either at the top or bottom edge of the gridview. If the hover
     * cell is at either edge of the gridview, the gridview will begin scrolling. As
     * scrolling takes place, the gridview continuously checks if new cells became visible
     * and determines whether they are potential candidates for a cell swap.
     */
    private OnScrollListener mScrollListener = new OnScrollListener() {

        private int mPreviousFirstVisibleItem = -1;
        private int mPreviousVisibleItemCount = -1;
        private int mCurrentFirstVisibleItem;
        private int mCurrentVisibleItemCount;
        private int mCurrentScrollState;

        public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
                             int totalItemCount) {
            mCurrentFirstVisibleItem = firstVisibleItem;
            mCurrentVisibleItemCount = visibleItemCount;

            mPreviousFirstVisibleItem = (mPreviousFirstVisibleItem == -1) ? mCurrentFirstVisibleItem
                    : mPreviousFirstVisibleItem;
            mPreviousVisibleItemCount = (mPreviousVisibleItemCount == -1) ? mCurrentVisibleItemCount
                    : mPreviousVisibleItemCount;

            if(IS_REORDER) checkAndHandleFirstVisibleCellChange();
            if(IS_REORDER) checkAndHandleLastVisibleCellChange();

            mPreviousFirstVisibleItem = mCurrentFirstVisibleItem;
            mPreviousVisibleItemCount = mCurrentVisibleItemCount;
            if (isPostHoneycomb() && mWobbleInEditMode) {
                updateWobbleState(visibleItemCount);
            }
        }

        @TargetApi(Build.VERSION_CODES.HONEYCOMB)
        private void updateWobbleState(int visibleItemCount) {
            for (int i = 0; i < visibleItemCount; i++) {
                View child = getChildAt(i);

                if (child != null) {
                    if (mMobileItemId != INVALID_ID && Boolean.TRUE != child.getTag(R.id.dynamic_grid_wobble_tag)) {
                        if (i % 2 == 0)
                            animateWobble(child);
                        else
                            animateWobbleInverse(child);
                        child.setTag(R.id.dynamic_grid_wobble_tag, true);
                    } else if (mMobileItemId == INVALID_ID && child.getRotation() != 0) {
                        child.setRotation(0);
                        child.setTag(R.id.dynamic_grid_wobble_tag, false);
                    }
                }

            }
        }

        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {
            mCurrentScrollState = scrollState;
            mScrollState = scrollState;
            isScrollCompleted();
        }

        /**
         * This method is in charge of invoking 1 of 2 actions. Firstly, if the gridview
         * is in a state of scrolling invoked by the hover cell being outside the bounds
         * of the gridview, then this scrolling event is continued. Secondly, if the hover
         * cell has already been released, this invokes the animation for the hover cell
         * to return to its correct position after the gridview has entered an idle scroll
         * state.
         */
        private void isScrollCompleted() {
            if (mCurrentVisibleItemCount > 0 && mCurrentScrollState == SCROLL_STATE_IDLE) {
                if (mCellIsMobile && mIsMobileScrolling) {
                    handleMobileCellScroll();
                } else if (mIsWaitingForScrollFinish) {
                    touchEventsEnded();
                }
            }
        }

        /**
         * Determines if the gridview scrolled up enough to reveal a new cell at the
         * top of the list. If so, then the appropriate parameters are updated.
         */
        public void checkAndHandleFirstVisibleCellChange() {
            if (mCurrentFirstVisibleItem != mPreviousFirstVisibleItem) {
                if (mCellIsMobile && mMobileItemId != INVALID_ID) {
                    updateNeighborViewsForId(mMobileItemId);
                    handleCellSwitch();
                }
            }
        }

        /**
         * Determines if the gridview scrolled down enough to reveal a new cell at the
         * bottom of the list. If so, then the appropriate parameters are updated.
         */
        public void checkAndHandleLastVisibleCellChange() {
            int currentLastVisibleItem = mCurrentFirstVisibleItem + mCurrentVisibleItemCount;
            int previousLastVisibleItem = mPreviousFirstVisibleItem + mPreviousVisibleItemCount;
            if (currentLastVisibleItem != previousLastVisibleItem) {
                if (mCellIsMobile && mMobileItemId != INVALID_ID) {
                    updateNeighborViewsForId(mMobileItemId);
                    handleCellSwitch();
                }
            }
        }
    };

    public void setIsReorder(boolean isReorder){
        this.IS_REORDER = isReorder;
    }

    public void setItemHoverListener(ItemHoverListener itemHoverListener) {
        this.itemHoverListener = itemHoverListener;
    }
}
