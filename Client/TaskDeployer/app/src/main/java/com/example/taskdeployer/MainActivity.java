package com.example.taskdeployer;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.wear.widget.BoxInsetLayout;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

import static android.provider.CalendarContract.EXTRA_EVENT_ID;

public class MainActivity extends WearableActivity {

    private static class Task{
        private final String taskId;
        private final String taskUrgency;
        private final String taskOrigin;
        private final String taskDescription;

        public Task(String origin, String description, String urgency, String id){
            taskId = id;
            taskUrgency = urgency;
            taskOrigin = origin;
            taskDescription = description;
        }

        public String get_urgency(){
            return taskUrgency;
        }

        public String get_id(){
            return taskId;
        }

        public String get_origin(){
            return taskOrigin;
        }

        public String get_description(){
            return taskDescription;
        }
    }
    private boolean task_in_progress = false, app_active = true, sending_message = false;
    private final String ip_address = "10.96.4.74";
    private String id_task_in_progress="";
    private int current_task = 0;
    private TextView task_origin_text, task_description_text, count_text;
    private Button action_button, next_button, prev_button;
    private BoxInsetLayout main_layout;
    private int communication_port = 0;
    private String message_to_send = "Status=NEUTRAL\n";
    private String [] task_info = new String[4];
    private List<Task> task_list=new ArrayList<>();
    public NotificationCompat.Builder notificationBuilder;
    public NotificationManagerCompat notificationManager;
    private MediaPlayer mp;



    final Thread tcp_communication = new Thread(){
        @Override
        public void run(){
            try{
                Log.i("Communication", "Attempting communication");
                DatagramSocket ds = new DatagramSocket();
                InetAddress ip = InetAddress.getByName(ip_address);
                ds.setSoTimeout(4000);
                task_description_text.post(new Runnable() {
                    public void run() {
                        task_description_text.setText(R.string.NOTASK);
                    }
                });
                byte[] buf;
                byte[] buf2 = new byte[128];
                while (app_active) {
                    buf = message_to_send.getBytes();
                    DatagramPacket DpSend = new DatagramPacket(buf, buf.length, ip, communication_port);
                    ds.send(DpSend);
                    DatagramPacket packet = new DatagramPacket(buf2, buf2.length);
                    ds.receive(packet);
                    String received = new String(packet.getData(), 0, packet.getLength());
//                    Log.i("Communication", "message: " + received);
                    if (received.contains("TASK_DISCARD")){
                        message_to_send = "MESSOK\n";
                        Log.i("Communication", received);
                        received = received.replace("TASK_DISCARD:{", "");
                        received = received.replace("}\n", "");
                        Log.i("Communication", received);
                        remove_task(received);

                    } else if (received.contains("TASK")){
                        generate_notification();
                        message_to_send = "MESSOK\n";
                        received = received.replace("TASK:{", "");
                        received = received.replace("}\n", "");
                        task_info = received.split(";");
                        final Task task = new Task(task_info[0], task_info[1], task_info[2], task_info[3]);
                        task_list.add(task);
                        current_task = task_list.size();
                        task_origin_text.post(new Runnable() {
                            public void run() {
                                task_origin_text.setText(task_info[0]);
                                task_description_text.setText(task_info[1]);
                            }
                        });
                        if (!task_in_progress){
                            if (task_list.get(current_task-1).get_urgency().contains("URGENT")){
                                main_layout.setBackgroundColor(Color.parseColor("#80F44336"));
                            } else {
                                main_layout.setBackgroundColor(Color.parseColor("#80FFC107"));
                            }
                            if (task_list.size() == 1){
                                action_button.post(new Runnable() {
                                    public void run() {
                                        action_button.setVisibility(View.VISIBLE);
                                        task_origin_text.setVisibility(View.VISIBLE);
                                    }
                                });
                            } else {
                                final String text_for_count = task_list.size() + "/" + task_list.size();

                                prev_button.post(new Runnable() {
                                    public void run() {
                                        prev_button.setVisibility(View.VISIBLE);
                                        next_button.setVisibility(View.VISIBLE);
                                        count_text.setText(text_for_count);
                                        count_text.setVisibility(View.VISIBLE);
                                    }
                                });
                            }
                        }

                    } else if (received.contains("Status=NEUTRAL") && !sending_message){
                        message_to_send = "Status=NEUTRAL\n";
                    } else if (received.contains("Status=RECEIVED_MESSAGE")){
                        Log.i("Communication", "Server reports it received message");
                        message_to_send = "MESSOK\n";
                        sending_message = false;
                    }
                    sleep(500);
                }
                ds.close();
                Log.i("Communication", "Closed!");
            } catch (SocketTimeoutException e){
                Log.i("Communication", "Server has disconnected");
                mp.release();
                mp = null;
                finish();
            } catch (IOException e){
                e.printStackTrace();
            } catch (InterruptedException e){
                e.printStackTrace();
            }
        }
    };

    final Thread tcp_thread = new Thread(){
        @Override
        public void run() {
            try{
                Log.i("Communication", "Creating datagram socket");
                DatagramSocket ds = new DatagramSocket();
                InetAddress ip = InetAddress.getByName(ip_address);
                byte[] buf;
                String toSend = "Device_ID=SmartWatch2\n";
                buf = toSend.getBytes();
                Log.i("Communication", "Sending communication request to server");
                DatagramPacket DpSend = new DatagramPacket(buf, buf.length, ip,50000);
                ds.send(DpSend);
                Log.i("Communication", "Waiting for port assignment");
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                ds.receive(packet);
                String received = new String(packet.getData(), 0, packet.getLength());
                received = received.replace("\n", "");
                Log.i("Communication", "Assigned port is: " + received);
                communication_port = Integer.parseInt(received);
                tcp_communication.start();


            } catch (Exception e){
                e.printStackTrace();
            }
        }
    };


    @RequiresApi(api = Build.VERSION_CODES.Q)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!isTaskRoot()
                && getIntent().hasCategory(Intent.CATEGORY_LAUNCHER)
                && getIntent().getAction() != null
                && getIntent().getAction().equals(Intent.ACTION_MAIN)) {

            finish();
            return;
        }
        setContentView(R.layout.activity_main);
        ConnectivityManager cm = getApplicationContext().getSystemService(ConnectivityManager.class);
        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build();
        ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback();
        cm.requestNetwork(request, networkCallback);
        task_origin_text = (TextView) findViewById(R.id.task_origin_text);
        task_description_text = (TextView) findViewById(R.id.task_description_text);
        count_text = (TextView) findViewById(R.id.count_text);
        action_button = (Button) findViewById(R.id.action_button);
        next_button = (Button) findViewById(R.id.next_button);
        prev_button = (Button) findViewById(R.id.prev_button);
        main_layout = (BoxInsetLayout) findViewById(R.id.box_layout);
        mp = MediaPlayer.create(this, R.raw.ding);

// The channel ID of the notification.
        String id = "my_channel_01";
// Build intent for notification content
        Intent viewIntent = new Intent(this, MainActivity.class);
        viewIntent.putExtra(EXTRA_EVENT_ID, 1);
        PendingIntent viewPendingIntent =
                PendingIntent.getActivity(this, 0, viewIntent, 0);

// Notification channel ID is ignored for Android 7.1.1
// (API level 25) and lower.
        notificationBuilder =
                new NotificationCompat.Builder(this, id)
                        .setSmallIcon(R.drawable.ic_cc_checkmark)
                        .setContentTitle("WOW")
                        .setContentText("wew")
                        .setChannelId("00033")
                        .setContentIntent(viewPendingIntent);


        notificationManager = NotificationManagerCompat.from(this);
        String channelId = "Your_channel_id";
        NotificationChannel channel = new NotificationChannel(
                channelId,
                "Channel human readable title",
                NotificationManager.IMPORTANCE_HIGH);
        notificationManager.createNotificationChannel(channel);
        notificationBuilder.setChannelId(channelId);


        // Enables Always-on
        setAmbientEnabled();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        tcp_thread.start();
    }



    public void generate_notification(){
        Log.i("Notification", "here");
        if(!mp.isPlaying()){

            mp.start();

        }

// Issue the notification with notification manager.
        //notificationManager.notify(001, notificationBuilder.build());


    }

    private void remove_task(final String task_id){
        current_task = 1;
        for (int i=0; i<task_list.size(); i++){
            if (task_list.get(i).get_id().equals(task_id)){
                task_list.remove(i);
                final String text_for_count = "1/" + task_list.size();

                if (task_list.size()>1){
                    if (!task_in_progress)
                        task_origin_text.post(new Runnable() {
                            public void run() {
                                task_origin_text.setText(task_list.get(0).get_origin());
                                task_origin_text.setVisibility(View.VISIBLE);
                                next_button.setVisibility(View.VISIBLE);
                                prev_button.setVisibility(View.VISIBLE);
                                count_text.setVisibility(View.VISIBLE);
                                task_description_text.setText(task_list.get(0).get_description());
                                count_text.setText(text_for_count);
                                if (task_list.get(current_task-1).get_urgency().contains("URGENT")){
                                    main_layout.setBackgroundColor(Color.parseColor("#80F44336"));
                                } else {
                                    main_layout.setBackgroundColor(Color.parseColor("#80FFC107"));
                                }

                            }
                        });


                } else if (task_list.size() == 1){

                    if (!task_in_progress)
                        prev_button.post(new Runnable() {
                            public void run() {
                                if (task_list.get(0).get_urgency().contains("URGENT")){
                                    main_layout.setBackgroundColor(Color.parseColor("#80F44336"));
                                } else {
                                    main_layout.setBackgroundColor(Color.parseColor("#80FFC107"));
                                }
                                prev_button.setVisibility(View.INVISIBLE);
                                next_button.setVisibility(View.INVISIBLE);
                                count_text.setVisibility(View.INVISIBLE);
                                task_origin_text.setText(task_list.get(0).get_origin());
                                task_description_text.setText(task_list.get(0).get_description());
                            }
                        });
                } else{
                    if (!task_in_progress)
                        task_description_text.post(new Runnable() {
                            public void run() {
                                task_origin_text.setVisibility(View.INVISIBLE);
                                action_button.setVisibility(View.INVISIBLE);
                                next_button.setVisibility(View.INVISIBLE);
                                prev_button.setVisibility(View.INVISIBLE);
                                count_text.setVisibility(View.INVISIBLE);
                                task_description_text.setText(R.string.NOTASK);
                                main_layout.setBackgroundColor(Color.parseColor("#80575757"));
                            }
                        });
                }
            }
        }
    }

    public void onDestroy(){
        app_active = false;
        super.onDestroy();
    }

    public void action_button_event(View view){
        if (task_in_progress){
            task_in_progress = false;
            message_to_send = "COMPLETED:" + id_task_in_progress + "\n";
            sending_message = true;
            action_button.setText(R.string.ACQUIRE);
            remove_task(id_task_in_progress);
            if (task_list.size()>1)
            id_task_in_progress = "";


        } else {
            task_in_progress = true;
            id_task_in_progress = task_list.get(current_task-1).get_id();
            message_to_send = "ACQUIRE:" + id_task_in_progress + "\n";
            sending_message = true;
            next_button.setVisibility(View.INVISIBLE);
            prev_button.setVisibility(View.INVISIBLE);
            count_text.setVisibility(View.INVISIBLE);
            main_layout.setBackgroundColor(Color.parseColor("#803F51B5"));
            action_button.setText(R.string.COMPLETE);
        }


    }

    public void next_button_event(View view){
        if(current_task==task_list.size()){
            current_task = 1;
        } else {
            current_task += 1;
        }
        task_origin_text.setText(task_list.get(current_task-1).get_origin());
        task_description_text.setText(task_list.get(current_task-1).get_description());
        String text_to_display = current_task + "/" + task_list.size();
        count_text.setText(text_to_display);
        if (task_list.get(current_task-1).get_urgency().contains("URGENT")){
            main_layout.setBackgroundColor(Color.parseColor("#80F44336"));
        } else {
            main_layout.setBackgroundColor(Color.parseColor("#80FFC107"));
        }

    }

    public void prev_button_event(View view){
        if(current_task==1){
            current_task = task_list.size();
        } else {
            current_task -= 1;
        }

        task_origin_text.setText(task_list.get(current_task-1).get_origin());
        task_description_text.setText(task_list.get(current_task-1).get_description());
        String text_to_display = current_task + "/" + task_list.size();
        count_text.setText(text_to_display);
        main_layout.setBackgroundColor(Color.parseColor("#80F44336"));
        if (task_list.get(current_task-1).get_urgency().contains("URGENT")){
            main_layout.setBackgroundColor(Color.parseColor("#80F44336"));
        } else {
            main_layout.setBackgroundColor(Color.parseColor("#80FFC107"));
        }
    }
}