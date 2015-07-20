package com.tian.ye.mymenuitemtest;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import java.util.ArrayList;


public class MainActivity extends Activity {

    private static final int RESULT_SETTINGS = 1, REQUEST_ENABLE_BT = 2;
    private ChartView chartView;
    private ArrayList<String> macList = new ArrayList<>();
    private ArrayList<Integer> rssList = new ArrayList<>();
    //    private ArrayList<Integer> indList = new ArrayList<>();
    private ArrayList<State> kfList = new ArrayList<>();
    private int timer = 0;
    private BluetoothAdapter btAdapter;

    private double model_a, model_b;

    private long SCAN_INTERVAL_MS;
    private long STOP_INTERVAL_MS;
    private boolean isScanning = false;
    private Handler scanHandler;
    private boolean continuousScan = true;

    private boolean scanInProgress = false;

    private BluetoothAdapter.LeScanCallback leScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, final int rssi, final byte[] scanRecord) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (macList.contains(device.getAddress())) {
                        rssList.add(-rssi);
                        long currentTimeMillis = System.currentTimeMillis();
                        if (kfList.isEmpty()) {
                            double a = 1, h = 1, q = Math.pow(2, 2), r = Math.pow(5, 2), z = rssi, x = z, p = r;
                            State s = new State(a, h, q, r, p, x, z, currentTimeMillis);
                            kfList.add(kalmanFilter(s));
                        } else {
                            double drv_rss = (10 * model_b / Math.log(10)) * Math.pow(10, ((kfList.get(kfList.size() - 1).x - model_a) / 10 / model_b));
                            double a = kfList.get(kfList.size() - 1).a, h = kfList.get(kfList.size() - 1).h, q = Math.pow(drv_rss, 2) * (currentTimeMillis - kfList.get(kfList.size() - 1).t) / 1000, r = kfList.get(kfList.size() - 1).r, z = rssi, x = kfList.get(kfList.size() - 1).x, p = kfList.get(kfList.size() - 1).p;
                            Log.d("Rss Filter a value:", Double.toString(model_a));
                            Log.d("Rss Filter b value:", Double.toString(model_b));
                            Log.d("Rss Filter Q value:", Double.toString(q));
                            State s = new State(a, h, q, r, p, x, z, System.currentTimeMillis());
                            kfList.add(kalmanFilter(s));
                        }
                        while (kfList.get(kfList.size() - 1).t - kfList.get(0).t > 100000) {
                            kfList.remove(0);
                        }
                        Log.d("Rss Filtered:", Double.toString(-kfList.get(kfList.size() - 1).x));
                        chartView.onChart();
                        timer += 1;
                    }
                }
            });
        }
    };
    private Runnable scanRunnable;

    private State kalmanFilter(State s) {
        s.x = s.a * s.x;
        s.p = s.a * s.p * s.a + s.q;
        double k = s.p * s.h / (s.h * s.p * s.h + s.r);
        s.x = s.x + k * (s.z - s.h * s.x);
        s.p = s.p - k * s.h * s.p;
        return s;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                if (resultCode != RESULT_OK) enableBLE();
                break;
            case RESULT_SETTINGS:
                updateSettings();
                break;
        }
    }

    private void updateSettings() {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        String mac = sharedPrefs.getString("listpref_mac_add", null);
        if (mac != null) {
            macList.clear();
            macList.add(mac);
        }

        continuousScan = sharedPrefs.getBoolean("switch_continuous_scan_on_off", true);

        model_a = Float.parseFloat(sharedPrefs.getString("pref_value_a", "-20"));
        model_b = Float.parseFloat(sharedPrefs.getString("pref_value_b", "5"));
        SCAN_INTERVAL_MS = Long.parseLong(sharedPrefs.getString("pref_value_scan_time", "1000"));
        STOP_INTERVAL_MS = Long.parseLong(sharedPrefs.getString("pref_value_stop_time", "1000"));

    }

    private void checkBLE() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private boolean enableBLE() {
        boolean ret = true;
        if (btAdapter == null || !btAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            ret = false;
        }
        return ret;
    }

    public class ChartView extends View {

        private static final int INVALID_POINTER_ID = -1;
        int win_n = 70;
        private ArrayList<Path> pathList = new ArrayList<>();
        private ArrayList<Paint> pathPaintList = new ArrayList<>();
        private ArrayList<PointF> pointList = new ArrayList<>();
        private Bitmap grid;
        private Paint paintCircle = new Paint();
        private Paint paintText = new Paint();
        private float mPosX;
        private float mPosY;
        private float mLastTouchX;
        private float mLastTouchY;
        private int mActivePointerId = INVALID_POINTER_ID;
        private ScaleGestureDetector mScaleDetector;
        private float mScaleFactor = 1.f;

        public ChartView(Context context) {
            super(context);
            grid = BitmapFactory.decodeResource(context.getResources(), R.drawable.grid);
            paintText.setColor(Color.RED);
            paintText.setTextSize(60);
            mScaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        }

        private void createLines() {
            while (macList.size() > pathList.size()) {
                pathList.add(new Path());
                pathPaintList.add(new Paint());
                pathPaintList.get(pathPaintList.size() - 1).setAntiAlias(true);
                pathPaintList.get(pathPaintList.size() - 1).setColor(Color.rgb((int) (Math.random() * 256), (int) (Math.random() * 256), (int) (Math.random() * 256)));
                pathPaintList.get(pathPaintList.size() - 1).setStrokeWidth((float) 3.0);
                pathPaintList.get(pathPaintList.size() - 1).setStyle(Paint.Style.STROKE);
                pathPaintList.get(pathPaintList.size() - 1).setStrokeWidth(5f);
            }
            pathList.get(0).reset();
            pathList.get(0).moveTo(0, (float) (-10 * kfList.get(0).x));
            for (int i = 1; i < kfList.size(); i++) {
                pathList.get(0).lineTo((kfList.get(i).t - kfList.get(0).t) / 100, (float) (-10 * kfList.get(i).x));
            }

//            if (rssList.size() < win_n + 1) {
//                for (int i = 0; i < pathList.size(); i++) {
//                    pathList.get(i).lineTo(10 * timer, (float) (-10 * kfList.get(timer).x));
//                }
//
//                while (macList.size() > pathList.size()) {
//                    pathList.add(new Path());
//                    pathList.get(pathList.size() - 1).moveTo(10 * timer, (float) (-10 * kfList.get(timer).x));
//
//                    pathPaintList.add(new Paint());
//                    pathPaintList.get(pathPaintList.size() - 1).setAntiAlias(true);
//                    pathPaintList.get(pathPaintList.size() - 1).setColor(Color.rgb((int) (Math.random() * 256), (int) (Math.random() * 256), (int) (Math.random() * 256)));
//                    pathPaintList.get(pathPaintList.size() - 1).setStrokeWidth((float) 3.0);
//                    pathPaintList.get(pathPaintList.size() - 1).setStyle(Paint.Style.STROKE);
//                    pathPaintList.get(pathPaintList.size() - 1).setStrokeWidth(5f);
//
//                }
//            } else {
//                pathList.get(0).reset();
//                pathList.get(0).moveTo(0, (float) (-10 * kfList.get(rssList.size() - win_n).x));
//                for (int i = 0; i < win_n - 1; i++) {
//                    pathList.get(0).lineTo(10 * (i + 1), (float) (-10 * kfList.get(rssList.size() - win_n + 1 + i).x));
//                }
//            }
        }

        private void createPoints() {
            pointList.clear();
            for (int i = 0; i < kfList.size(); i++) {
                pointList.add(new PointF((float) (kfList.get(i).t - kfList.get(0).t) / 100, (float) (-10 * kfList.get(i).z)));
            }
//
//            if (rssList.size() < win_n + 1) {
//                pointList.add(new PointF(10 * timer, 10 * rssList.get(timer)));
//            } else {
//                pointList.clear();
//                for (int i = 0; i < win_n; i++) {
//                    pointList.add(new PointF(10 * i, 10 * rssList.get(rssList.size() - win_n + i)));
//                }
//            }
        }

        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.translate(mPosX, mPosY);
            canvas.scale(mScaleFactor, mScaleFactor, 0, 0);
            canvas.drawBitmap(grid, 0, 0, null);
            if (kfList.size() > 1) {
                canvas.drawText(String.format("%.2f", kfList.size() * 1000.f / (kfList.get(kfList.size() - 1).t - kfList.get(0).t)), 0, 40, paintText);
//                Log.d("kfList_Size:", Integer.toString(kfList.size()));
//                Log.d("kfList_TimeDiff:", Long.toString(kfList.get(kfList.size() - 1).t - kfList.get(0).t));
//                Log.d("kfList_updrate:", Float.toString(kfList.size() * 1000.f / (kfList.get(kfList.size() - 1).t - kfList.get(0).t)));
            }
            for (int i = 0; i < pathList.size(); i++) {
                canvas.drawPath(pathList.get(i), pathPaintList.get(i));
            }
            for (int i = 0; i < pointList.size(); i++) {
                canvas.drawCircle(pointList.get(i).x, pointList.get(i).y, 4.f, paintCircle);
            }
        }

        protected void onChart() {
            createPoints();
            createLines();
            invalidate();
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            // Let the ScaleGestureDetector inspect all events.
            mScaleDetector.onTouchEvent(ev);

            final int action = ev.getAction();
            switch (action & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_DOWN: {
                    final float x = ev.getX();
                    final float y = ev.getY();

                    mLastTouchX = x;
                    mLastTouchY = y;
                    mActivePointerId = ev.getPointerId(0);
                    break;
                }

                case MotionEvent.ACTION_MOVE: {
                    final int pointerIndex = ev.findPointerIndex(mActivePointerId);
                    final float x = ev.getX(pointerIndex);
                    final float y = ev.getY(pointerIndex);

                    // Only move if the ScaleGestureDetector isn't processing a gesture.
                    if (!mScaleDetector.isInProgress()) {
                        final float dx = x - mLastTouchX;
                        final float dy = y - mLastTouchY;

                        mPosX += dx;
                        mPosY += dy;

                        invalidate();
                    }

                    mLastTouchX = x;
                    mLastTouchY = y;

                    break;
                }

                case MotionEvent.ACTION_UP: {
                    mActivePointerId = INVALID_POINTER_ID;
                    break;
                }

                case MotionEvent.ACTION_CANCEL: {
                    mActivePointerId = INVALID_POINTER_ID;
                    break;
                }

                case MotionEvent.ACTION_POINTER_UP: {
                    final int pointerIndex = (ev.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK)
                            >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
                    final int pointerId = ev.getPointerId(pointerIndex);
                    if (pointerId == mActivePointerId) {
                        // This was our active pointer going up. Choose a new
                        // active pointer and adjust accordingly.
                        final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
                        mLastTouchX = ev.getX(newPointerIndex);
                        mLastTouchY = ev.getY(newPointerIndex);
                        mActivePointerId = ev.getPointerId(newPointerIndex);
                    }
                    break;
                }
            }

            return true;
        }

        private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                final float scale = detector.getScaleFactor();
                mScaleFactor *= detector.getScaleFactor();
                // Don't let the object get too small or too large.
                mScaleFactor = Math.max(0.1f, Math.min(mScaleFactor, 10.0f));

//            Log.d("Scale Focus X", Float.toString(detector.getFocusX()));
//            Log.d("Scale Focus Y", Float.toString(detector.getFocusY()));

                // 1 Grabbing center
                float centerX = detector.getFocusX();
                float centerY = detector.getFocusY();
                // 2 Calculating difference
                float diffX = centerX - mPosX;
                float diffY = centerY - mPosY;
                // 3 Scaling difference
                diffX = diffX * scale - diffX;
                diffY = diffY * scale - diffY;
                // 4 Updating image origin
                mPosX -= diffX;
                mPosY -= diffY;
//            Log.d("mScaleFactor", Float.toString(mScaleFactor));
//            Log.d("mPosX", Float.toString(mPosX));
//            Log.d("mPosY", Float.toString(mPosY));

                invalidate();
                return true;
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

//        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        chartView = new ChartView(this);
        chartView.setBackgroundColor(Color.WHITE);
        chartView.setFocusable(true);
        chartView.setClickable(true);

        setContentView(chartView);

        updateSettings();

        scanHandler = new Handler();
        scanRunnable = new Runnable() {
            @Override
            public void run() {
                if (isScanning) {
                    Log.d("State: ", "Stop");
                    Log.d("Stop Time: ", Long.toString(STOP_INTERVAL_MS));
                    btAdapter.stopLeScan(leScanCallback);
                    scanHandler.postDelayed(this, STOP_INTERVAL_MS);
                } else {
                    Log.d("State: ", "Scan");
                    Log.d("Scan Time: ", Long.toString(SCAN_INTERVAL_MS));
                    btAdapter.startLeScan(leScanCallback);
                    scanHandler.postDelayed(this, SCAN_INTERVAL_MS);
                }
                isScanning = !isScanning;
            }
        };

        btAdapter = BluetoothAdapter.getDefaultAdapter();
        checkBLE();
        enableBLE();
        scanInProgress = false;
    }

    @Override
    protected void onResume() {
//        if (continuousScan)
//            btAdapter.startLeScan(leScanCallback);
//        else
//            scanHandler.post(scanRunnable);
//        scanInProgress = true;
        super.onResume();
    }

    @Override
    protected void onPause() {
        if (continuousScan)
            btAdapter.stopLeScan(leScanCallback);
        else
            scanHandler.removeCallbacks(scanRunnable);
        scanInProgress = false;
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        this.menu = menu;
        getMenuInflater().inflate(R.menu.menu_main, menu);
        if (scanInProgress)
            setOptionTitle(R.id.action_scan,"Stop");
        else
            setOptionTitle(R.id.action_scan,"Scan");
        return true;
    }

    private Menu menu;
    private void hideOption(int id)
    {
        MenuItem item = menu.findItem(id);
        item.setVisible(false);
    }

    private void showOption(int id)
    {
        MenuItem item = menu.findItem(id);
        item.setVisible(true);
    }

    private void setOptionTitle(int id, String title)
    {
        MenuItem item = menu.findItem(id);
        item.setTitle(title);
    }

    private void setOptionIcon(int id, int iconRes)
    {
        MenuItem item = menu.findItem(id);
        item.setIcon(iconRes);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Intent i = new Intent(this, SettingActivity.class);
            startActivityForResult(i, RESULT_SETTINGS);
            return true;
        }
        if (id == R.id.action_scan) {
            if (scanInProgress) {
                if (continuousScan)
                    btAdapter.stopLeScan(leScanCallback);
                else
                    scanHandler.removeCallbacks(scanRunnable);
                scanInProgress = false;
                setOptionTitle(id,"Scan");
                setOptionIcon(id, R.drawable.scan);
            } else {
                if (continuousScan)
                    btAdapter.startLeScan(leScanCallback);
                else
                    scanHandler.post(scanRunnable);
                scanInProgress = true;
                setOptionTitle(id,"Stop");
                setOptionIcon(id,R.drawable.scan_on);
            }
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
