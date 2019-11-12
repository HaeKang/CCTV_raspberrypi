package com.example.cctv;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.renderscript.ScriptGroup;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.LinkedList;


public class MainActivity extends AppCompatActivity {

    EditText edtIp;
    EditText edtP;
    TextView textR;
    Button btnIp;
    Button btnSocket;
    WebView webView;

    private Socket socket;
    Handler msghandler;

    SocketClient client; // 서버 접속으 위한 클라이언트 클래스
    ReceiveThread receive; // 서버에서 보내온 데이터 안드로이드에서 보이게 하는 거

    LinkedList<SocketClient> threadList;


    private String ip = "192.9.45.191";
    private String port = "8080";
    public static final String NOTIFICATION_CHANNEL_ID = "10001";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        edtIp = findViewById(R.id.edtIp);
        edtP  = findViewById(R.id.edtPort);
        btnIp = findViewById(R.id.btnCCTV);
        textR = findViewById(R.id.response);
        btnSocket = findViewById(R.id.btnSocket);

        edtIp.setText("192.9.45.191:8091");
        edtP.setText("8080");


        webView = findViewById(R.id.webView);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setSupportZoom(true);

        btnIp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String url = "http://192.9.45.191:8091/javascript_simple.html";
                webView.loadUrl(url);
                Toast.makeText(getApplicationContext(), url + " 로 연결", Toast.LENGTH_SHORT).show();
            }
        });


        // ReceiveThread를통해서 받은 메세지를 Handler로 MainThread에서 처리(외부Thread에서는 UI변경이불가)

        msghandler = new Handler() {
            @Override
            public void handleMessage(Message hdmsg) {
                if (hdmsg.what == 1111) {
                    //식별자.
                    textR.setText(hdmsg.obj.toString() + "\n");//보여줄 객체
                    Toast.makeText(MainActivity.this, "사람이 인식되었습니다!", Toast.LENGTH_LONG).show();
                }
            }
        };





        btnSocket.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                    client = new SocketClient(ip, port);
                    client.start();
            }
        });

    }

    class SocketClient extends Thread {
        boolean threadAlive;
        String ip;
        String port;

        DataOutputStream output = null; //byte 로 보내고 문자열로 읽고

        public SocketClient(String ip, String port) {
            threadAlive = true;
            this.ip = ip;
            this.port = port;
        }
        @Override
        public void run() {

            try {
// 연결후 바로 ReceiveThread 시작
                socket = new Socket(ip, Integer.parseInt(port));
                output = new DataOutputStream(socket.getOutputStream());
                receive = new ReceiveThread(socket);
                receive.start();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    class ReceiveThread extends Thread {
        private Socket sock = null;
        DataInputStream input;

        public ReceiveThread(Socket socket) {
            this.sock = socket;
            try{
                input = new DataInputStream(sock.getInputStream());
            }catch(Exception e){
            }
        }
        // 메세지 수신후 Handler로 전달
        public void run() {
            try {
                while (input != null) {
                    String msg;
                    int count = input.available();
                    byte[] rcv = new byte[count];
                    input.read(rcv);
                    msg = new String(rcv);

                    if (count > 0) {
                        Log.d(ACTIVITY_SERVICE, "test :" +msg);
                        Message hdmsg = msghandler.obtainMessage();
                        hdmsg.what = 1111;
                        hdmsg.obj = msg;
                        msghandler.sendMessage(hdmsg);
                        Log.d(ACTIVITY_SERVICE,hdmsg.obj.toString());
                        NotificationSomethings();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    // Notification
    public void NotificationSomethings(){
        NotificationManager notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK) ;
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,  PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher_foreground)) //BitMap 이미지 요구
                .setContentTitle("CCTV에 사람이 발견되었습니다")
                .setContentText("CCTV를 확인해보세요")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent) // 사용자가 노티피케이션을 탭시 ResultActivity로 이동하도록 설정
                .setAutoCancel(true);

//OREO API 26 이상에서는 채널 필요
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            builder.setSmallIcon(R.drawable.ic_launcher_foreground); //mipmap 사용시 Oreo 이상에서 시스템 UI 에러남
            CharSequence channelName  = "노티페케이션 채널";
            String description = "오레오 이상을 위한 것임";
            int importance = NotificationManager.IMPORTANCE_HIGH;

            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName , importance);
            channel.setDescription(description);

            // 노티피케이션 채널을 시스템에 등록
            assert notificationManager != null;
            notificationManager.createNotificationChannel(channel);

        }else builder.setSmallIcon(R.mipmap.ic_launcher); // Oreo 이하에서 mipmap 사용하지 않으면 Couldn't create icon: StatusBarIcon 에러남

        assert notificationManager != null;
        notificationManager.notify(1234, builder.build()); // 고유숫자로 노티피케이션 동작시킴

    }

}


