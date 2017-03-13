package com.flarejaven.example.jnithread.util;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Point;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;

/**
 * 用于获取显示的相关数据
 */
public enum DisplayUtils {

    INSTANCE;

    public class DisplayInfo {

        private int screenWidth;

        private int screenHeight;

        private float density;

        public int getScreenWidth() {
            return screenWidth;
        }

        public void setScreenWidth(int screenWidth) {
            this.screenWidth = screenWidth;
        }

        public int getScreenHeight() {
            return screenHeight;
        }

        public void setScreenHeight(int screenHeight) {
            this.screenHeight = screenHeight;
        }

        public float getDensity() {
            return density;
        }

        public void setDensity(float density) {
            this.density = density;
        }
    }

    public DisplayInfo getSystemInfo(Context context){
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        DisplayInfo info = new DisplayInfo();
        info.setScreenWidth(dm.widthPixels);
        info.setScreenHeight(dm.heightPixels);
        info.setDensity(dm.density);
        return info;
    }

    public Point measureView(View view) {
        int width =View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED);
        int height =View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED);
        view.measure(width,height);

        return new Point(view.getMeasuredWidth(), view.getMeasuredHeight());
    }

    public int dp2px(float dp, Resources resources){
        float px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.getDisplayMetrics());
        return (int) px;
    }

    public int dp2px(Context context, float dpValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }

    public int px2dp(Context context, float pxValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (pxValue / scale + 0.5f);
    }

    public int sp2px(Context context, float spValue) {
        final float fontScale = context.getResources().getDisplayMetrics().scaledDensity;
        return (int) (spValue * fontScale + 0.5f);
    }

}
