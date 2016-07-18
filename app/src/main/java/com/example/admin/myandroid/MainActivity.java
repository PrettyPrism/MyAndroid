package com.example.admin.myandroid;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.media.Image;
import android.net.Uri;
import android.os.Vibrator;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.w3c.dom.Text;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

import twitter4j.AsyncTwitter;
import twitter4j.AsyncTwitterFactory;
import twitter4j.Status;
import twitter4j.Twitter;
//import twitter4j.TwitterAdapter;
import twitter4j.TwitterAdapter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.TwitterListener;
import twitter4j.auth.AccessToken;
import twitter4j.conf.ConfigurationBuilder;

public class MainActivity extends AppCompatActivity implements SensorEventListener, LocationListener {
    //各種グローバル変数の定義
    AsyncTwitterFactory factory = new AsyncTwitterFactory();
    AsyncTwitter twitter = factory.getInstance();
    static Twitter myTwitter;
    AccessToken accessToken;
    final int REQUEST_ACCESS_TOKEN = 0;
    final String consumer_key = "mN3nLNC0DKY1hvrut1rJZVdqG";
    final String consumer_secret = "zeoqUjyvaNTZDiDTbE2ERyw2j7JDTJGqUE6pZHCJLBlbAcIYbJ";
    String token = "", token_secret = "";
    SharedPreferences pref;
    SharedPreferences.Editor editor;

    //主にセンサー関係のグローバル変数
    private SensorManager asm;
    private LocationManager lm;
    private Button attentionMode;
    boolean endless = false, setDistance = false, isDisTweet = false, isAttention = false;
    float[] data = new float[3];
    int vibra = 0, location_min_time = 0, location_min_distance = 1;
    double startLati, endLati, startLong, endLong, distance = 1.0, total = 0.0;
    long start, end, endlessS=0, endlessG=0;
    TextView time, locate, prov, appMessage;
    static ImageView appFace;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //アクティビティ生成時に呼び出される
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //加速度センサマネージャとロケーションマネージャの設定
        asm = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
        lm = (LocationManager)getSystemService(Service.LOCATION_SERVICE);
        //時間計測の開始
        start = System.currentTimeMillis();

        data[0] = 0;
        data[1] = 0;
        data[2] = 0;
        //各種テキストビューの紐づけ
        time = (TextView)findViewById(R.id.acceleText);
        locate = (TextView)findViewById(R.id.locationText);
        prov = (TextView)findViewById(R.id.providerText);
        appFace = (ImageView)findViewById(R.id.face);
        appMessage = (TextView)findViewById(R.id.msg);
        attentionMode = (Button)findViewById(R.id.attension);

        pref = getSharedPreferences("t4jdata", Activity.MODE_PRIVATE);
        token=pref.getString("token", "");
        token_secret=pref.getString("token_secret", "");

        twitter.setOAuthConsumer(consumer_key, consumer_secret);
        twitter.getOAuthRequestTokenAsync();

        if(token.length()==0){
            Intent intent = new Intent(getApplicationContext(), OAuthActivity.class);
            intent.putExtra(OAuthActivity.EXTRA_CONSUMER_KEY, consumer_key);
            intent.putExtra(OAuthActivity.EXTRA_CONSUMER_SECRET, consumer_secret);
            startActivityForResult(intent, REQUEST_ACCESS_TOKEN);
        } else {
            accessToken = new AccessToken(token, token_secret);
            Intent intent = new Intent(MainActivity.this, TwitterService.class);
            intent.putExtra(TwitterService.EXTRA_CONSUMER_KEY, consumer_key);
            intent.putExtra(TwitterService.EXTRA_CONSUMER_SECRET, consumer_secret);
            intent.putExtra(TwitterService.EXTRA_ACCESS_TOKEN, token);
            intent.putExtra(TwitterService.EXTRA_ACCESS_TOKEN_SECRET, token_secret);
            startService(intent);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == REQUEST_ACCESS_TOKEN && resultCode == Activity.RESULT_OK) {
            token = data.getStringExtra(OAuthActivity.EXTRA_ACCESS_TOKEN);
            token_secret = data.getStringExtra(OAuthActivity.EXTRA_ACCESS_TOKEN_SECRET);
            accessToken = new AccessToken(token, token_secret);

            //  accesstokenを記録して2回目以降自動にログインする
            editor = pref.edit();
            editor.putString("token", token);
            editor.putString("token_secret", token_secret);
            editor.commit();

            Intent intent = new Intent(MainActivity.this, TwitterService.class);
            intent.putExtra(TwitterService.EXTRA_CONSUMER_KEY, consumer_key);
            intent.putExtra(TwitterService.EXTRA_CONSUMER_SECRET, consumer_secret);
            intent.putExtra(TwitterService.EXTRA_ACCESS_TOKEN, token);
            intent.putExtra(TwitterService.EXTRA_ACCESS_TOKEN_SECRET, token_secret);
            startService(intent);
        }
    }
    @Override
    protected void onResume() {
        //センサマネージャ等の設定
        super.onResume();
        List<Sensor> sensors = asm.getSensorList(Sensor.TYPE_ACCELEROMETER);
        if (sensors.size() > 0) {
            Sensor s = sensors.get(0);
            asm.registerListener(this, s, SensorManager.SENSOR_DELAY_NORMAL);
        }
        boolean isNetworkEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        if (isNetworkEnabled) {
            //警告文が出るが、これは「ユーザに拒否されるかもよ？」という意味なので実行には問題ない
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, location_min_time, location_min_distance, this);
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        String m = "";
        //位置情報の表示
        m += "緯度 : "+location.getLatitude()+"\n";//緯度の取得
        m += "経度 : "+location.getLongitude()+"\n";//経度の取得
        locate.setText(m);
        if (!setDistance) {
            //移動前の位置の記録
            startLati = location.getLatitude();
            startLong = location.getLongitude();
            setDistance = true;
        }
        //現在地の記録
        endLati = location.getLatitude();
        endLong = location.getLongitude();
        //√( (緯度の差 * 111)**２ + (経度の差 * 91)**２ )
        if (setDistance) {
            //現在地と初期値の距離を計算する
            double latiDis = ((endLati-startLati)*111.0)*((endLati-startLati)*111.0);
            double longDis = ((endLong-startLong)*91.0)*((endLong-startLong)*91.0);
            if (Math.sqrt(latiDis+longDis) >= 0.01 && !isDisTweet) {
                //初期値からの移動距離が一定以上だったら「移動した」とみなす
                total += Math.sqrt(latiDis+longDis);
                //初期位置のリセット
                startLati = endLati;
                startLong = endLong;
                appMessage.setText("今だいたい" + total + "km移動したよー");
                if (total >= distance) {
                    //トータルで一定以上移動してたらツイート
                    //isDisTweet = true;
                    Intent intent = new Intent(MainActivity.this, TwitterService.class);
                    intent.putExtra(TwitterService.EXTRA_isTweet, true);
                    intent.putExtra(TwitterService.EXTRA_tweet, distance + "km移動したー");
                    startService(intent);
                    distance += 1.0;
                }
            }
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle bundle) {
        switch (status) {
            case LocationProvider.OUT_OF_SERVICE:
                prov.setText(provider+"が圏外になっていて利用できません");
                break;
            case LocationProvider.TEMPORARILY_UNAVAILABLE:
                prov.setText("一時的に"+provider+"が利用できません");
                break;
            case LocationProvider.AVAILABLE:
                prov.setText(provider+"が利用できます");
                break;
        }
    }

    @Override
    public void onProviderEnabled(String s) {

    }

    @Override
    public void onProviderDisabled(String s) {

    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if (isAttention) {
            String m = "";
            float x = 0, y = 0, z = 0;
            switch (sensorEvent.sensor.getType()) {
                case Sensor.TYPE_ACCELEROMETER:
                    x = sensorEvent.values[0];
                    y = sensorEvent.values[1];
                    z = sensorEvent.values[2];
                    if (Math.abs(data[0] - x) > 1 || Math.abs(data[1] - y) > 0.2 || Math.abs(data[2] - z) > 1) {
                        //加速度が一定以上の変化があった場合
                        data[0] = x;
                        data[1] = y;
                        data[2] = z;
                        start = System.currentTimeMillis();
                        vibra = 0;
//                        appFace.setText("(-ω-)");
                        appMessage.setText("・・・・・・。");
                    }
                    end = System.currentTimeMillis();
            }
            //情報表示用の処理
            m += x + "\n";
            m += y + "\n";
            m += z + "\n";
            m += ((end - start) / 1000) + "秒";
            time.setText(m);
            if ((end - start) / 1000 >= 10 && vibra == 0) {
                //もし一定以上加速度が変わらなかった場合、バイブレーションを起動する
                ((Vibrator) getSystemService(Context.VIBRATOR_SERVICE)).vibrate(new long[]{500, 200, 500, 200}, -1);
                vibra = 1;
//                appFace.setText("(-Д-)");
                appMessage.setText("？？？？？？");
                Intent intent = new Intent(MainActivity.this, TwitterService.class);
                intent.putExtra(TwitterService.EXTRA_isTweet, true);
                intent.putExtra(TwitterService.EXTRA_tweet, "10秒間も音沙汰無し");
                startService(intent);
            }
            if ((end - start) / 1000 >= 20 && vibra == 1) {
                //さらに一定以上加速度が変わらなかった場合、バイブレーションを起動する
                ((Vibrator) getSystemService(Context.VIBRATOR_SERVICE)).vibrate(new long[]{500, 600, 500, 600}, -1);
                vibra = 2;
//                appFace.setText("(-Д-#)");
                appMessage.setText("もしもーし？");
                Intent intent = new Intent(MainActivity.this, TwitterService.class);
                intent.putExtra(TwitterService.EXTRA_isTweet, true);
                intent.putExtra(TwitterService.EXTRA_tweet, "20秒も放置されてる・・・つらい・・・");
                startService(intent);
            }
            if ((end - start) / 1000 >= 30) {
                //さらにさらに一定以上加速度が変わらなかった場合、バイブレーションを永続起動する
                if (!endless) {
                    endlessS = System.currentTimeMillis();
                    ((Vibrator) getSystemService(Context.VIBRATOR_SERVICE)).vibrate(1000);
//                    appFace.setText("Щ(°Д°#Щ)");
                    appMessage.setText("か゛ま゛え゛よ゛お゛お゛お゛お゛お゛お゛お゛お゛!!!!!");
                    Intent intent = new Intent(MainActivity.this, TwitterService.class);
                    intent.putExtra(TwitterService.EXTRA_isTweet, true);
                    intent.putExtra(TwitterService.EXTRA_tweet, "構ってほしいなあ・・・");
                    startService(intent);
                }
                endlessG = System.currentTimeMillis();
                if ((endlessG - endlessS) < 700) endless = true;
                else endless = false;
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }
    @Override
    protected void onDestroy() {
        //センサマネージャの解放
        super.onDestroy();
        asm.unregisterListener(this);
    }

    public void attentionSeeker(View view) {
        if (isAttention) {
            isAttention = false;
            attentionMode.setText("OFF");
        }
        else {
            isAttention = true;
            attentionMode.setText("ON");
        }
    }
}
