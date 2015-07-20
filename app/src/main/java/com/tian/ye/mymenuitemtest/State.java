package com.tian.ye.mymenuitemtest;

/**
 * Created by Ours on 2015/7/4.
 */
public class State {

    public double a,h,q,r,p,x,z;
    public long t;

    State() {
        this.a = 0;
        this.h = 0;
        this.q = 0;
        this.r = 0;
        this.p = 0;
        this.x = 0;
        this.z = 0;
        this.t = System.currentTimeMillis();
    }
    State(double a, double h, double q, double r, double p, double x, double z, long t) {
        this.a = a;
        this.h = h;
        this.q = q;
        this.r = r;
        this.p = p;
        this.x = x;
        this.z = z;
        this.t = t;
    }

}
