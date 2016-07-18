package com.example.admin.myandroid;

import twitter4j.AsyncTwitter;
import twitter4j.AsyncTwitterFactory;
import twitter4j.TwitterAdapter;
import twitter4j.TwitterException;
import twitter4j.TwitterListener;
import twitter4j.TwitterMethod;
import twitter4j.auth.AccessToken;
import twitter4j.auth.RequestToken;
import android.net.Uri;
import android.os.Bundle;
import android.app.Activity;
import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class OAuthActivity extends Activity implements OnClickListener {
    public final static String EXTRA_CONSUMER_KEY = "consumer_key";
    public final static String EXTRA_CONSUMER_SECRET = "consumer_secret";
    public final static String EXTRA_ACCESS_TOKEN = "access_token";
    public final static String EXTRA_ACCESS_TOKEN_SECRET = "access_token_secret";

    private RequestToken mRequestToken;
    final AsyncTwitterFactory factory = new AsyncTwitterFactory();
    final AsyncTwitter twitter = factory.getInstance();

    // 非同期版 Twitter4J のリスナ
    private final TwitterListener listener = new TwitterAdapter() {
        @Override
        public void gotOAuthRequestToken(RequestToken token) {
            mRequestToken = token;
        }

        @Override
        public void gotOAuthAccessToken(AccessToken token) {
            // Access Token 取得成功
            // 呼び出し元に Access Token を返す
            final Intent intent = new Intent();
            intent.putExtra(EXTRA_ACCESS_TOKEN, token.getToken());
            intent.putExtra(EXTRA_ACCESS_TOKEN_SECRET, token.getTokenSecret());
            setResult(Activity.RESULT_OK, intent);
            finish();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.loginactivity);

        // Request Token をリクエスト
        final Intent intent = getIntent();
        final String consumer_key = intent.getStringExtra(EXTRA_CONSUMER_KEY);
        final String consumer_secret = intent.getStringExtra(EXTRA_CONSUMER_SECRET);
        twitter.addListener(listener);
        twitter.setOAuthConsumer(consumer_key, consumer_secret);
        twitter.getOAuthRequestTokenAsync();

        // EventListener をセット
        final View start_login = findViewById(R.id.button_start_login);
        start_login.setOnClickListener(this);
        final View login = findViewById(R.id.button_login);
        login.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        switch(view.getId()) {
            case R.id.button_start_login:
            {
                // 認証画面をブラウザで開く
                final Intent intent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse(mRequestToken.getAuthorizationURL()));
                startActivity(intent);
            }
            break;
            case R.id.button_login:
            {
                // PINコードを取得
                final TextView editPin = (TextView)findViewById(R.id.editPin);
                final String pin = editPin.getText().toString();

                // Access Token をリクエスト
                twitter.getOAuthAccessTokenAsync(mRequestToken, pin);
            }
            break;
        }
    }
}
