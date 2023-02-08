package com.alibaba.nacossync.timer;


import io.meshware.common.timer.Timer;

/**
 * TimerHolder
 *
 * @author Zhiguo.Chen
 * @since 20230207
 */
public class TimerHolder {

    public static Timer timer = new Timer("task-watch-timer", 1, 512, 4);
}

