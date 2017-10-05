package tech.tdjs.dash;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.robinhood.spark.SparkAdapter;
import com.robinhood.spark.SparkView;
import com.robinhood.ticker.TickerUtils;
import com.robinhood.ticker.TickerView;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    public LocationManager locationManager;
    public LocationListener locationListener;

    public TickerView speedTicker;
    public TickerView avgSpeedTicker;
    public TickerView peakSpeedTicker;
    public TickerView bearingTicker;
    public TextView compassText;
    public TextView scrubText;

    public SpeedAdapter speedAdapter;
    public float[] speedData;
    public int RESOLUTION = 60;
    public float peakSpeed = 0;

    // 0 = mph, 1 = kmh
    public int UNITS_MODE = 0;
    public float CONVERSION_FACTOR = 0.44704f;
    public TextView unitsText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (ContextCompat.checkSelfPermission( this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }

        compassText = (TextView) findViewById(R.id.compassText);
        unitsText = (TextView) findViewById(R.id.unitsText);

        speedTicker = (TickerView) findViewById(R.id.speedTicker);
        speedTicker.setCharacterList(TickerUtils.getDefaultListForUSCurrency());
        speedTicker.setText("NOFIX");

        avgSpeedTicker = (TickerView) findViewById(R.id.avgSpeedTicker);
        avgSpeedTicker.setCharacterList(TickerUtils.getDefaultListForUSCurrency());
        avgSpeedTicker.setText("0");

        peakSpeedTicker = (TickerView) findViewById(R.id.peakSpeedTicker);
        peakSpeedTicker.setCharacterList(TickerUtils.getDefaultListForUSCurrency());
        peakSpeedTicker.setText("0");

        bearingTicker = (TickerView) findViewById(R.id.bearingTicker);
        bearingTicker.setCharacterList(TickerUtils.getDefaultListForUSCurrency());
        bearingTicker.setText("NOFIX");

        speedData = new float[RESOLUTION];
        Arrays.fill(speedData, 0.0f);

        final SparkView sparkView = (SparkView) findViewById(R.id.sparkview);
        speedAdapter = new SpeedAdapter(speedData);
        sparkView.setAdapter(speedAdapter);
        sparkView.setScrubEnabled(true);
        scrubText = (TextView) findViewById(R.id.scrubText);
        if (Build.VERSION.SDK_INT >= 23) {
            sparkView.setLineColor(getColor(R.color.colorPrimary));
            sparkView.setScrubLineColor(getColor(R.color.colorAccent));
        }
        else {
            sparkView.setLineColor(getResources().getColor(R.color.colorPrimary));
            sparkView.setScrubLineColor(getResources().getColor(R.color.colorAccent));
        }
        sparkView.setScrubListener(new SparkView.OnScrubListener() {
            @Override
            public void onScrubbed(Object value) {
                if (value != null) {
                    scrubText.setVisibility(View.VISIBLE);
                    scrubText.setText(value.toString());
                }
                else {
                    scrubText.setVisibility(View.GONE);
                }
            }
        });

        final DecimalFormat df = new DecimalFormat("#");
        df.setRoundingMode(RoundingMode.CEILING);

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                speedTicker.setText(df.format(location.getSpeed()/CONVERSION_FACTOR));
                bearingTicker.setText(df.format(location.getBearing()) + "°");
                compassText.setText(getCompassDir(location.getBearing()));

                shiftLeft(speedData);
                speedData[speedData.length-1] = Float.valueOf(df.format(location.getSpeed()/CONVERSION_FACTOR));
                speedAdapter.update();

                float avgSpeed = sumArray(speedData)/speedData.length;
                avgSpeedTicker.setText(df.format(avgSpeed));

                if (location.getSpeed() > peakSpeed) {
                    peakSpeed = location.getSpeed();
                }
                peakSpeedTicker.setText(df.format(peakSpeed/CONVERSION_FACTOR));
            }

            @Override
            public void onStatusChanged(String s, int i, Bundle bundle) {

            }

            @Override
            public void onProviderEnabled(String s) {

            }

            @Override
            public void onProviderDisabled(String s) {

            }
        };

        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 100, 1, locationListener);

        unitsText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (UNITS_MODE == 0) {
                    UNITS_MODE = 1;
                    CONVERSION_FACTOR = 0.27777f;
                    unitsText.setText("kmh");
                }
                else {
                    UNITS_MODE = 0;
                    CONVERSION_FACTOR = 0.44704f;
                    unitsText.setText("mph");
                }
            }
        });

        unitsText.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                startActivity(new Intent(getApplicationContext(), AboutActivity.class));
                return true;
            }
        });
    }

    public class SpeedAdapter extends SparkAdapter {
        private float[] yData;

        public SpeedAdapter(float[] yData) {
            this.yData = yData;
        }

        public void update() {
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return yData.length;
        }

        @Override
        public Object getItem(int index) {
            return yData[index];
        }

        @Override
        public float getY(int index) {
            return yData[index];
        }

        @Override
        public boolean hasBaseLine() {
            return true;
        }

        @Override
        public float getBaseLine() {
            return 0;
        }
    }

    public float[] shiftLeft(float[] nums) {
        if (nums == null || nums.length <= 1) {
            return nums;
        }
        float start = nums[0];
        System.arraycopy(nums, 1, nums, 0, nums.length - 1);
        nums[nums.length - 1] = start;
        return nums;
    }

    public static String getCompassDir(float x)
    {
        String directions[] = {"N", "NE", "E", "SE", "S", "SW", "W", "NW", "N"};
        return directions[(int) Math.round((( (float) x % 360) / 45))];
    }

    public float sumArray(float[] arr) {
        float temp = 0;

        for (float item : arr) {
            temp += item;
        }
        return temp;
    }
}


