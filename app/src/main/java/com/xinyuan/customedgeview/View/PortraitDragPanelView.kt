package com.xinyuan.video.ellipticalmenu.menu

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.RelativeLayout
import androidx.annotation.NonNull
import androidx.core.content.ContextCompat
import androidx.core.view.NestedScrollingParent
import androidx.core.view.ViewCompat
import com.xinyuan.video.ellipticalmenu.R
import kotlin.math.abs

/** TAG */
private const val TAG = "PortraitDragPanelView"
/** 滑动速率的阈值 */
private const val TRACKER_DEFAULT = 0.5f
/** 动画时间 */
private const val ANIM_DURATION = 240L
/** 展开态高度默认百分比 */
private const val UNFOLD_STATE_HEIGHT_PERCENT_DEFAULT = 70f
/** 默展态高度默认百分比 */
private const val FOLD_STATE_HEIGHT_PERCENT_DEFAULT = 25f
/** 默展态高度最大百分比 */
private const val FOLD_STATE_HEIGHT_PERCENT_MAX = 35f
/** 默展态高度最小百分比 */
private const val FOLD_STATE_HEIGHT_PERCENT_MIN = 20f

/**
 * 竖屏面板可拖拽的view
 *
 * @param configUnFoldHeight  展开态高度
 * @param topBlankHeight  顶部空白区域高度
 */
class PortraitDragPanelView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
    defStyleAttr: Int = 0, var configUnFoldHeight: Float? = null, var topBlankHeight: Int? = null
) : FrameLayout(context, attrs, defStyleAttr), NestedScrollingParent {

    /** 是否是往下拉 */
    private var showTop = false

    /** 是否是往上滑 */
    private var hideTop = false

    /** 是否是往左拉 */
    private var showLeft = false

    /** 是否是往右滑 */
    private var hideRight = false

    /** 外部View滑动的最大距离 */
    private var topViewHeight = 0

    /** 顶部padding */
    private var topPadding = 0

    /** 按下时的x位置 */
    private var downX: Float = 0f

    /** 按下时的y位置 */
    private var downY: Float = 0f

    /** 按下时的时间戳 */
    private var downCurTime: Long = 0

    /** 是否是横向move操作 */
    private var isHorizontalMove = false

    /** 是否能进行横滑 */
    private var isHorizontalEnable = false

    /** 是否是竖向move操作 */
    private var isVerticalMove = false

    /** 滑动前的偏移量 */
    private var startScrollY = 0

    /** 滑动前的偏移量X */
    private var startScrollX = 0

    /** 面板展示状态 0：不展示  1：默展态  2：展开态 */
    private var panelState: PanelDragStatus = PanelDragStatus.NONE

    /** 面板是否展示水平展开 */
    private var isHorizontalShowAnim: Boolean = false

    /** 面板宽度 */
    private var panelWidth = 0

    /** 拖拽回调 */
    var dragListener: IDragListener? = null

    /** padding 相关动画 */
    private var paddingAnim: ValueAnimator? = null

    /** 显示动画 */
    private var showAnim: ValueAnimator? = null

    /** 聚合动画 */
    private var animSet: AnimatorSet? = null

    /** 是否动画中 */
    private var isRunAnim = false

    /** 展开态的高度 */
    private var unFoldStateHeight = 0f

    /** 默展态的高度 */
    private var foldStateHeight = 0f

    /** 默展态的最大百分比高度 */
    var maxFoldHeightPercent = FOLD_STATE_HEIGHT_PERCENT_MAX

    /** 默展态的最小百分比高度 */
    var minFoldHeightPercent = FOLD_STATE_HEIGHT_PERCENT_MIN

    /** 拖动距离超过面板高度1/4 是否可以震动 */
    private var enableMoreVibrate = true

    /** 拖动距离小于面板高度1/4 是否可以震动 */
    private var enableLessVibrate = false

    /** 头部布局容器 */
    private val headerContainer: FrameLayout

    /** 内容布局容器 */
    private val contentContainer: FrameLayout

    /** 底部布局容器 */
    private val footerContainer: FrameLayout

    /** 根布局容器 */
    private val rootContainer: RelativeLayout

    init {
        LayoutInflater.from(context).inflate(R.layout.layout_drag_content, this)
        headerContainer = findViewById(R.id.header_container)
        contentContainer = findViewById(R.id.content_container)
        footerContainer = findViewById(R.id.footer_container)
        rootContainer = findViewById(R.id.root_container)
        resetPanelHeight()
        visibility = View.GONE
        // 屏蔽点击，防止点击穿透
        rootContainer.setOnClickListener { }
    }

    /**
     * 获取内容容器
     *
     * @return 内容容器
     */
    fun getRootContainer(): RelativeLayout {
        return rootContainer
    }

    /**
     * 重置下面板高度
     */
    fun resetPanelHeight() {
        val displayHeight = getRealHeight()
        unFoldStateHeight = configUnFoldHeight ?: (displayHeight * (UNFOLD_STATE_HEIGHT_PERCENT_DEFAULT / 100))
        foldStateHeight = displayHeight * (FOLD_STATE_HEIGHT_PERCENT_DEFAULT / 100)
        topViewHeight = abs(unFoldStateHeight).toInt()
        topPadding = (displayHeight - unFoldStateHeight).toInt()
        // 适配内外计算高度时对状态栏的差异处理
        if (configUnFoldHeight != null) {
            if (topBlankHeight != null) {
                topPadding = topBlankHeight ?: 0
                topPadding += ScreenInfo.getStatusBarHeight(context)
            }
        }
        setPadding(0, topPadding, 0, 0)
    }

    /**
     * 获取屏幕真实高度，去掉底部导航栏
     *
     * @return 真实高度
     */
    fun getRealHeight(): Int {
        val realScreenHeight = ScreenInfo.getRealScreenHeight(context)
        val calculateHeight =
            ScreenInfo.getDisplayHeight(context) + ScreenInfo.getStatusBarHeight(context)
        return if (calculateHeight >= realScreenHeight) {
            realScreenHeight
        } else {
            calculateHeight
        }
    }

    /**
     * 更新面板默认高度百分比
     *
     * @param foldStateHeightPercent 默展态高度百分比
     */
    fun updateDefaultPanelHeightPercent(foldStateHeightPercent: Float) {
        resetPanelHeight()
        val heightPercent = when {
            foldStateHeightPercent > maxFoldHeightPercent -> {
                maxFoldHeightPercent
            }

            foldStateHeightPercent < minFoldHeightPercent -> {
                minFoldHeightPercent
            }

            else -> {
                foldStateHeightPercent
            }
        }
        foldStateHeight = getRealHeight() * (heightPercent / 100)
    }

    /**
     * 更新面板默认高度
     *
     * @param foldStateDefaultHeight 默展态高度
     */
    fun updateDefaultPanelHeight(foldStateDefaultHeight: Float) {
        resetPanelHeight()
        val displayHeight = getRealHeight()
        val maxHeight = displayHeight * (maxFoldHeightPercent / 100)
        val minHeight = displayHeight * (minFoldHeightPercent / 100)
        foldStateHeight = when {
            foldStateDefaultHeight > maxHeight -> {
                maxHeight
            }

            foldStateDefaultHeight < minHeight -> {
                minHeight
            }

            else -> {
                foldStateDefaultHeight
            }
        }
    }

    /**
     * 展开默展态
     */
    fun showFoldPanel() {
        visibility = View.VISIBLE
        when (panelState) {
            // 不展现 到 默展态 动画实现
            PanelDragStatus.NONE -> {
                val startY = 0 - unFoldStateHeight
                val endY = foldStateHeight - unFoldStateHeight
                setPadding(0, topPadding, 0, 0)
                showViewWithAnim(startY, endY, PanelDragStatus.FOLD)
            }
            // 7分屏 到 默展态 动画实现
            PanelDragStatus.UNFOLD -> {
                setPadding(0, topPadding, 0, 0)
                val startY = 0f
                val endY = foldStateHeight - unFoldStateHeight
                showViewWithAnim(startY, endY, PanelDragStatus.FOLD)
            }
            // 全屏态 到 默展态，动画 + padding
            PanelDragStatus.FULLSCREEN -> {
                val startY = 0f
                val endY = foldStateHeight - unFoldStateHeight
                handleAnimSet(startY, endY, PanelDragStatus.FOLD)
            }

            else -> {
                // do nothing
            }
        }
    }

    /**
     * 展开默展态，使用渐变动画
     */
    fun showFoldPanelWithAlphaAnim() {
        visibility = View.VISIBLE
        releaseShowAnim()
        val endY = foldStateHeight - unFoldStateHeight
        scrollY = endY.toInt()
        alpha = 0f
        val endState = PanelDragStatus.FOLD
        showAnim = createShowAnimation()
        showAnim?.addUpdateListener { animValue ->
            (animValue.animatedValue as? Float)?.let { progress ->
                alpha = progress
            }
        }
        showAnim?.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                super.onAnimationStart(animation)
                isRunAnim = true
                if (panelState != endState) {
                    dragListener?.onPanelStatusChangedBefore(panelState, endState)
                }
            }

            override fun onAnimationEnd(animation: Animator) {
                super.onAnimationEnd(animation)
                isRunAnim = false
                if (panelState != endState) {
                    dragListener?.onPanelStatusChanged(panelState, endState)
                }
                panelState = endState
                visibility = View.VISIBLE
                alpha = 1f
            }

            override fun onAnimationCancel(animation: Animator) {
                super.onAnimationCancel(animation)
                releaseShowAnim()
            }
        })
        showAnim?.start()
    }

    /**
     * 展开展开态
     *
     * @param defaultBackgroundEnabled 是否使用默认背景
     * @param isCommonPanel 是否是通用面板
     * @param isHorizontalShow 是否是水平展示
     */
    fun showUnFoldPanel(
        defaultBackgroundEnabled: Boolean = true,
        isCommonPanel: Boolean = false,
        isHorizontalShow: Boolean = false
    ) {
        isHorizontalShowAnim = isHorizontalShow
        visibility = View.VISIBLE
        when (panelState) {
            // 全屏态 到 7分屏态，通过padding动画实现
            PanelDragStatus.FULLSCREEN -> {
                changePanelPaddingWithAnim(false, PanelDragStatus.UNFOLD)
            }
            // 折叠态 到 7分屏态，通过位移动画实现
            PanelDragStatus.FOLD -> {
                resetPanelHeight()
                setPadding(0, topPadding, 0, 0)
                val startY = foldStateHeight - unFoldStateHeight
                val endY = 0f
                showViewWithAnim(startY, endY, PanelDragStatus.UNFOLD)
            }
            // 默展态 到 7分屏态，通过位移动画实现
            PanelDragStatus.NONE -> {
                resetPanelHeight()
                setPadding(0, topPadding, 0, 0)
                val startY = 0 - unFoldStateHeight
                val endY = 0f
                showViewWithAnim(startY, endY, PanelDragStatus.UNFOLD)
            }

            else -> {
                // do nothing
            }
        }
    }

    /**
     * 展开全屏态
     *
     * @param defaultBackgroundEnabled 是否使用默认背景
     * @param isHorizontalShow 是否是水平展示
     * @param panelWidth 面板宽度
     */
    fun showFullScreenPanel(
        defaultBackgroundEnabled: Boolean = true,
        isHorizontalShow: Boolean = false,
        panelWidth: Int = 0
    ) {
        isHorizontalShowAnim = isHorizontalShow
        this.panelWidth = panelWidth
        visibility = View.VISIBLE
        when (panelState) {
            // 不可见状态，直接到全屏状态，通过位移动画实现
            PanelDragStatus.NONE -> {
                val startY: Float
                val startX: Float
                val endY = 0f
                var endX = 0f
                val topPadding: Int
                if (isLandAndHorizontalDragPanel()) {
                    startX = -panelWidth.toFloat()
                    endX = 0f
                    setPadding(0, 0, 0, 0)
                    showViewWithAnim(startX, endX, PanelDragStatus.FULLSCREEN)
                } else {
                    topPadding = ScreenInfo.getStatusBarHeight(context)
                    startY = -(getRealHeight() - ScreenInfo.getStatusBarHeight(context)).toFloat()
                    setPadding(0, topPadding, 0, 0)
                    showViewWithAnim(startY, endY, PanelDragStatus.FULLSCREEN)
                }
            }
            // 折叠态，到全屏状态，通过位移动画实现，同时需要改变padding
            PanelDragStatus.FOLD -> {
                val startY = foldStateHeight - unFoldStateHeight
                val endY = 0f
                setPadding(0, topPadding, 0, 0)
                handleAnimSet(startY, endY, PanelDragStatus.FULLSCREEN)
            }
            // 7分屏态，到全屏态，通过padding动画实现
            PanelDragStatus.UNFOLD -> {
                changePanelPaddingWithAnim(true, PanelDragStatus.FULLSCREEN)
            }
            // 否则，无需处理
            else -> {
                // do nothing
            }
        }
    }

    /**
     * 是否是全屏模式
     */
    fun isFullScreenState(): Boolean {
        return panelState == PanelDragStatus.FULLSCREEN
    }

    /**
     * 设置面板背景颜色
     *
     * @param drawable
     */
    fun setPanelBackground(drawable: Int) {
        rootContainer.background = ContextCompat.getDrawable(context, drawable)
    }

    /**
     * 关闭面板
     */
    fun closePanel() {
        if (panelState != PanelDragStatus.NONE) {
            if (isHorizontalShowAnim) {
                val startX = scrollX.toFloat()
                val endX = 0 - panelWidth.toFloat()
                topPadding = 0
                showViewWithAnim(startX, endX, PanelDragStatus.NONE)
            } else {
                val startY = scrollY.toFloat()
                var endY = 0 - unFoldStateHeight
                // 结束位置高于开始位置时，高度兜底
                if (endY > startY) {
                    endY = startY
                }
                showViewWithAnim(startY, endY, PanelDragStatus.NONE)
            }
        } else {
            releaseAllAnim()
            visibility = View.GONE
        }
        setPadding(0, topPadding, 0, 0)
    }

    /**
     * 处理聚合动画
     *
     * @param startY 开始位置
     * @param endY 结束位置
     * @param endState 结束状态
     */
    private fun handleAnimSet(startY: Float, endY: Float, endState: PanelDragStatus) {
        releaseAnimSet()
        if (startY == endY) {
            return
        }
        showViewWithAnim(startY, endY, endState, needStart = false)
        changePanelPaddingWithAnim(
            endState == PanelDragStatus.FULLSCREEN,
            endState,
            needStart = false
        )
        paddingAnim?.duration = ANIM_DURATION / 2
        showAnim?.duration = ANIM_DURATION / 2
        animSet = AnimatorSet()
        animSet?.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator, isReverse: Boolean) {
                this.onAnimationStart(animation)
            }

            override fun onAnimationStart(animation: Animator) {
                super.onAnimationStart(animation)
                isRunAnim = true
                if (panelState != endState) {
                    dragListener?.onPanelStatusChangedBefore(panelState, endState)
                }
            }

            override fun onAnimationEnd(animation: Animator, isReverse: Boolean) {
                this.onAnimationEnd(animation)
            }

            override fun onAnimationEnd(animation: Animator) {
                isRunAnim = false
                if (panelState != endState) {
                    dragListener?.onPanelStatusChanged(panelState, endState)
                }
                panelState = endState
                if (endState == PanelDragStatus.NONE) {
                    visibility = View.GONE
                    dragListener?.onDismiss()
                } else {
                    visibility = View.VISIBLE
                }
            }

            override fun onAnimationCancel(animation: Animator) {
                super.onAnimationCancel(animation)
                releaseShowAnim()
                releasePaddingAnim()
            }
        })
        if (endState == PanelDragStatus.FULLSCREEN) {
            animSet?.play(paddingAnim)?.after(showAnim)
        } else {
            animSet?.play(paddingAnim)?.before(showAnim)
        }
        animSet?.start()
    }

    /**
     * 通过动画改变面板的padding
     *
     * @param isToFullScreen 是否打开全屏模式
     * @param endState 结束状态
     * @param needStart 是否需要开始动画
     */
    private fun changePanelPaddingWithAnim(
        isToFullScreen: Boolean,
        endState: PanelDragStatus,
        needStart: Boolean = true
    ) {
        releasePaddingAnim()
        paddingAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ANIM_DURATION
            interpolator = LinearInterpolator()
        }
        val statusHeight = ScreenInfo.getStatusBarHeight(context)
        val tPadding = topPadding - statusHeight
        paddingAnim?.addUpdateListener { animValue ->
            (animValue.animatedValue as? Float)?.let { progress ->
                val padding = if (isToFullScreen) {
                    (tPadding * (1 - progress)).toInt()
                } else {
                    (tPadding * progress).toInt()
                }
                setPadding(0, padding + statusHeight, 0, 0)
            }
        }
        if (needStart) {
            paddingAnim?.addListener(object : AnimatorListenerAdapter() {

                override fun onAnimationStart(animation: Animator, isReverse: Boolean) {
                    this.onAnimationStart(animation)
                }

                override fun onAnimationStart(animation: Animator) {
                    super.onAnimationStart(animation)
                    isRunAnim = true
                    if (panelState != endState) {
                        dragListener?.onPanelStatusChangedBefore(panelState, endState)
                    }
                }

                override fun onAnimationEnd(animation: Animator, isReverse: Boolean) {
                    this.onAnimationEnd(animation)
                }

                override fun onAnimationEnd(animation: Animator) {
                    isRunAnim = false
                    if (panelState != endState) {
                        dragListener?.onPanelStatusChanged(panelState, endState)
                    }
                    panelState = endState
                }

                override fun onAnimationCancel(animation: Animator) {
                    super.onAnimationCancel(animation)
                    releasePaddingAnim()
                }
            })
            paddingAnim?.start()
        }
    }

    /**
     * 用动画显示view
     *
     * @param startPosition 动画开始前的位置
     * @param endPosition 动画开始后的位置
     * @param endState 结束后view的状态
     * @param needStart 是否需要开始动画
     * @param animEnd 动画结束后的回调
     *
     */
    private fun showViewWithAnim(
        startPosition: Float,
        endPosition: Float,
        endState: PanelDragStatus,
        needStart: Boolean = true,
        animEnd: (() -> Unit)? = null
    ) {
        releaseShowAnim()
        if (endState != PanelDragStatus.NONE && startPosition == endPosition) {
            return
        }
        showAnim = createShowAnimation()
        showAnim?.addUpdateListener { animValue ->
            (animValue.animatedValue as? Float)?.let { progress ->
                if (isLandAndHorizontalDragPanel()) {
                    scrollX = (startPosition + (endPosition - startPosition) * progress).toInt()
                    onHorizontalDrag()
                } else {
                    scrollY = (startPosition + (endPosition - startPosition) * progress).toInt()
                    onVerticalDrag()
                }
            }
        }
        if (needStart) {
            showAnim?.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    super.onAnimationStart(animation)
                    isRunAnim = true
                    if (panelState != endState) {
                        dragListener?.onPanelStatusChangedBefore(panelState, endState)
                    }
                }

                override fun onAnimationEnd(animation: Animator) {
                    super.onAnimationEnd(animation)
                    isRunAnim = false
                    if (panelState != endState) {
                        dragListener?.onPanelStatusChanged(panelState, endState)
                    }
                    panelState = endState
                    if (endState == PanelDragStatus.NONE) {
                        visibility = View.GONE
                        dragListener?.onDismiss()
                    } else {
                        visibility = View.VISIBLE
                    }
                    animEnd?.invoke()
                }

                override fun onAnimationCancel(animation: Animator) {
                    super.onAnimationCancel(animation)
                    releaseShowAnim()
                }
            })
            showAnim?.start()
        }
    }

    /**
     * 添加头部view
     *
     * @param view 头部view
     */
    fun addHeaderView(view: View) {
        headerContainer.addView(view)
    }

    /**
     * 添加内容view
     *
     * @param view 头部view
     */
    fun addContentView(view: View) {
        contentContainer.addView(view)
    }

    /**
     * 添加底部view
     *
     * @param view 头部view
     */
    fun addFooterView(view: View) {
        footerContainer.addView(view)
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        ev?.let {
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX
                    downY = ev.rawY
                    downCurTime = System.currentTimeMillis()
                    startScrollY = scrollY
                    startScrollX = scrollX
                    isHorizontalMove = false
                    /** 默认可以进行横滑 */
                    isHorizontalEnable = dragListener?.isHorizontalDragEnable(ev) ?: true
                    isVerticalMove = false
                    parent.requestDisallowInterceptTouchEvent(true)
                    dragListener?.onStartDragging()
                }

                MotionEvent.ACTION_UP -> {
                    touchEnd(ev)
                    if (ev.rawX == downX && ev.rawY == downY && !isUnderChildView()) {
                        dragListener?.onClickNonPanelArea()
                        // 关闭面板
                        changeToNoneState()
                    } else {
                        // 改变view的位置
                        changeViewPosition(ev)
                    }
                }

                MotionEvent.ACTION_CANCEL -> {
                    touchEnd(ev)
                    // 异常原因cancel事件，恢复至down之前的状态
                    when (panelState) {
                        is PanelDragStatus.UNFOLD -> {
                            changeToUnFoldState()
                        }

                        is PanelDragStatus.FOLD -> {
                            changeToFoldState()
                        }

                        is PanelDragStatus.FULLSCREEN -> {
                            changeToFullScreenState()
                        }

                        else -> {
                            changeToNoneState()
                        }
                    }
                }

                else -> {}
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    /**
     * 结束按压数据重置
     *
     * @param ev MotionEvent
     */
    private fun touchEnd(ev: MotionEvent) {
        onEndDragging(ev)
        isHorizontalMove = false
        isVerticalMove = false
        enableMoreVibrate = true
        enableLessVibrate = false
    }

    /**
     * 结束拖拽
     *
     * @param ev MotionEvent
     */
    private fun onEndDragging(ev: MotionEvent) {
        val isClick = ev.rawX == downX && ev.rawY == downY
        dragListener?.onEndDragging(panelState, downY > ev.rawY, isClick)
        if (panelState == PanelDragStatus.NONE) {
            dragListener?.onDismiss()
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        ev?.let {
            when (ev.action) {
                MotionEvent.ACTION_MOVE -> {
                    return onInterceptTouchMove(ev)
                }

                else -> {}
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    /**
     * 是否拦截滑动事件
     *
     * @param ev MotionEvent
     *
     * @return true 拦截事件
     */
    private fun onInterceptTouchMove(ev: MotionEvent): Boolean {
        val moveX = ev.rawX
        val moveY = ev.rawY
        val deltaX = moveX - downX
        val deltaY = moveY - downY
        if (abs(deltaY) > abs(deltaX) && !isHorizontalMove) {
            if (deltaY > 0) {
                // 下滑
                val disAllowed =
                    !(dragListener?.isChildTop() ?: false) || panelState != PanelDragStatus.NONE
                parent.requestDisallowInterceptTouchEvent(disAllowed)
                if (dragListener?.isChildTop() == true && !isLandAndHorizontalDragPanel()) {
                    isVerticalMove = true
                    return true
                }
            } else if (deltaY < 0) {
                // 上滑
                if (panelState == PanelDragStatus.FOLD && (!isUnderChildView() || dragListener?.isForbidFoldPanelDragUp() == true)) {
                    // 默展态，上滑不消费，交给父view处理
                    parent.requestDisallowInterceptTouchEvent(false)
                    return false
                } else if (panelState == PanelDragStatus.UNFOLD && !isUnderChildView()) {
                    // 展开态，上滑消费
                    parent.requestDisallowInterceptTouchEvent(true)
                    isVerticalMove = true
                    return true
                } else if (panelState == PanelDragStatus.FULLSCREEN && !isUnderChildView()) {
                    // 全屏态，上滑不消费
                    parent.requestDisallowInterceptTouchEvent(false)
                    return false
                } else {
                    val disAllowed = !(dragListener?.isChildBottom()
                        ?: false) || (panelState != PanelDragStatus.UNFOLD && panelState != PanelDragStatus.FULLSCREEN)
                    parent.requestDisallowInterceptTouchEvent(disAllowed)
                }
            }
        } else if (abs(deltaX) > abs(deltaY) && !isVerticalMove) {
            if (isLandAndHorizontalDragPanel()) {
                if (deltaX > 0) {
                    // 右滑
                    parent.requestDisallowInterceptTouchEvent(panelState != PanelDragStatus.NONE)
                    isHorizontalMove = true
                    return true
                } else if (deltaX < 0) {
                    // 左滑
                    if (panelState == PanelDragStatus.FULLSCREEN && !isUnderChildView()) {
                        // 全屏态，左滑不消费
                        parent.requestDisallowInterceptTouchEvent(false)
                        return false
                    } else {
                        parent.requestDisallowInterceptTouchEvent(panelState != PanelDragStatus.NONE)
                    }
                }
            } else {
                // 横滑拦截事件
                if (isHorizontalEnable) {
                    parent.requestDisallowInterceptTouchEvent(panelState != PanelDragStatus.NONE)
                    isHorizontalMove = true
                    return true
                } else {
                    parent.requestDisallowInterceptTouchEvent(false)
                }
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent?): Boolean {
        // fix:关闭按钮后，动画执行中，同时拖拽，视频位置最终不正确
        if (isRunAnim) {
            return false
        }
        ev?.let {
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val moveX = ev.rawX
                    val moveY = ev.rawY
                    val deltaX = moveX - downX
                    val deltaY = moveY - downY
                    return touchMove(deltaY, deltaX)
                }

                else -> {
                    return false
                }
            }
        }
        return false
    }

    /**
     * 手指移动处理
     *
     * @param deltaX 横向滑动距离
     * @param deltaY 竖向滑动距离
     *
     * @return 是否消费这个事件 true 消费
     */
    private fun touchMove(deltaY: Float, deltaX: Float): Boolean {
        // 竖屏
        if (((abs(deltaY) > abs(deltaX) && !isHorizontalMove) || isVerticalMove) && !isLandAndHorizontalDragPanel()) {
            isVerticalMove = true
            if (deltaY > 0) {
                // 下滑
                val disAllowed =
                    !(dragListener?.isChildTop() ?: false) || panelState != PanelDragStatus.NONE
                parent.requestDisallowInterceptTouchEvent(disAllowed)
            } else if (deltaY < 0) {
                // 上滑
                if (panelState == PanelDragStatus.FOLD && (!isUnderChildView() || dragListener?.isForbidFoldPanelDragUp() == true)) {
                    // 默展态，上滑不消费，交给父view处理
                    parent.requestDisallowInterceptTouchEvent(false)
                    return false
                } else if (panelState == PanelDragStatus.UNFOLD && !isUnderChildView()) {
                    // 展开态，上滑消费
                    parent.requestDisallowInterceptTouchEvent(true)
                } else if (panelState == PanelDragStatus.FULLSCREEN && !isUnderChildView()) {
                    // 全屏态，上滑不消费
                    parent.requestDisallowInterceptTouchEvent(false)
                } else {
                    val disAllowed = !(dragListener?.isChildBottom()
                        ?: false) || (panelState != PanelDragStatus.UNFOLD && panelState != PanelDragStatus.FULLSCREEN)
                    parent.requestDisallowInterceptTouchEvent(disAllowed)
                }
            }
            scrollY = if (startScrollY - deltaY <= 0) {
                (startScrollY - deltaY).toInt()
            } else {
                0
            }
        } else if ((abs(deltaX) > abs(deltaY) && !isVerticalMove) || isHorizontalMove) {
            isHorizontalMove = true
            if (isHorizontalEnable) {
                if (isLandAndHorizontalDragPanel()) {
                    // PadStyle横屏情况下，横向正常滑动
                    if (deltaX > 0) {
                        // 向右滑
                        parent.requestDisallowInterceptTouchEvent(panelState != PanelDragStatus.NONE)
                    } else {
                        // 向左滑
                        if (panelState == PanelDragStatus.FULLSCREEN && !isUnderChildView()) {
                            // 全屏态，左滑不消费
                            parent.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                    if (deltaX > 0) {
                        scrollX = -deltaX.toInt()
                    }
                } else {
                    // 默认情况下，横向滑动时，将垂直距离映射成水平距离
                    parent.requestDisallowInterceptTouchEvent(panelState != PanelDragStatus.NONE)
                    // 横向滑动时，将水平距离映射成垂直距离
                    val panelWidth = width.toFloat()
                    val panelHeight = when (panelState) {
                        PanelDragStatus.UNFOLD -> {
                            unFoldStateHeight
                        }

                        PanelDragStatus.FOLD -> {
                            foldStateHeight
                        }

                        else -> {
                            0f
                        }
                    }
                    // 按照面板宽高的比例换距离
                    val dragY = if (panelWidth > 0 && panelHeight > 0) {
                        ((deltaX / panelWidth) * panelHeight).toInt()
                    } else {
                        deltaX.toInt()
                    }
                    scrollY = if (startScrollY - dragY <= 0) {
                        startScrollY - dragY
                    } else {
                        0
                    }
                }
            } else {
                parent.requestDisallowInterceptTouchEvent(false)
            }
        }
        if (isLandAndHorizontalDragPanel()) {
            onHorizontalDrag()
        } else {
            onVerticalDrag()
            if (panelState == PanelDragStatus.UNFOLD || panelState == PanelDragStatus.FULLSCREEN) {
                dragVibrate(abs(scrollY))
            }
        }
        return true
    }

    /**
     * 改变view的位置
     *
     * @param ev MotionEvent
     */
    private fun changeViewPosition(ev: MotionEvent) {
        val moveX = ev.rawX
        val moveY = ev.rawY
        val deltaX = moveX - downX
        val deltaY = moveY - downY
        // 滑动手势时间
        val touchTime = System.currentTimeMillis() - downCurTime
        // 横向滑动速度
        val xVelocity = deltaX / touchTime
        // 竖向滑动速度
        val yVelocity = deltaY / touchTime
        if (abs(xVelocity) > abs(yVelocity) && abs(xVelocity) > TRACKER_DEFAULT && isHorizontalEnable) {
            if (isLandAndHorizontalDragPanel()) {
                if (xVelocity > 0) {
                    // 向右滑
                    if (dragListener?.isAllowHorizontalSlideDismissPanel(deltaX) != false) {
                        // 横滑 判断方向 deltaX > 0 向右滑回调
                        dragListener?.pullHorizontalToClosePanel(deltaX > 0)
                        // 横向扫一下, 关闭面板
                        changeToNoneState()
                    }
                } else {
                    // 向左滑，横向扫一下, 展开面板
                    showViewWithAnim(scrollX.toFloat(), 0f, PanelDragStatus.FULLSCREEN)
                }
            } else {
                if (dragListener?.isAllowHorizontalSlideDismissPanel(deltaX) != false) {
                    // 横滑 判断方向 deltaX > 0 向右滑回调
                    dragListener?.pullHorizontalToClosePanel(deltaX > 0)
                    // 横向扫一下, 关闭面板
                    changeToNoneState()
                }
            }
        } else if (abs(yVelocity) > abs(xVelocity) && abs(yVelocity) > TRACKER_DEFAULT) {
            // 竖向扫一下
            if (yVelocity > 0) {
                // 向下拉
                if (((dragListener?.isChildTop() == true && isUnderChildView()) || !isUnderChildView()) && !isLandAndHorizontalDragPanel()) {
                    // 向下拉回调
                    dragListener?.pullDownToClosePanel()
                    changeToNoneState()
                }
            } else {
                // 向上拉
                if (isFullScreenState()) {
                    changeToFullScreenState()
                } else {
                    changeToUnFoldState()
                }
            }
        } else {
            // 根据松手时的位置判断
            if (isLandAndHorizontalDragPanel()) {
                if (deltaX > 0) {
                    changeToNoneState()
                }
            } else {
                changePanelDragState()
            }
        }
    }

    /**
     * 切换面板状态
     */
    private fun changePanelDragState() {
        if (panelState == PanelDragStatus.FOLD) {
            // 默展态
            when {
                // 滑到默展态和展开态中间一半以上的位置
                scrollY >= (foldStateHeight - unFoldStateHeight) / 2 -> {
                    // 切换至展开态
                    changeToUnFoldState()
                }
                // 滑到默展态和展开态中间一半以下的位置
                scrollY in (foldStateHeight - unFoldStateHeight).toInt()..(foldStateHeight - unFoldStateHeight).toInt() / 2 -> {
                    // 默展态 -> 默展态
                    changeToFoldState()
                }
                // 滑到默展态高度以下的位置
                scrollY < foldStateHeight - unFoldStateHeight -> {
                    // 关闭面板
                    changeToNoneState()
                }
            }
        } else if (panelState == PanelDragStatus.UNFOLD) {
            when {
                // 滑到滑动关闭的最小距离以上
                scrollY >= 0 - getReleaseDistance() -> {
                    // 切换至展开态
                    changeToUnFoldState()
                }
                // 滑到滑动关闭的最小距离以下
                scrollY < 0 - getReleaseDistance() -> {
                    // 关闭面板
                    changeToNoneState()
                }
            }
        } else if (panelState == PanelDragStatus.FULLSCREEN) {
            when {
                // 滑到滑动关闭的最小距离以上
                scrollY >= 0 - getReleaseDistance() -> {
                    // 切换至展开态
                    changeToFullScreenState()
                }
                // 滑到滑动关闭的最小距离以下
                scrollY < 0 - getReleaseDistance() -> {
                    // 关闭面板
                    changeToNoneState()
                }
            }
        }
    }

    /**
     * 切换至无状态模式
     */
    private fun changeToNoneState() {
        closePanel()
    }

    /**
     * 切换至默展态模式
     */
    private fun changeToFoldState() {
        val endY = foldStateHeight - unFoldStateHeight
        showViewWithAnim(scrollY.toFloat(), endY, PanelDragStatus.FOLD)
    }

    /**
     * 切换至展开态模式
     */
    private fun changeToUnFoldState() {
        showViewWithAnim(scrollY.toFloat(), 0f, PanelDragStatus.UNFOLD)
    }

    /**
     * 切换至展开态模式
     */
    private fun changeToFullScreenState() {
        showViewWithAnim(scrollY.toFloat(), 0f, PanelDragStatus.FULLSCREEN)
    }

    override fun onStartNestedScroll(
        @NonNull child: View,
        @NonNull target: View,
        nestedScrollAxes: Int
    ): Boolean {
        return nestedScrollAxes and ViewCompat.SCROLL_AXIS_VERTICAL != 0
    }

    override fun onStopNestedScroll(@NonNull target: View) {
    }

    /**
     * 内部View在滑动之前通知外部View是否要处理这次滑动的横向和纵向距离
     *
     * @param target   内部View
     * @param dx       滑动的横向距离
     * @param dy       滑动的纵向距离
     * @param consumed 外部View消耗的横向和纵向距离
     */
    override fun onNestedPreScroll(
        @NonNull target: View,
        dx: Int,
        dy: Int,
        @NonNull consumed: IntArray
    ) {
        if (isLandAndHorizontalDragPanel()) {
            var dx = dx
            showLeft = dx < 0 && abs(scrollX) < panelWidth && !target.canScrollHorizontally(1) //往左拉
            if (showLeft) {
                if (abs(scrollX + dx) > panelWidth) { //如果超过了指定位置
                    dx = -(panelWidth - abs(scrollX)) //滑动到指定位置
                }
            }
            hideRight = dx > 0 && scrollX < 0 //往上滑
            if (hideRight) {
                if (dx + scrollX > 0) { //如果超过了初始位置
                    dx = -scrollX //滑动到初始位置
                }
            }
            if (showTop || hideTop) {
                consumed[0] = dx //消耗纵向距离
                scrollBy(dx, 0) //外部View滚动
            }
            onHorizontalDrag()
        } else {
            var dy = dy
            showTop =
                dy < 0 && abs(scrollY) < topViewHeight && !target.canScrollVertically(-1) //往下拉
            if (showTop) {
                if (abs(scrollY + dy) > topViewHeight) { //如果超过了指定位置
                    dy = -(topViewHeight - abs(scrollY)) //滑动到指定位置
                }
            }
            hideTop = dy > 0 && scrollY < 0 //往上滑
            if (hideTop) {
                if (dy + scrollY > 0) { //如果超过了初始位置
                    dy = -scrollY //滑动到初始位置
                }
            }
            if (showTop || hideTop) {
                consumed[1] = dy //消耗纵向距离
                scrollBy(0, dy) //外部View滚动
            }
            onVerticalDrag()
        }
    }

    /**
     * 垂直滑动高度回调
     */
    private fun onVerticalDrag() {
        dragListener?.onVerticalDrag((unFoldStateHeight + scrollY).toInt(), panelState)
    }

    /**
     * 水平滑动回调
     */
    private fun onHorizontalDrag() {
        dragListener?.onHorizontalDrag(scrollX, panelWidth)
    }

    /**
     * 判断是否是横屏下且是可水平拖拽的面板
     *
     * @return 是否是横屏下且是可水平拖拽的面板
     */
    private fun isLandAndHorizontalDragPanel(): Boolean {
        return dragListener?.isLandAndHorizontalDragPanel() == true
    }

    override fun onNestedFling(
        @NonNull target: View,
        velocityX: Float,
        velocityY: Float,
        consumed: Boolean
    ): Boolean {
        return scrollY != 0
    }

    override fun onNestedPreFling(
        @NonNull target: View,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        return scrollY != 0
    }

    override fun startNestedScroll(axes: Int): Boolean {
        return false
    }

    override fun getNestedScrollAxes(): Int {
        return ViewCompat.SCROLL_AXIS_VERTICAL
    }

    /**
     * 创建显示动画
     *
     * @return 显示动画
     */
    private fun createShowAnimation(): ValueAnimator {
        return ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ANIM_DURATION
            interpolator = LinearInterpolator()
        }
    }

    /**
     * 创建渐变用的动画
     *
     * @return 渐变用的动画
     */
    private fun createAlphaAnimation(): ValueAnimator {
        return ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 120L
            interpolator = LinearInterpolator()
        }
    }

    /**
     * 所有动画资源释放
     */
    private fun releaseAllAnim() {
        releaseShowAnim()
        releasePaddingAnim()
        releaseAnimSet()
    }

    /**
     * show动画资源释放
     */
    private fun releaseShowAnim() {
        showAnim?.removeAllUpdateListeners()
        showAnim?.removeAllListeners()
        showAnim?.cancel()
        showAnim = null
    }

    /**
     * padding动画资源释放
     */
    private fun releasePaddingAnim() {
        paddingAnim?.removeAllUpdateListeners()
        paddingAnim?.removeAllListeners()
        paddingAnim?.cancel()
        paddingAnim = null
    }

    /**
     * 聚合动画资源释放
     */
    private fun releaseAnimSet() {
        animSet?.cancel()
        animSet = null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        releaseAllAnim()
    }

    /**
     * 初始滑动坐标点是否包含在子view内部
     *
     * @return ture：坐标点在view内部，false：坐标点在view外部
     */
    private fun isUnderChildView(): Boolean {
        getChildAt(0)?.let { view ->
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            return downX >= location[0] && downX <= location[0] + view.width
                    && downY >= location[1] && downY <= location[1] + view.height
        }
        return false
    }

    /**
     * 获取展开态的高度
     *
     * @return 展开态的高度
     */
    fun getUnFoldStateHeight(): Float {
        return unFoldStateHeight
    }

    /**
     * 获取默展态的高度
     *
     * @return 默展态的高度
     */
    fun getFoldStateHeight(): Float {
        return foldStateHeight
    }

    /**
     * 获取当前的展开状态
     *
     * @return 当前的展开状态
     */
    fun getPanelState(): PanelDragStatus {
        return panelState
    }

    /**
     * 拖动中震动
     *
     * @param distance 拖动的距离
     */
    private fun dragVibrate(distance: Int) {
        if (enableMoreVibrate && distance >= getReleaseDistance()) {
            // 下滑，经过1/4距离时震动
//            microVibrate(context)
            enableMoreVibrate = false
        } else if (enableLessVibrate && distance < getReleaseDistance()) {
            // 上滑，经过1/4距离的时候震动
//            microVibrate(context)
            enableLessVibrate = false
        } else if (distance < getReleaseDistance()) {
            // 重置下滑控制变量
            enableMoreVibrate = true
        } else if (distance > getReleaseDistance()) {
            // 重置上滑控制变量
            enableLessVibrate = true
        }
    }

    /**
     * 获取滑动关闭的最小距离
     *
     * @return 屏幕容器高度的1/4
     */
    private fun getReleaseDistance(): Int {
        val maxEdge = height.coerceAtLeast(unFoldStateHeight.toInt())
        return maxEdge / 4
    }

    /**
     * 水平拖拽监听
     */
    interface IDragListener {

        /**
         * view 消失
         */
        fun onDismiss() {
        }

        /**
         * 垂直拖拽回调
         *
         * @param distance 当前面板距底部距离
         * @param panelState 起始面板状态
         */
        fun onVerticalDrag(distance: Int, panelState: PanelDragStatus) {
        }

        /**
         * 水平滑动回调
         *
         * @param distance 当前面板距侧边距离
         * @param width 面板宽度
         */
        fun onHorizontalDrag(distance: Int, width: Int) {
        }

        /**
         * 是否是水平滑动面板
         *
         * @return true 是横滑面板 false 不是横滑面板
         */
        fun isLandAndHorizontalDragPanel(): Boolean {
            return false
        }

        /**
         * 子view是否滑到顶部
         *
         * @return true 滑到顶部
         */
        fun isChildTop(): Boolean {
            return true
        }

        /**
         * view开始拖拽
         */
        fun onStartDragging() {
        }

        /**
         * view结束拖拽
         *
         * @param panelState 松手时的面板状态
         * @param isUp 松手时是否是向上滑动
         * @param isClick 是否是点击操作
         */
        fun onEndDragging(panelState: PanelDragStatus, isUp: Boolean, isClick: Boolean) {
        }

        /**
         * 子view是否滑到底部
         *
         * @return true 滑到底部
         */
        fun isChildBottom(): Boolean {
            return true
        }

        /**
         * 是否可以横滑
         *
         * @param ev 事件
         * @return true 可以横滑
         */
        fun isHorizontalDragEnable(ev: MotionEvent): Boolean {
            return true
        }

        /**
         * 点击非面板区域
         */
        fun onClickNonPanelArea() {
        }

        /**
         * 面板展开状态变化之前的回调
         *
         * @param oldPanelStatus 旧状态
         * @param newPanelStatus 新状态
         */
        fun onPanelStatusChangedBefore(
            oldPanelStatus: PanelDragStatus,
            newPanelStatus: PanelDragStatus
        ) {
        }

        /**
         * 面板展开状态变化的回调
         *
         * @param oldPanelStatus 旧状态
         * @param newPanelStatus 新状态
         */
        fun onPanelStatusChanged(oldPanelStatus: PanelDragStatus, newPanelStatus: PanelDragStatus) {
        }

        /**
         * 是否展示白色面板
         *
         * @return true 白色面板 false 黑色面板
         */
        fun isShowWhitePanel(): Boolean {
            return false
        }

        /**
         * 是否禁止默展态面板上滑
         *
         * @return true 禁止上滑 false 可以上滑   默认false
         */
        fun isForbidFoldPanelDragUp(): Boolean {
            return false
        }

        /**
         * 是否允许横向滑动关闭面板 默认开启
         *
         * @param deltaX 滑动距离 大于0向右滑动，小于0向左滑动
         *
         * @return true 允许关闭面板，false 不允许关闭面板
         */
        fun isAllowHorizontalSlideDismissPanel(deltaX: Float): Boolean {
            return true
        }

        /**
         * 横向拉 关闭面板
         *
         * @param isRight 滑动方向 true 向右; false 向左
         */
        fun pullHorizontalToClosePanel(isRight: Boolean) {}

        /**
         * 向下拉 关闭面板
         */
        fun pullDownToClosePanel() {}
    }
}

/**
 * 拖拽状态
 */
sealed class PanelDragStatus {

    /** 无状态  意味着不可见  */
    object NONE : PanelDragStatus()

    /** 默展态 展开了一部分  */
    object FOLD : PanelDragStatus()

    /** 展开态 展开到最大高度  */
    object UNFOLD : PanelDragStatus()

    /** 全屏态，9分屏 */
    object FULLSCREEN : PanelDragStatus()

    override fun toString(): String {
        if (this == NONE) {
            return "retract"
        }
        if (this == FOLD) {
            return "default"
        }
        if (this == UNFOLD) {
            return "expand"
        }
        if (this == FULLSCREEN) {
            return "fullscreen"
        }
        return ""
    }
}

object ScreenInfo {

    fun getStatusBarHeight(context: Context): Int {
        var result = 0
        val resourceId: Int =
            context.getResources().getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = try {
                context.getResources().getDimensionPixelSize(resourceId)
            } catch (var3: Exception) {
                0
            }
        }
        return result
    }

    fun getRealScreenHeight(context: Context?): Int {
        if (context == null) {
            return 0
        } else {
            var realScreenHeight = -1
            val windowMgr = context.getSystemService("window") as WindowManager
            if (windowMgr != null) {
                val sDisplayMetrics = DisplayMetrics()
                if (Build.VERSION.SDK_INT >= 17) {
                    windowMgr.defaultDisplay.getRealMetrics(sDisplayMetrics)
                    realScreenHeight = sDisplayMetrics.heightPixels
                } else {
                    realScreenHeight = getDisplayHeight(context)
                }
            }
            return realScreenHeight
        }
    }

    fun getDisplayWidth(context: Context?): Int {
        val metrics: DisplayMetrics? = getDisplayMetrics(context)
        return metrics?.widthPixels ?: 0
    }

    fun getDisplayHeight(context: Context?): Int {
        val metrics: DisplayMetrics? = getDisplayMetrics(context)
        return metrics?.heightPixels ?: 0
    }

    private fun getDisplayMetrics(context: Context?): DisplayMetrics? {
        return context?.resources?.displayMetrics
    }
}