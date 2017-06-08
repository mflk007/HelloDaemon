package com.xdandroid.hellodaemon;

import android.util.Log;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;


/**
 * Created by linzb on 17-6-8.
 */

public class RequestHelper {

    private static final String WEBHOOK = "https://oapi.dingtalk.com/robot/send?access_token=3ea97de847b33920bd5d143045c1a91bfba3661a1dced8b48f69212e0796da8f";
    public static  void post(String content) {
        OkHttpClient okHttpClient = new OkHttpClient();

//        RequestBody body = new FormBody.Builder()
//                .add("键", "值")
//                .add("键", "值")
//        .build();

        MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        String json = "{\n" +
                "    \"msgtype\": \"text\", \n" +
                "    \"text\": {\n" +
                "        \"content\": \"%s\"\n" +
                "    }\n" +
                "}";
        json = String.format(json, content);
        RequestBody body = RequestBody.create(JSON, json);

        Request request = new Request.Builder()
                .url(WEBHOOK)
                .post(body)
                .build();

        Call call = okHttpClient.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.d("LINZB", "onFailure");
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                Log.d("LINZB", "onResponse");
            }
        });
    }
}
