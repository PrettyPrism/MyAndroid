package com.example.admin.myandroid;

import android.app.Activity;
import android.app.IntentService;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Vibrator;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import twitter4j.ResponseList;
import twitter4j.StatusUpdate;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.Status;
import twitter4j.TwitterStream;
import twitter4j.TwitterStreamFactory;
import twitter4j.User;
import twitter4j.UserStreamAdapter;
import twitter4j.auth.AccessToken;
import twitter4j.conf.ConfigurationBuilder;

public class TwitterService extends IntentService {

    final static String EXTRA_CONSUMER_KEY = "consumer_key";
    final static String EXTRA_CONSUMER_SECRET = "consumer_secret";
    final static String EXTRA_ACCESS_TOKEN = "access_token";
    final static String EXTRA_ACCESS_TOKEN_SECRET = "access_token_secret";
    final static String EXTRA_isTweet = "extra_isTweet";
    final static String EXTRA_tweet = "extra_tweet";

    static ResponseList<Status> myTweets = null;

    static Twitter twitterService;
    static User myUser;
    static SharedPreferences pref;
    static SharedPreferences.Editor editor;
    static long ownerId;
    static ArrayList<Long> friendId = new ArrayList<Long>();
    Calendar now;

    public TwitterService(String name) {
        super(name);
    }

    public TwitterService() {
        super("TwitterService");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if(twitterService==null) {
            //  初回のみ実行
            //  Twitterのメインのインスタンスを作成
            Log.i("message", "create twitter service");
            String key = intent.getStringExtra(EXTRA_CONSUMER_KEY);
            String key_secret = intent.getStringExtra(EXTRA_CONSUMER_SECRET);
            String token = intent.getStringExtra(EXTRA_ACCESS_TOKEN);
            String token_secret = intent.getStringExtra(EXTRA_ACCESS_TOKEN_SECRET);
            ConfigurationBuilder cb = new ConfigurationBuilder();
            cb.setOAuthConsumerKey(key);
            cb.setOAuthConsumerSecret(key_secret);
            cb.setOAuthAccessToken(token);
            cb.setOAuthAccessTokenSecret(token_secret);
            twitter4j.conf.Configuration c = cb.build();
            twitterService = new TwitterFactory(c).getInstance();

            //  TLの受け取り（TwitterStream）の作成
            TwitterStream twitterStream = new TwitterStreamFactory(c).getInstance();
            twitterStream.setOAuthAccessToken(new AccessToken(token, token_secret));
            twitterStream.addListener(new MyUserStreamAdapter());
            twitterStream.user();

            //  自分のTwitterアカウントの情報の取得
            try {
                myUser = twitterService.verifyCredentials();
                myTweets = twitterService.getUserTimeline(twitterStream.getId());
                Log.i("myTweets.length", ""+myTweets.size());
            } catch (TwitterException e) {
                Log.i("read error", e.getMessage());
            }
            pref = getSharedPreferences("t4jdata", Activity.MODE_PRIVATE);
            ownerId = pref.getLong("owner", -1);
            for(int i=0; i<friendId.size(); i++) {
                friendId.add(pref.getLong("friends"+i, 0));
            }
            Log.i("message", "logined");

            //  アイコンの取得
            try {
                Uri uri = Uri.parse(twitterService.verifyCredentials().getOriginalProfileImageURL());
                Uri.Builder builder = uri.buildUpon();
                AsyncTaskHttpRequest task = new AsyncTaskHttpRequest(MainActivity.appFace);
                task.execute(builder);
            } catch (Exception e) {
                Log.e("error", e.toString());
            }
        }

        //  ツイートの場合ツイートする
        boolean isTweet = intent.getBooleanExtra(EXTRA_isTweet, false);
        String tweet = intent.getStringExtra(EXTRA_tweet);
        if(isTweet) {
            if(tweet != null) {
                Log.i("twitterService", tweet);
            } else {
                Log.i("twitterService", "tweet is null");
            }
            boolean tweeted = false;
            for(Status t : myTweets) {
                if(t.getText().equals(tweet)) {
                    tweeted = true;
                }
            }
            if(!tweeted) {
                try {
                myTweets.add(twitterService.updateStatus(tweet));
                } catch(Exception e) {
                    Log.i("error", ""+e.getMessage());
                }
            } else {
                Log.i("message", tweet+" is already tweeted");
            }
        }
    }

    //  TLの受け取りと受け答え
    private String[] hellotweets = {"おはよーおはよー", "おはよー", "おはモニ", "にゃんぱすー", "おはやっぷー", "おはようのかしこま！"};
    private String[] goodnighttweets = {"おやすみー", "おやすみなさーい", "おやすミルキィ", "おやすみのかしこま～", "ｸﾞﾝﾅｲ･･･"};
    class MyUserStreamAdapter extends UserStreamAdapter {

        //  TLに新着ツイートが来た時に実行されるメソッド
        public synchronized void onStatus(Status status) {
            String username = status.getUser().getScreenName();
            String text = status.getText();
            long id = status.getUser().getId();
            String[] s;
            s = text.split(" ");

            if(id == ownerId) {
                if(text.contains("おはよ")) reply(status, hellotweets[(int)(Math.random()*hellotweets.length)]);
                else if(text.contains("おやすみ")) reply(status, goodnighttweets[(int)(Math.random()*goodnighttweets.length)]);
            }

            if(s.length>1) {
                if(s[0].equals("@"+myUser.getScreenName())) {
                    if(s[1].equals("owner")) {
                        editor = pref.edit();
                        editor.putLong("owner", id);
                        editor.commit();
                        ownerId = id;

                        tweet("@"+username+" "+"Hello my owner"+(dateToString(Calendar.getInstance().getTime())));
                    }
                    else if(s[1].equals("friend")) {
                        editor = pref.edit();
                        editor.putLong("friends"+friendId.size(), id);
                        editor.commit();
                        friendId.add(id);

                        tweet("@"+username+" "+"Hello my owner"+(dateToString(Calendar.getInstance().getTime())));
                    }
                }
            }
            //. 取り出した情報を表示する（以下では id, username, text のみ）
            Log.i("username, text", "username = " + username + ", text = "+text);
        }
    }

    //  ツイートメソッド
    void tweet(String tweet) {
        try {
            boolean tweeted = false;
            for(Status t : myTweets) {
                if(t.getText().equals(tweet)) {
                    tweeted = true;
                }
            }
            if(!tweeted) {
                myTweets.add(twitterService.updateStatus(tweet));
            } else {
                Log.i("message", tweet+" is already tweeted");
            }
        } catch(Exception e) {
            Log.i("error", e.getMessage());
        }
    }

    //  特定のStatus（ツイート）に対するリプライ
    void reply(Status status, String tweet) {
        try {
            boolean tweeted = false;
            for(Status t : myTweets) {
                if(t.getText().equals(tweet)) {
                    tweeted = true;
                }
            }
            if(!tweeted) {
                myTweets.add(twitterService.updateStatus(new StatusUpdate("@"+status.getUser().getScreenName()+" "+tweet).inReplyToStatusId(status.getId())));
            } else {
                Log.i("message", tweet+" is already tweeted");
            }
        } catch(Exception e) {
            Log.i("error", e.getMessage());
        }
    }
    String dateToString(Date date) {
        return new SimpleDateFormat(" MM/dd HH:mm:ss").format(date);
    }
}
