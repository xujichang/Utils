package me.xujichang.util.simple;

import android.view.View;

/**
 * @author xjc
 *         Created by xjc on 2017/6/14.
 */

public abstract class SimpleViewHolder<T> extends ViewHolderWithAvoidShake {

    public SimpleViewHolder(View itemView) {
        super(itemView);
    }

    /**
     * 绑定数据
     *
     * @param data 数据对象
     */
    protected abstract void bindData(T data);

}
