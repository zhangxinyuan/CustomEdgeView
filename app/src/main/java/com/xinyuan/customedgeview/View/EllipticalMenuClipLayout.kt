package com.xinyuan.video.ellipticalmenu.menu

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.xinyuan.video.ellipticalmenu.R

/**
 * 裁切椭圆菜单Layout
 */
class VideoLongPressSpeedMenuClipLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    companion object {
        /** 裁剪方向左边 */
        const val CLIP_ORIENTATION_LEFT = 0
        /** 裁剪方向右边 */
        const val CLIP_ORIENTATION_RIGHT = 1
    }

    /** 裁剪方向 */
    private var clipOrientation: Int
    /** rectF */
    private val mRectF = RectF()
    /** path */
    private val mPath = Path()

    init {
        val typedArray =
            context.obtainStyledAttributes(attrs, R.styleable.VideoLongPressSpeedMenuClipLayout)
        clipOrientation = typedArray.getInt(
            R.styleable.VideoLongPressSpeedMenuClipLayout_clip_orientation, CLIP_ORIENTATION_RIGHT
        )
        typedArray.recycle()
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }

    /**
     * 设置裁剪方向
     * @param isLeft 是否裁剪左侧
     */
    fun setClipOrientation(isLeft: Boolean) {
        clipOrientation = if (isLeft) CLIP_ORIENTATION_LEFT else CLIP_ORIENTATION_RIGHT
        genRectF(width, height)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        genRectF(w, h)
    }

    override fun draw(canvas: Canvas) {
        canvas.save()
        canvas.clipPath(genPath())
        super.draw(canvas)
        canvas.restore()
    }

    override fun dispatchDraw(canvas: Canvas) {
        canvas.save()
        canvas.clipPath(genPath())
        super.dispatchDraw(canvas)
        canvas.restore()
    }

    /**
     * 获取裁剪path
     * @return 裁剪path
     */
    private fun genPath(): Path {
        mPath.reset()
        if (clipOrientation == CLIP_ORIENTATION_LEFT) {
            mPath.addArc(mRectF, 90f, 180f)
        } else {
            mPath.addArc(mRectF, 270f, 180f)
        }
        return mPath
    }

    /**
     * 生成裁剪区域
     * @param w 宽
     * @param h 高
     */
    private fun genRectF(w: Int, h: Int) {
        if (w == 0 || h == 0) {
            return
        }
        val radius = (w * w + (h / 2) * (h / 2)) / (2 * w)
        if (clipOrientation == CLIP_ORIENTATION_LEFT) {
            mRectF.set(
                0f,
                -(radius - h / 2).toFloat(),
                (2 * radius).toFloat(),
                (h + (radius - h / 2)).toFloat()
            )
        } else {
            mRectF.set(
                -(radius * 2 - w).toFloat(),
                -(radius - h / 2).toFloat(),
                w.toFloat(),
                (h + (radius - h / 2)).toFloat()
            )
        }
    }
}
