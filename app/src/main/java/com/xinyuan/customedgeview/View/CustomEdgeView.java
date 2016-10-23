package com.xinyuan.customedgeview.View;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.widget.RelativeLayout;

import com.xinyuan.customedgeview.R;

/**
 * Created by xinyuan on 16-10-23.
 */

public class CustomEdgeView extends RelativeLayout {

    private int gap;//圆圈间距
    private int radius;//半径

    private Paint mCirclePaint;


    //圆数量
    private int circleNum;
    //剩余距离
    private float remain;

    public CustomEdgeView(Context context) {
        this(context, null);
    }

    public CustomEdgeView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CustomEdgeView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr);
    }

    private void init(Context context, AttributeSet attrs, int defStyleAttr) {
        TypedArray typedArray = context.getTheme().obtainStyledAttributes(attrs, R.styleable.CustomEdgeView, defStyleAttr,0);
        for (int i = 0; i < typedArray.getIndexCount(); i++) {

            int attr = typedArray.getIndex(i);
            switch (attr) {
                case R.styleable.CustomEdgeView_gap:
                    gap = typedArray.getDimensionPixelSize(R.styleable.CustomEdgeView_gap, 8);
                    break;
                case R.styleable.CustomEdgeView_radius:
                    radius = typedArray.getDimensionPixelSize(R.styleable.CustomEdgeView_radius, 10);
                    break;
            }
            typedArray.recycle();
        }

        mCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mCirclePaint.setDither(true);
        mCirclePaint.setColor(Color.WHITE);
        mCirclePaint.setStyle(Paint.Style.FILL);
    }


    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (remain == 0) {
             remain = (w - gap) % (2 * radius + gap);
        }
        circleNum = (w - gap) / (2 * radius + gap);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (int i = 0; i < circleNum; i++) {
            float x = gap + radius + (2 * radius +gap) * i + remain/2;
            canvas.drawCircle(x, 0, radius, mCirclePaint);//绘制上边的圆圈
            canvas.drawCircle(x, getHeight(), radius, mCirclePaint);//绘制下边的圆圈
        }
    }
}
